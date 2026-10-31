package com.repknot.data

import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject

private val codec = Json { ignoreUnknownKeys = true; encodeDefaults = true }

@Serializable
data class ServerSet(val id: String, @SerialName("exercise_name") val exerciseName: String,
    val load: Double, val unit: String, val reps: Int, val rir: Int? = null, val notes: String? = null, val position: Int = 0)

@Serializable
data class ServerSession(val id: String, val number: Long, @SerialName("performed_on") val performedOn: String,
    val title: String? = null, val notes: String? = null, val revision: Long,
    @SerialName("client_revision") val clientRevision: Long? = null,
    @SerialName("session_sets") val sets: List<ServerSet> = emptyList()) {
    fun json(): String = codec.encodeToString(this)
}

@Serializable
private data class PushPayload(val id: String, @SerialName("performed_on") val performedOn: String,
    val title: String, val notes: String?, val sets: List<ServerSet>)
@Serializable
private data class PushParams(val payload: PushPayload, @SerialName("local_revision") val localRevision: Long,
    @SerialName("base_revision") val baseRevision: Long?)
@Serializable
private data class PushResult(val status: String, val session: RpcSession? = null)
@Serializable
private data class RpcSession(val id: String, val number: Long, @SerialName("performed_on") val performedOn: String,
    val title: String? = null, val notes: String? = null, val revision: Long,
    @SerialName("client_revision") val clientRevision: Long? = null, val sets: List<ServerSet> = emptyList()) {
    fun server() = ServerSession(id, number, performedOn, title, notes, revision, clientRevision, sets)
}

class SessionSync(private val cloud: CloudAccount, private val dao: SessionDao, private val checkOwner: (String) -> Boolean = { true }) {
    private val mutex = Mutex()

    suspend fun refreshAndPush() = mutex.withLock {
        val account = cloud.state.first() as? CloudState.SignedIn ?: error("Sign in to sync")
        check(account.userId.isNotBlank() && checkOwner(account.userId)) { "This device is bound to a different account" }
        val client = cloud.requireClient()
        val remote = client.from("workout_sessions").select(Columns.raw("*,session_sets(*)")).decodeList<ServerSession>()
        dao.replaceCache(remote)
        for (row in dao.pending()) push(row)
    }

    private suspend fun push(row: SessionRow) {
        val (current, localSets) = dao.snapshot(row.id) ?: return
        if (current.status != "finished" || current.localRevision <= current.pushedLocalRevision) return
        val sets = localSets.map { ServerSet(it.id, it.exerciseName, it.load, it.unit.lowercase(), it.reps, it.rir, it.notes, it.position) }
        val response = cloud.requireClient().postgrest.rpc("push_session", codec.encodeToJsonElement(PushParams(
            PushPayload(current.id, current.performedOn, current.title, current.notes, sets), current.localRevision, current.baseRevision)).jsonObject).decodeAs<PushResult>()
        val remote = response.session?.server()
        when (response.status) {
            "applied", "replay" -> {
                requireNotNull(remote)
                dao.acknowledge(current.id, current.localRevision, remote.revision, remote.number)
            }
            "conflict" -> {
                requireNotNull(remote)
                dao.handleConflict(remote)
            }
            else -> error("Unexpected push status: ${response.status}")
        }
    }

    suspend fun keepMine(id: String) {
        val row = dao.get(id) ?: return
        val remote = codec.decodeFromString<ServerSession>(requireNotNull(row.serverSnapshot))
        dao.keepMine(id, remote.revision, remote.clientRevision ?: 0)
        refreshAndPush()
    }
    suspend fun takeTheirs(id: String) {
        val row = dao.get(id) ?: return
        val remote = codec.decodeFromString<ServerSession>(requireNotNull(row.serverSnapshot))
        dao.adopt(remote, row.localRevision)
    }
}
