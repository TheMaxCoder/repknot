package com.repknot.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Transaction
import com.repknot.domain.LoadUnit
import com.repknot.domain.Prescription
import com.repknot.domain.Program
import com.repknot.domain.ProgramWorkout
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "exercises")
data class ExerciseRow(
    @PrimaryKey val id: String, val slug: String, val name: String,
    val defaultUnit: String?, val archived: Boolean,
)

@Entity(tableName = "programs")
data class ProgramRow(
    @PrimaryKey val id: String, val key: String, val name: String, val version: Int,
    val startsOn: String, val rules: String?, val isActive: Boolean,
)

@Entity(tableName = "program_workouts")
data class ProgramWorkoutRow(
    @PrimaryKey val id: String, val programId: String, val key: String, val name: String,
    val sequencePosition: Int, val defaultWeekday: Int?,
)

@Entity(tableName = "program_exercises")
data class ProgramExerciseRow(
    @PrimaryKey val id: String, val workoutId: String, val position: Int, val exerciseId: String,
    val sets: Int, val repMin: Int?, val repMax: Int?, val rirMin: Int?, val rirMax: Int?,
    val restSMin: Int?, val restSMax: Int?, val load: Double?, val unit: String, val notes: String?,
)

@Dao
interface ReferenceDao {
    @Query("DELETE FROM program_exercises")
    suspend fun deletePrescriptions()
    @Query("DELETE FROM program_workouts")
    suspend fun deleteWorkouts()
    @Query("DELETE FROM programs")
    suspend fun deletePrograms()
    @Query("DELETE FROM exercises")
    suspend fun deleteExercises()

    @Insert
    suspend fun insertExercises(rows: List<ExerciseRow>)
    @Insert
    suspend fun insertPrograms(rows: List<ProgramRow>)
    @Insert
    suspend fun insertWorkouts(rows: List<ProgramWorkoutRow>)
    @Insert
    suspend fun insertPrescriptions(rows: List<ProgramExerciseRow>)

    @Query("SELECT * FROM programs WHERE isActive = 1 LIMIT 1")
    suspend fun getActiveProgram(): ProgramRow?
    @Query("SELECT id FROM programs WHERE `key` = :key")
    suspend fun getProgramVersionIds(key: String): List<String>
    @Query("SELECT * FROM programs WHERE isActive = 1 LIMIT 1")
    fun observeActiveProgramRow(): Flow<ProgramRow?>
    @Query("SELECT * FROM exercises WHERE archived = 0 ORDER BY name")
    suspend fun activeExercises(): List<ExerciseRow>
    @Query("SELECT * FROM program_workouts WHERE programId = :programId")
    suspend fun getWorkouts(programId: String): List<ProgramWorkoutRow>
    @Query("SELECT * FROM program_exercises")
    suspend fun getPrescriptions(): List<ProgramExerciseRow>
    @Query("SELECT * FROM exercises")
    suspend fun getExercises(): List<ExerciseRow>

    @Transaction
    suspend fun replaceAll(snapshot: ReferenceSnapshot) {
        val rows = snapshot.toRows()
        deletePrescriptions()
        deleteWorkouts()
        deletePrograms()
        deleteExercises()
        insertExercises(rows.exercises)
        insertPrograms(rows.programs)
        insertWorkouts(rows.workouts)
        insertPrescriptions(rows.prescriptions)
    }

    @Transaction
    suspend fun activeProgram(): Program? {
        val program = getActiveProgram() ?: return null
        return toDomainProgram(program, getWorkouts(program.id), getPrescriptions(), getExercises(),
            getProgramVersionIds(program.key))
    }
}

data class ReferenceRows(
    val exercises: List<ExerciseRow>, val programs: List<ProgramRow>,
    val workouts: List<ProgramWorkoutRow>, val prescriptions: List<ProgramExerciseRow>,
)

fun ReferenceSnapshot.toRows(): ReferenceRows = ReferenceRows(
    exercises = exercises.map { ExerciseRow(it.id, it.slug, it.name, it.defaultUnit, it.archivedAt != null) },
    programs = programs.map { ProgramRow(it.id, it.key, it.name, it.version, it.startsOn, it.rules, it.isActive) },
    workouts = programs.flatMap { program -> program.workouts.map {
        ProgramWorkoutRow(it.id, program.id, it.key, it.name, it.sequencePosition, it.defaultWeekday)
    } },
    prescriptions = programs.flatMap { program -> program.workouts.flatMap { workout -> workout.exercises.map {
        ProgramExerciseRow(it.id, workout.id, it.position, it.exerciseId, it.sets, it.repMin, it.repMax,
            it.rirMin, it.rirMax, it.restSMin, it.restSMax, it.load, it.unit, it.notes)
    } } },
)

fun parseUnit(value: String): LoadUnit = when (value.lowercase()) {
    "kg" -> LoadUnit.KG
    "lb" -> LoadUnit.LB
    "plates" -> LoadUnit.PLATES
    "bodyweight" -> LoadUnit.BODYWEIGHT
    else -> throw IllegalArgumentException("Unknown load unit: $value")
}

fun toDomainProgram(
    program: ProgramRow, workouts: List<ProgramWorkoutRow>,
    prescriptions: List<ProgramExerciseRow>, exercises: List<ExerciseRow>,
    versionIds: Collection<String> = emptyList(),
): Program {
    val names = exercises.associate { it.id to it.name }
    return Program(program.id, workouts.filter { it.programId == program.id }.map { workout ->
        ProgramWorkout(workout.key, workout.name, workout.sequencePosition,
            prescriptions.filter { it.workoutId == workout.id }.sortedBy { it.position }.map { row ->
                val name = names[row.exerciseId] ?: error("Missing exercise: ${row.exerciseId}")
                Prescription(row.position, row.exerciseId, name, row.sets, row.repMin, row.repMax,
                    row.rirMin, row.rirMax, row.load, parseUnit(row.unit))
            })
    }, versionIds.toSet() + program.id)
}
