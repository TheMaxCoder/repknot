package com.repknot.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [SessionRow::class, SessionExerciseRow::class, SetRow::class, ExerciseRow::class,
    ProgramRow::class, ProgramWorkoutRow::class, ProgramExerciseRow::class], version = 5, exportSchema = true)
abstract class WorkoutDatabase : RoomDatabase() {
    abstract fun sessions(): SessionDao
    abstract fun reference(): ReferenceDao

    companion object {
        /** v4 → v5: session light flag. From v4 on the phone holds real workouts: migrate, never reset. */
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE sessions ADD COLUMN light INTEGER NOT NULL DEFAULT 0")
            }
        }
        // Versions 1–3 were synthetic building-phase data and may still be reset.
        fun open(context: Context): WorkoutDatabase = Room.databaseBuilder(
            context.applicationContext, WorkoutDatabase::class.java, "workouts.db",
        ).addMigrations(MIGRATION_4_5).fallbackToDestructiveMigrationFrom(true, 1, 2, 3).build()
    }
}
