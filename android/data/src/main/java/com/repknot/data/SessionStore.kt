package com.repknot.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/** Sync state follows ADR 001: status, local/pushed/base revisions and the conflict snapshot. */
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
    val programId: String? = null,
    val programWorkoutKey: String? = null,
)

/** One exercise in a session, with the prescription snapshot it was started from (ADR 002). */
@Entity(tableName = "session_exercises")
data class SessionExerciseRow(
    @PrimaryKey val id: String,
    val sessionId: String,
    val position: Int,
    val exerciseId: String,
    val exerciseName: String,
    val plannedSets: Int? = null,
    val plannedRepMin: Int? = null,
    val plannedRepMax: Int? = null,
    val plannedRirMin: Int? = null,
    val plannedRirMax: Int? = null,
    val plannedLoad: Double? = null,
    val plannedUnit: String? = null,
    val substitutedFor: String? = null,
    val notes: String? = null,
)

/** Units are stored as the server's lowercase strings (`kg|lb|plates|bodyweight`). */
@Entity(tableName = "logged_sets")
data class SetRow(
    @PrimaryKey val id: String,
    val sessionId: String,
    val sessionExerciseId: String,
    val position: Int,
    val load: Double?,
    val unit: String?,
    val reps: Double?,
    val durationS: Int? = null,
    val rir: Int? = null,
    val notes: String? = null,
)

data class SessionSnapshot(val row: SessionRow, val exercises: List<SessionExerciseRow>, val sets: List<SetRow>)

@Dao
interface SessionDao {
    @Query("SELECT * FROM sessions WHERE id = :id")
    fun observe(id: String): Flow<SessionRow?>
    @Query("SELECT * FROM sessions WHERE id = :id")
    suspend fun get(id: String): SessionRow?
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(row: SessionRow)
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfAbsent(row: SessionRow): Long
    @Query("SELECT * FROM sessions WHERE status = 'finished' AND localRevision > pushedLocalRevision")
    suspend fun pending(): List<SessionRow>
    @Query("SELECT * FROM session_exercises WHERE sessionId = :id ORDER BY position, id")
    suspend fun exercises(id: String): List<SessionExerciseRow>
    @Query("SELECT * FROM session_exercises WHERE sessionId = :id ORDER BY position, id")
    fun observeExercises(id: String): Flow<List<SessionExerciseRow>>
    @Query("SELECT * FROM logged_sets WHERE sessionId = :id ORDER BY position, id")
    suspend fun sets(id: String): List<SetRow>
    @Query("SELECT * FROM logged_sets WHERE sessionId = :id ORDER BY position, id")
    fun observeSets(id: String): Flow<List<SetRow>>
    @Query("SELECT * FROM logged_sets WHERE id = :id")
    suspend fun getSet(id: String): SetRow?
    @Query("SELECT * FROM session_exercises WHERE id = :id")
    suspend fun getExercise(id: String): SessionExerciseRow?
    @Query("DELETE FROM logged_sets WHERE sessionExerciseId = :id")
    suspend fun deleteExerciseSets(id: String)
    @Query("DELETE FROM session_exercises WHERE id = :id")
    suspend fun deleteExerciseRow(id: String)

    fun observeSession(id: String): Flow<SessionSnapshot?> =
        combine(observe(id), observeExercises(id), observeSets(id)) { row, exercises, sets ->
            row?.let { SessionSnapshot(it, exercises, sets) }
        }

    @Transaction
    suspend fun addExercise(row: SessionExerciseRow) {
        val position = (exercises(row.sessionId).maxOfOrNull { it.position } ?: -1) + 1
        putExercises(listOf(row.copy(position = position)))
        bump(row.sessionId)
    }
    @Transaction
    suspend fun removeExercise(id: String) {
        val row = getExercise(id) ?: return
        deleteExerciseSets(id)
        deleteExerciseRow(id)
        putExercises(exercises(row.sessionId).mapIndexed { index, exercise -> exercise.copy(position = index) })
        bump(row.sessionId)
    }
    @Transaction
    suspend fun moveExercise(id: String, delta: Int) {
        if (delta != -1 && delta != 1) return
        val row = getExercise(id) ?: return
        val neighbor = exercises(row.sessionId).firstOrNull { it.position == row.position + delta } ?: return
        putExercises(listOf(row.copy(position = neighbor.position), neighbor.copy(position = row.position)))
        bump(row.sessionId)
    }
    @Transaction
    suspend fun substituteExercise(id: String, exerciseId: String, exerciseName: String) {
        val row = getExercise(id) ?: return
        val original = row.substitutedFor ?: row.exerciseId
        putExercises(listOf(row.copy(exerciseId = exerciseId, exerciseName = exerciseName,
            substitutedFor = if (exerciseId == original) null else original)))
        bump(row.sessionId)
    }
    @Transaction
    suspend fun saveExerciseNotes(id: String, notes: String?) {
        val row = getExercise(id) ?: return
        putExercises(listOf(row.copy(notes = notes)))
        bump(row.sessionId)
    }
    @Query("SELECT * FROM sessions")
    suspend fun allSessions(): List<SessionRow>
    /** Emits whenever any session row changes (refresh, finish, edits); used as a reload trigger. */
    @Query("SELECT * FROM sessions")
    fun observeAllSessions(): Flow<List<SessionRow>>
    @Query("SELECT * FROM session_exercises")
    suspend fun allExercises(): List<SessionExerciseRow>
    @Query("SELECT * FROM logged_sets")
    suspend fun allSets(): List<SetRow>

