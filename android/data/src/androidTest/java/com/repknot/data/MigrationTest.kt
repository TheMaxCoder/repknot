package com.repknot.data

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** From v4 the phone holds real workouts: upgrades must keep them (task 019 step 4). */
@RunWith(AndroidJUnit4::class)
class MigrationTest {
    private val name = "migration-test.db"
    private val name6 = "migration-test-6.db"
    private val name7 = "migration-test-7.db"
    private val name8 = "migration-test-8.db"
    @get:Rule val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), WorkoutDatabase::class.java)

    @Test fun v4WorkoutSurvivesUpgradeToV5WithLightOff() {
        helper.createDatabase(name, 4).apply {
            execSQL("""INSERT INTO sessions (id, performedOn, title, notes, status, localRevision, pushedLocalRevision,
                baseRevision, sharedLocalRevision, number, serverSnapshot, programId, programWorkoutKey)
                VALUES ('s1', '2026-11-02', 'Push 1', 'real', 'finished', 5, 0, NULL, NULL, NULL, NULL, NULL, NULL)""")
            execSQL("""INSERT INTO session_exercises (id, sessionId, position, exerciseId, exerciseName)
                VALUES ('e1', 's1', 0, 'press', 'Press')""")
            execSQL("""INSERT INTO logged_sets (id, sessionId, sessionExerciseId, position, load, unit, reps, rir)
                VALUES ('t1', 's1', 'e1', 0, 25.0, 'kg', 10.0, 1)""")
            close()
        }
        helper.runMigrationsAndValidate(name, 5, true, WorkoutDatabase.MIGRATION_4_5).close()
        val db = Room.databaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext,
            WorkoutDatabase::class.java, name).addMigrations(WorkoutDatabase.MIGRATION_4_5, WorkoutDatabase.MIGRATION_5_6, WorkoutDatabase.MIGRATION_6_7, WorkoutDatabase.MIGRATION_7_8).build()
        try {
            runBlocking {
                val row = db.sessions().get("s1")!!
                assertEquals("finished", row.status)
                assertEquals(5L, row.localRevision)
                assertFalse(row.light)
                assertEquals(10.0, db.sessions().sets("s1").single().reps!!, 0.0)
                db.sessions().setLight("s1", true)
                assertEquals(true, db.sessions().get("s1")!!.light)
                assertEquals(6L, db.sessions().get("s1")!!.localRevision)
            }
        } finally { db.close() }
    }

    @Test fun v5UpgradesToV6WithAnEmptyCheckInTable() {
        helper.createDatabase(name6, 5).apply {
            execSQL("""INSERT INTO sessions (id, performedOn, title, notes, status, localRevision, pushedLocalRevision,
                baseRevision, sharedLocalRevision, number, serverSnapshot, programId, programWorkoutKey, light)
                VALUES ('s1', '2026-11-02', 'Push 1', NULL, 'in_progress', 2, 0, NULL, NULL, NULL, NULL, NULL, NULL, 1)""")
            close()
        }
        helper.runMigrationsAndValidate(name6, 6, true, WorkoutDatabase.MIGRATION_5_6).close()
        val db = Room.databaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext,
            WorkoutDatabase::class.java, name6).addMigrations(WorkoutDatabase.MIGRATION_4_5, WorkoutDatabase.MIGRATION_5_6, WorkoutDatabase.MIGRATION_6_7, WorkoutDatabase.MIGRATION_7_8).build()
        try {
            runBlocking {
                assertEquals(true, db.sessions().get("s1")!!.light)
                assertEquals(0, db.checkIns().all().size)
                db.checkIns().add(CheckInRow("c1", "2026-11-02", waistCm = 91.0))
                assertEquals(true, db.checkIns().all().single().pending)
            }
        } finally { db.close() }
    }

    @Test fun v6WorkoutSurvivesUpgradeToV7WithoutTimingAndRecordsItAfterwards() {
        helper.createDatabase(name7, 6).apply {
            execSQL("""INSERT INTO sessions (id, performedOn, title, notes, status, localRevision, pushedLocalRevision,
                baseRevision, sharedLocalRevision, number, serverSnapshot, programId, programWorkoutKey, light)
                VALUES ('s1', '2026-11-02', 'Push 1', NULL, 'in_progress', 3, 0, NULL, NULL, NULL, NULL, NULL, NULL, 0)""")
            execSQL("""INSERT INTO session_exercises (id, sessionId, position, exerciseId, exerciseName)
                VALUES ('e1', 's1', 0, 'press', 'Press')""")
            execSQL("""INSERT INTO logged_sets (id, sessionId, sessionExerciseId, position, load, unit, reps, rir)
                VALUES ('t1', 's1', 'e1', 0, 25.0, 'kg', 10.0, 1)""")
            close()
        }
        helper.runMigrationsAndValidate(name7, 7, true, WorkoutDatabase.MIGRATION_6_7).close()
        val db = Room.databaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext,
            WorkoutDatabase::class.java, name7).addMigrations(WorkoutDatabase.MIGRATION_4_5, WorkoutDatabase.MIGRATION_5_6, WorkoutDatabase.MIGRATION_6_7, WorkoutDatabase.MIGRATION_7_8).build()
        try {
            runBlocking {
                val row = db.sessions().get("s1")!!
                assertEquals("in_progress", row.status)
                assertEquals(null, row.startedAt)
                assertEquals(null, db.sessions().sets("s1").single().loggedAt)
                // Finish records the time once; a retry finish keeps the first value.
                db.sessions().finishAt("s1", 1000L)
                db.sessions().finishAt("s1", 2000L)
                assertEquals(1000L, db.sessions().get("s1")!!.finishedAt)
                assertEquals("finished", db.sessions().get("s1")!!.status)
            }
        } finally { db.close() }
    }

    @Test fun v7UpgradeToV8KeepsDataAndMarksOnlyRowsTheOldSwitchWouldUpload() {
        helper.createDatabase(name8, 7).apply {
            fun session(id: String, status: String, pushed: Long, base: String, number: String) = execSQL(
                """INSERT INTO sessions (id, performedOn, title, notes, status, localRevision, pushedLocalRevision,
                baseRevision, sharedLocalRevision, number, serverSnapshot, programId, programWorkoutKey, light, startedAt, finishedAt)
                VALUES ('$id', '2026-11-02', 'Push 1', NULL, '$status', 3, $pushed, $base, NULL, $number, NULL, NULL, NULL, 0, 100, NULL)""")
            session("current", "in_progress", 0, "NULL", "NULL")
            session("waiting", "finished", 0, "NULL", "NULL")
            session("uploaded", "pushed", 3, "5", "405")
            execSQL("""INSERT INTO session_exercises (id, sessionId, position, exerciseId, exerciseName)
                VALUES ('e1', 'current', 0, 'press', 'Press')""")
            execSQL("""INSERT INTO logged_sets (id, sessionId, sessionExerciseId, position, load, unit, reps, rir, loggedAt)
                VALUES ('t1', 'current', 'e1', 0, 25.0, 'kg', 10.0, 1, 200)""")
            execSQL("""INSERT INTO checkins (id, checkedOn, weightKg, waistCm, chestCm, armsCm, armsFlexedCm, legsCm, notes, pending)
                VALUES ('c1', '2026-11-01', 75.0, 91.0, NULL, NULL, NULL, NULL, NULL, 1)""")
            close()
        }
        helper.runMigrationsAndValidate(name8, 8, true, WorkoutDatabase.MIGRATION_7_8).close()
        val db = Room.databaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext,
            WorkoutDatabase::class.java, name8).addMigrations(WorkoutDatabase.MIGRATION_4_5, WorkoutDatabase.MIGRATION_5_6, WorkoutDatabase.MIGRATION_6_7, WorkoutDatabase.MIGRATION_7_8).build()
        try {
            runBlocking {
                val current = db.sessions().get("current")!!
                assertFalse(current.uploadRequested)
                assertEquals(100L, current.startedAt)
                assertEquals(200L, db.sessions().sets("current").single().loggedAt)
                assertEquals(true, db.sessions().get("waiting")!!.uploadRequested)
                assertEquals(true, db.sessions().get("uploaded")!!.uploadRequested)
                assertEquals(listOf("waiting"), db.sessions().pending().map { it.id })
                assertEquals(listOf("c1"), db.checkIns().pending().map { it.id })
                // The in-progress workout still needs an explicit Upload after finishing.
                db.sessions().finish("current")
                assertEquals(listOf("waiting"), db.sessions().pending().map { it.id })
            }
        } finally { db.close() }
    }
}
