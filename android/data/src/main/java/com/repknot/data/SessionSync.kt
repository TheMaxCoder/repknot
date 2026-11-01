package com.repknot.data

import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.coroutines.CancellationException
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
@Serializable
private data class PushResult(val status: String, val session: ServerSession? = null)

class SessionSync(
    private val cloud: CloudAccount, private val dao: SessionDao, private val reference: ReferenceDao,
    private val checkIns: CheckInDao? = null,
    /** When false, refresh still downloads programs and sessions but nothing is uploaded. */
    private val uploadEnabled: () -> Boolean = { true },
    private val checkOwner: (String) -> Boolean = { true },
) {
    private val mutex = Mutex()

    suspend fun refreshAndPush() = mutex.withLock {
        val account = cloud.state.first() as? CloudState.SignedIn ?: error("Sign in to sync")
        check(account.userId.isNotBlank() && checkOwner(account.userId)) { "This device is bound to a different account" }
        val client = cloud.requireClient()
        // Programs and exercises are read-only on the server: replace the cache wholesale.
        reference.replaceAll(fetchReference(cloud))
        val remote = client.from("workout_sessions").select(Columns.raw(SESSION_COLUMNS)).decodeList<ServerSession>()
        dao.replaceCache(remote)
        checkIns?.replaceCache(client.from("checkins").select(Columns.raw(CHECKIN_COLUMNS)).decodeList<ServerCheckIn>())
        if (!uploadEnabled()) return@withLock
        var firstFailure: Exception? = null
        // Check-ins carry the phone's id, so a retried upload is a no-op on the server.
        for (row in checkIns?.pending().orEmpty()) {
            try {
                client.from("checkins").upsert(row.toServer()) { onConflict = "id"; ignoreDuplicates = true }
                checkIns?.uploaded(row.id)
            }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { if (firstFailure == null) firstFailure = e }
        }
        for (row in dao.pending()) {
            try { push(row) }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { if (firstFailure == null) firstFailure = e }
        }
        firstFailure?.let { throw it }
    }

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