    @Query("UPDATE sessions SET status = 'finished' WHERE id = :id AND status NOT IN ('conflict', 'conflict_deleted')")
    suspend fun finish(id: String)
    /** Every local edit bumps the local revision; an uploaded session becomes pending again. */
    @Query("UPDATE sessions SET localRevision = localRevision + 1, status = CASE WHEN status = 'pushed' THEN 'finished' ELSE status END WHERE id = :id")
    suspend fun bump(id: String)
    @Query("UPDATE sessions SET notes = :notes WHERE id = :id")
    suspend fun setSessionNotes(id: String, notes: String?)
    @Transaction
    suspend fun saveSessionNotes(id: String, notes: String?) {
        setSessionNotes(id, notes)
        bump(id)
    }

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putExercises(rows: List<SessionExerciseRow>)
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putSets(rows: List<SetRow>)
    @Query("DELETE FROM logged_sets WHERE id = :id")
    suspend fun deleteSetRow(id: String)
    @Query("DELETE FROM logged_sets WHERE sessionId = :id")
    suspend fun deleteSets(id: String)
    @Query("DELETE FROM session_exercises WHERE sessionId = :id")
    suspend fun deleteExercises(id: String)

    /** Starts a session from a prescription snapshot; an existing session is left untouched. */
    @Transaction
    suspend fun start(row: SessionRow, exercises: List<SessionExerciseRow>): Boolean {
        if (insertIfAbsent(row) == -1L) return false
        putExercises(exercises)
        return true
    }
    @Query("DELETE FROM sessions WHERE id = :id")
    suspend fun deleteSessionRow(id: String)
    /**
     * Abandons a started workout. Only a session that is still in progress and was never
     * uploaded (no server number or base revision) can be discarded; returns whether it was.
     */
    @Transaction
    suspend fun discard(id: String): Boolean {
        val row = get(id) ?: return false
        if (row.status != "in_progress" || row.number != null || row.baseRevision != null) return false
        deleteSets(id)
        deleteExercises(id)
        deleteSessionRow(id)
        return true
    }
    /** Values and the new revision are written in one transaction. */
    @Transaction
    suspend fun saveSet(set: SetRow) {
        putSets(listOf(set))
        bump(set.sessionId)
    }
    @Transaction
    suspend fun deleteSet(id: String) {
        val set = getSet(id) ?: return
        deleteSetRow(id)
        bump(set.sessionId)
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
        deleteExercises(id)
        deleteConflictRow(id)
    }
    @Query("UPDATE sessions SET status = 'finished', baseRevision = :revision, localRevision = MAX(localRevision, :clientRevision) + 1, serverSnapshot = NULL WHERE id = :id")
    suspend fun keepMine(id: String, revision: Long, clientRevision: Long)
    @Query("SELECT id FROM sessions WHERE status = 'pushed'")
    suspend fun cachedIds(): List<String>
    @Query("DELETE FROM sessions WHERE id = :id AND status = 'pushed'")
    suspend fun deleteCached(id: String)

    @Transaction
    suspend fun snapshot(id: String): SessionSnapshot? {
        val row = get(id) ?: return null
        return SessionSnapshot(row, exercises(id), sets(id))
    }

    @Transaction
    suspend fun shareSnapshot(id: String): SessionSnapshot? {
        val result = snapshot(id) ?: return null
        shared(id)
        return result
    }

    /** All sessions as domain history for today's workout and prefill. */
    @Transaction
    suspend fun history(): List<com.repknot.domain.PastSession> = pastSessions(allSessions(), allExercises(), allSets())

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
            deleteExercises(id)
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
        deleteExercises(remote.id)
        val acknowledged = maxOf(revision, remote.clientRevision ?: 0)
        put(SessionRow(remote.id, remote.performedOn, remote.title ?: "Workout", remote.notes,
            "pushed", acknowledged, acknowledged, remote.revision, null, remote.number, null,
            remote.programId, remote.programWorkoutKey))
        putExercises(remote.exerciseRows())
        putSets(remote.setRows())
    }
}
