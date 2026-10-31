package com.repknot.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import com.repknot.domain.LoadUnit
import com.repknot.domain.LoggedSet
import com.repknot.domain.SetRepository
import com.repknot.domain.SetValues
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Every local save increments [localRevision]. A sync acknowledgement records the revision it
 * uploaded in [syncedRevision], so a late acknowledgement for an older revision cannot mark a
 * newer edit as synced. The set is pending while localRevision > syncedRevision.
 */
@Entity(tableName = "logged_sets")
data class SetRow(
    @PrimaryKey val id: String,
    val sessionId: String,
    val load: Double,
    val unit: String,
    val reps: Int,
    val rir: Int?,
    val localRevision: Long,
    val syncedRevision: Long,
)

@Dao
interface SetDao {
    @Query("SELECT * FROM logged_sets WHERE id = :id")
    fun observe(id: String): Flow<SetRow?>

    @Query("SELECT * FROM logged_sets WHERE id = :id")
    suspend fun get(id: String): SetRow?

    @Insert
    suspend fun insert(row: SetRow)

    @Query(
        """UPDATE logged_sets SET sessionId = :sessionId, load = :load, unit = :unit, reps = :reps,
           rir = :rir, localRevision = localRevision + 1 WHERE id = :id""",
    )
    suspend fun update(id: String, sessionId: String, load: Double, unit: String, reps: Int, rir: Int?)

    /** Values and the new revision are written in one transaction. */
    @Transaction
    suspend fun save(id: String, sessionId: String, values: SetValues) {
        if (get(id) == null) {
            insert(SetRow(id, sessionId, values.load, values.unit.name, values.reps, values.rir, 1, 0))
        } else {
            update(id, sessionId, values.load, values.unit.name, values.reps, values.rir)
        }
    }

    /** Records an acknowledged upload; never moves syncedRevision backwards. */
    @Query("UPDATE logged_sets SET syncedRevision = MAX(syncedRevision, :revision) WHERE id = :id")
    suspend fun markSynced(id: String, revision: Long)
}

@Database(entities = [SetRow::class], version = 1, exportSchema = true)
abstract class WorkoutDatabase : RoomDatabase() {
    abstract fun sets(): SetDao

    companion object {
        fun open(context: Context): WorkoutDatabase = Room.databaseBuilder(
            context.applicationContext, WorkoutDatabase::class.java, "workouts.db",
        ).build()
    }
}

class RoomSetRepository(private val dao: SetDao) : SetRepository {
    companion object {
        fun open(context: Context): SetRepository = RoomSetRepository(WorkoutDatabase.open(context).sets())
    }

    override fun observeSet(id: String): Flow<LoggedSet?> = dao.observe(id).map { row ->
        row?.let {
            LoggedSet(
                it.id, it.sessionId,
                SetValues(it.load, LoadUnit.valueOf(it.unit), it.reps, it.rir),
                pendingSync = it.localRevision > it.syncedRevision,
            )
        }
    }

    override suspend fun save(id: String, sessionId: String, values: SetValues) =
        dao.save(id, sessionId, values)
}
