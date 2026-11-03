package com.repknot.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [SessionRow::class, SessionExerciseRow::class, SetRow::class, ExerciseRow::class,
    ProgramRow::class, ProgramWorkoutRow::class, ProgramExerciseRow::class, CheckInRow::class], version = 9, exportSchema = true)
abstract class WorkoutDatabase : RoomDatabase() {
    abstract fun sessions(): SessionDao
    abstract fun reference(): ReferenceDao
    abstract fun checkIns(): CheckInDao

    companion object {
        /** v4 → v5: session light flag. From v4 on the phone holds real workouts: migrate, never reset. */
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE sessions ADD COLUMN light INTEGER NOT NULL DEFAULT 0")
            }
        }
        /** v5 → v6: body check-ins (cache + pending phone entries). */
        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `checkins` (`id` TEXT NOT NULL, `checkedOn` TEXT NOT NULL, `weightKg` REAL, `waistCm` REAL, `chestCm` REAL, `armsCm` REAL, `armsFlexedCm` REAL, `legsCm` REAL, `notes` TEXT, `pending` INTEGER NOT NULL, PRIMARY KEY(`id`))")
            }
        }
        /** v6 → v7: workout timing (task 027); existing rows stay null. */
        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE sessions ADD COLUMN startedAt INTEGER")
                db.execSQL("ALTER TABLE sessions ADD COLUMN finishedAt INTEGER")
                db.execSQL("ALTER TABLE logged_sets ADD COLUMN loggedAt INTEGER")
            }
        }
        /** v7 → v8: explicit upload (task 028). Rows the old global switch would upload stay marked. */
        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE sessions ADD COLUMN uploadRequested INTEGER NOT NULL DEFAULT 0")
                db.execSQL("UPDATE sessions SET uploadRequested = 1 WHERE status <> 'in_progress' OR number IS NOT NULL OR baseRevision IS NOT NULL")
                db.execSQL("ALTER TABLE checkins ADD COLUMN uploadRequested INTEGER NOT NULL DEFAULT 0")
                db.execSQL("UPDATE checkins SET uploadRequested = 1")
            }
        }
        /** v8 → v9: session source (display only); filled when the next refresh re-adopts server rows. */
        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE sessions ADD COLUMN source TEXT")
            }
        }
        // Versions 1–3 were synthetic building-phase data and may still be reset.
        fun open(context: Context): WorkoutDatabase = Room.databaseBuilder(
            context.applicationContext, WorkoutDatabase::class.java, "workouts.db",
        ).addMigrations(MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9).fallbackToDestructiveMigrationFrom(true, 1, 2, 3).build()
    }
}
