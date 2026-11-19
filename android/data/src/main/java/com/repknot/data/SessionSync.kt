package com.repknot.data

import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject

@Serializable
private data class PushParams(val payload: PushPayload, @SerialName("local_revision") val localRevision: Long,
    @SerialName("base_revision") val baseRevision: Long?)
/** What a running [SessionSync.refreshAndPush] is doing, in order; shown while Upload runs. */
enum class SyncStep { ACCOUNT, REFERENCE, HISTORY, CHECK_INS, WORKOUTS }

@Serializable
private data class PushResult(val status: String, val session: ServerSession? = null)

class SessionSync(
    private val cloud: CloudAccount, private val dao: SessionDao, private val reference: ReferenceDao,
    private val checkIns: CheckInDao? = null,
    private val coachNotes: CoachNoteDao? = null,
    private val checkOwner: (String) -> Boolean = { true },
) {
    private val mutex = Mutex()
    private val checkInUploads = checkIns?.let(::CheckInUploads)
    private val mutableStep = MutableStateFlow<SyncStep?>(null)
    /** The running sync's current step; null when no sync runs. */
    val step: StateFlow<SyncStep?> = mutableStep.asStateFlow()

    suspend fun refreshAndPush() = mutex.withLock {
        try { refreshAndPushLocked() } finally { mutableStep.value = null }
    }

    private suspend fun refreshAndPushLocked() {
        mutableStep.value = SyncStep.ACCOUNT
        val account = cloud.state.first() as? CloudState.SignedIn ?: error("Sign in to sync")
        check(account.userId.isNotBlank() && checkOwner(account.userId)) { "This device is bound to a different account" }
        val client = cloud.requireClient()
        // Programs and exercises are read-only on the server: replace the cache wholesale.
        mutableStep.value = SyncStep.REFERENCE
        reference.replaceAll(fetchReference(cloud))
        mutableStep.value = SyncStep.HISTORY
        val remote = client.from("workout_sessions").select(Columns.raw(SESSION_COLUMNS)).decodeList<ServerSession>()
        dao.replaceCache(remote)
        mutableStep.value = SyncStep.CHECK_INS
        checkIns?.replaceCache(client.from("checkins").select(Columns.raw(CHECKIN_COLUMNS)).decodeList<ServerCheckIn>())
        var firstFailure: Exception? = null
        try {
            if (coachNotes != null) {
                val notes = client.from("coach_notes").select(Columns.raw(COACH_NOTE_COLUMNS)) {
                    order("published_at", Order.DESCENDING); limit(1)
                }.decodeList<ServerCoachNote>()
                coachNotes.replace(notes.firstOrNull()?.toRow())
            }
        }
        catch (e: CancellationException) { throw e }
        catch (e: Exception) { if (firstFailure == null) firstFailure = e }
        // Every phone check-in is pushed; sessions only once Upload was tapped (task 028). Check-ins carry the phone's id, so a retried upload is a no-op on the server.
        checkInUploads?.push { row ->
            client.from("checkins").upsert(row.toServer()) { onConflict = "id"; ignoreDuplicates = true }
        }?.let { if (firstFailure == null) firstFailure = it }
        mutableStep.value = SyncStep.WORKOUTS
        for (row in dao.pending()) {
            try { push(row) }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { if (firstFailure == null) firstFailure = e }
        }
        firstFailure?.let { throw it }
    }

    /** Deletes a check-in not uploaded yet, never racing its upload; returns whether it did. */
    suspend fun deleteCheckIn(id: String): Boolean = checkInUploads?.delete(id) { rowId ->
        if (cloud.state.first() is CloudState.SignedIn)
            cloud.requireClient().from("checkins").delete { filter { eq("id", rowId) } }
    } ?: false

    private suspend fun push(row: SessionRow) {
        val snapshot = dao.snapshot(row.id) ?: return
        val current = snapshot.row
        if (current.status != "finished" || current.localRevision <= current.pushedLocalRevision) return
        val response = cloud.requireClient().postgrest.rpc("push_session", codec.encodeToJsonElement(PushParams(
            snapshot.pushPayload(), current.localRevision, current.baseRevision)).jsonObject).decodeAs<PushResult>()
        val remote = response.session
        when (response.status) {
            "applied", "replay" -> {
                requireNotNull(remote)
                dao.acknowledge(current.id, current.localRevision, remote.revision, remote.number)
            }
            "conflict" -> {
                if (remote == null) dao.deletedConflict(current.id)
                else dao.handleConflict(remote)
            }
            else -> error("Unexpected push status: ${response.status}")
        }
    }

    suspend fun keepMine(id: String) {
        val row = dao.get(id) ?: return
        if (row.status == "conflict_deleted") dao.recreateDeleted(id)
        else {
            val remote = codec.decodeFromString<ServerSession>(requireNotNull(row.serverSnapshot))
            dao.keepMine(id, remote.revision, remote.clientRevision ?: 0)
        }
        refreshAndPush()
    }
    suspend fun takeTheirs(id: String) {
        val row = dao.get(id) ?: return
        if (row.status == "conflict_deleted") dao.takeDeleted(id)
        else {
            val remote = codec.decodeFromString<ServerSession>(requireNotNull(row.serverSnapshot))
            dao.adopt(remote, row.localRevision)
        }
    }
}
