package com.repknot.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query

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
    @Query("SELECT * FROM program_workouts WHERE programId = :programId")
    suspend fun getWorkouts(programId: String): List<ProgramWorkoutRow>
    @Query("SELECT * FROM program_exercises")
    suspend fun getPrescriptions(): List<ProgramExerciseRow>
    @Query("SELECT * FROM exercises")
    suspend fun getExercises(): List<ExerciseRow>
}
