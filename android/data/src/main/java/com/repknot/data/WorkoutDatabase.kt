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

@Entity(tableName = "logged_sets")
data class SetRow(
    @PrimaryKey val id: String,
    val sessionId: String,
    val load: Double,
    val unit: String,
    val reps: Int,
    val rir: Int?,
    @androidx.room.ColumnInfo(defaultValue = "'Dumbbell bench press'") val exerciseName: String = "Dumbbell bench press",
    val notes: String? = null,
    @androidx.room.ColumnInfo(defaultValue = "0") val position: Int = 0,
)

@Dao
interface SetDao {
    @Query("SELECT * FROM logged_sets WHERE sessionId = :sessionId ORDER BY position, id LIMIT 1")
    fun observeFirst(sessionId: String): Flow<SetRow?>

    @Query("SELECT * FROM logged_sets WHERE id = :id")
    suspend fun get(id: String): SetRow?

    @Insert
    suspend fun insert(row: SetRow)

    @Insert(onConflict = androidx.room.OnConflictStrategy.IGNORE)
    suspend fun insertSession(row: SessionRow)

    @Query("UPDATE sessions SET localRevision = localRevision + 1, status = CASE WHEN status = 'pushed' THEN 'finished' ELSE status END WHERE id = :id")
    suspend fun bumpSession(id: String)

    @Query(
        """UPDATE logged_sets SET sessionId = :sessionId, load = :load, unit = :unit, reps = :reps,
           rir = :rir WHERE id = :id""",
    )
    suspend fun update(id: String, sessionId: String, load: Double, unit: String, reps: Int, rir: Int?)

    /** Values and the new revision are written in one transaction. */
    @Transaction
    suspend fun save(id: String, sessionId: String, values: SetValues) {
        insertSession(newSession(sessionId))
        bumpSession(sessionId)
        if (get(id) == null) {
            insert(SetRow(id, sessionId, values.load, values.unit.name, values.reps, values.rir))
        } else {
            update(id, sessionId, values.load, values.unit.name, values.reps, values.rir)
        }
    }
}

@Database(entities = [SetRow::class, SessionRow::class, ExerciseRow::class, ProgramRow::class,
    ProgramWorkoutRow::class, ProgramExerciseRow::class], version = 4, exportSchema = true)
abstract class WorkoutDatabase : RoomDatabase() {
    abstract fun sets(): SetDao
    abstract fun sessions(): SessionDao
    abstract fun reference(): ReferenceDao

    companion object {
        fun open(context: Context): WorkoutDatabase = Room.databaseBuilder(
            context.applicationContext, WorkoutDatabase::class.java, "workouts.db",
        ).fallbackToDestructiveMigrationFrom(true, 1, 2, 3).build()
    }
}

class RoomSetRepository(private val dao: SetDao) : SetRepository {
    companion object {
        fun open(context: Context): SetRepository = RoomSetRepository(WorkoutDatabase.open(context).sets())
    }

    override fun observeFirstSet(sessionId: String): Flow<LoggedSet?> = dao.observeFirst(sessionId).map { row ->
        row?.let {
            LoggedSet(
                it.id, it.sessionId,
                SetValues(it.load, LoadUnit.valueOf(it.unit), it.reps, it.rir),
            )
        }
    }

    override suspend fun save(id: String, sessionId: String, values: SetValues) =
        dao.save(id, sessionId, values)
}
