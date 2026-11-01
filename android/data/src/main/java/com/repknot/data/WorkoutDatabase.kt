package com.repknot.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [SessionRow::class, SessionExerciseRow::class, SetRow::class, ExerciseRow::class,
    ProgramRow::class, ProgramWorkoutRow::class, ProgramExerciseRow::class], version = 4, exportSchema = true)
abstract class WorkoutDatabase : RoomDatabase() {
    abstract fun sessions(): SessionDao
    abstract fun reference(): ReferenceDao

    companion object {
        // Building phase: local data is synthetic and disposable (docs/architecture.md).
        fun open(context: Context): WorkoutDatabase = Room.databaseBuilder(
            context.applicationContext, WorkoutDatabase::class.java, "workouts.db",
        ).fallbackToDestructiveMigrationFrom(true, 1, 2, 3).build()
    }
}
