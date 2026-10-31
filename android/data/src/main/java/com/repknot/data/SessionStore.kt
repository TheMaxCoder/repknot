package com.repknot.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

@Entity(tableName = "sessions")
data class SessionRow(
    @PrimaryKey val id: String,
    val performedOn: String,
    val title: String,
    val notes: String?,
    val status: String,
    val localRevision: Long,
    val pushedLocalRevision: Long,
    val baseRevision: Long?,
    val sharedLocalRevision: Long?,
    val number: Long?,
    val serverSnapshot: String?,
)

@Dao
interface SessionDao {
    @Query("SELECT * FROM sessions WHERE id = :id")
    fun observe(id: String): Flow<SessionRow?>
    @Query("SELECT * FROM sessions WHERE id = :id")
    suspend fun get(id: String): SessionRow?
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(row: SessionRow)
    @Query("SELECT * FROM sessions WHERE status = 'finished' AND localRevision > pushedLocalRevision")
    suspend fun pending(): List<SessionRow>
    @Query("SELECT * FROM logged_sets WHERE sessionId = :id ORDER BY position, id")
    suspend fun sets(id: String): List<SetRow>
    @Query("SELECT * FROM logged_sets WHERE sessionId = :id ORDER BY position, id")
    fun observeSets(id: String): Flow<List<SetRow>>
    @Query("UPDATE sessions SET status = 'finished' WHERE id = :id AND status NOT IN ('conflict', 'conflict_deleted')")
    suspend fun finish(id: String)
    @Query("UPDATE sessions SET notes = :notes, localRevision = localRevision + 1, status = CASE WHEN status = 'pushed' THEN 'finished' ELSE status END WHERE id = :id")
    suspend fun updateSessionNotes(id: String, notes: String?)
    @Query("UPDATE logged_sets SET notes = :notes WHERE id = :id")
    suspend fun updateSetNotes(id: String, notes: String?)
    @Transaction
    suspend fun saveNotes(id: String, setId: String, sessionNotes: String?, setNotes: String?) {
        updateSessionNotes(id, sessionNotes)
        updateSetNotes(sets(id).firstOrNull()?.id ?: setId, setNotes)
    }
    @Query("UPDATE sessions SET sharedLocalRevision = localRevision WHERE id = :id")
    suspend fun shared(id: String)
    @Query("UPDATE sessions SET pushedLocalRevision = MAX(pushedLocalRevision, :revision), baseRevision = :serverRevision, number = :number, status = CASE WHEN localRevision > :revision THEN 'finished' ELSE 'pushed' END WHERE id = :id")
    suspend fun acknowledge(id: String, revision: Long, serverRevision: Long, number: Long)
    @Query("UPDATE sessions SET status = 'conflict', serverSnapshot = :snapshot WHERE id = :id")
    suspend fun conflict(id: String, snapshot: String)
    @Query("UPDATE sessions SET status = 'conflict_deleted', serverSnapshot = NULL WHERE id = :id")
    suspend fun deletedConflict(id: String)
    @Query("UPDATE sessions SET status = 'finished', baseRevision = NULL, localRevision = localRevision + 1, serverSnapshot = NULL WHERE id = :id AND status = 'conflict_deleted'")
    suspend fun recreateDeleted(id: String)
    @Query("DELETE FROM sessions WHERE id = :id AND status = 'conflict_deleted'")
    suspend fun deleteConflictRow(id: String)
    @Transaction
    suspend fun takeDeleted(id: String) {
        if (get(id)?.status != "conflict_deleted") return
        deleteSets(id)
        deleteConflictRow(id)
    }
    @Query("UPDATE sessions SET status = 'finished', baseRevision = :revision, localRevision = MAX(localRevision, :clientRevision) + 1, serverSnapshot = NULL WHERE id = :id")
    suspend fun keepMine(id: String, revision: Long, clientRevision: Long)
    @Query("DELETE FROM logged_sets WHERE sessionId = :id")
    suspend fun deleteSets(id: String)
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putSets(rows: List<SetRow>)
    @Query("SELECT id FROM sessions WHERE status = 'pushed'")
    suspend fun cachedIds(): List<String>
    @Query("DELETE FROM sessions WHERE id = :id AND status = 'pushed'")
    suspend fun deleteCached(id: String)

    @Transaction
    suspend fun snapshot(id: String): Pair<SessionRow, List<SetRow>>? {
        val row = get(id) ?: return null
        return row to sets(id)
    }

    @Transaction
    suspend fun shareSnapshot(id: String): Pair<SessionRow, List<SetRow>>? {
        val result = snapshot(id) ?: return null
        shared(id)
        return result
    }

    @Transaction
    suspend fun handleConflict(remote: ServerSession) {
        val local = get(remote.id) ?: return
        if (local.baseRevision == null && local.sharedLocalRevision != null && local.localRevision == local.sharedLocalRevision)
            adopt(remote, local.localRevision)
        else conflict(local.id, remote.json())
    }

    @Transaction
    suspend fun replaceCache(snapshots: List<ServerSession>) {
        // Run only after the full response has been decoded. Never remove a draft.
        val remoteIds = snapshots.map { it.id }.toSet()
        cachedIds().filter { it !in remoteIds }.forEach { id ->
            deleteSets(id)
            deleteCached(id)
        }
        snapshots.forEach { remote ->
            val local = get(remote.id)
            when {
                local == null -> adopt(remote, remote.clientRevision ?: 0)
                local.status == "pushed" -> adopt(remote, maxOf(local.localRevision, remote.clientRevision ?: 0))
                local.status != "in_progress" && local.localRevision > local.pushedLocalRevision && local.baseRevision == null -> handleConflict(remote)
            }
        }
    }

    @Transaction
    suspend fun adopt(remote: ServerSession, revision: Long) {
        deleteSets(remote.id)
        val acknowledged = maxOf(revision, remote.clientRevision ?: 0)
        put(SessionRow(remote.id, remote.performedOn, remote.title ?: "Workout", remote.notes,
            "pushed", acknowledged, acknowledged, remote.revision, null, remote.number, null))
        putSets(remote.sets.sortedBy { it.position }.mapIndexed { index, set -> SetRow(set.id, remote.id, set.load, set.unit.uppercase(), set.reps, set.rir, set.exerciseName, set.notes, index) })
    }
}

fun newSession(id: String) = SessionRow(id, LocalDate.now().toString(), "Push A", null,
    "in_progress", 0, 0, null, null, null, null)
