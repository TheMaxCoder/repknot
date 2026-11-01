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
            WorkoutDatabase::class.java, name).addMigrations(WorkoutDatabase.MIGRATION_4_5).build()
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
}
