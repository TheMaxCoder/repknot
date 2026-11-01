package com.repknot.data

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class WorkoutDatabaseTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var db: WorkoutDatabase

    @Before fun openInMemory() { db = Room.inMemoryDatabaseBuilder(context, WorkoutDatabase::class.java).build() }
    @After fun close() = db.close()

    @Test fun savingTheSameSetOverwritesItAndEachEditAdvancesTheRevision() = runBlocking {
        assertTrue(db.sessions().observeSets("session").first().isEmpty())
        db.logSet("session", "set", 20.0)
        assertEquals(1L, db.sessions().get("session")!!.localRevision)
        db.logSet("session", "set", 22.5)
        assertEquals(2L, db.sessions().get("session")!!.localRevision)
        assertEquals(listOf(22.5), db.sessions().sets("session").map { it.load })
        db.sessions().deleteSet("set")
        assertEquals(3L, db.sessions().get("session")!!.localRevision)
        assertTrue(db.sessions().sets("session").isEmpty())
    }

    @Test fun startingSnapshotsExercisesOnceAndKeepsAnExistingSession() = runBlocking {
        val exercises = listOf(SessionExerciseRow("e1", "s", 0, "press-id", "DB press", plannedSets = 3, plannedUnit = "kg"))
        assertTrue(db.sessions().start(draft("s"), exercises))
        assertFalse(db.sessions().start(draft("s").copy(title = "Other"), emptyList()))
        assertEquals("Push", db.sessions().get("s")!!.title)
        assertEquals(exercises, db.sessions().exercises("s"))
    }

    @Test fun referenceCacheReplacesEverythingAndYieldsTheActiveProgram() = runBlocking {
        fun snapshot(active: Boolean) = ReferenceSnapshot(
            listOf(ServerExercise("e1", "press", "DB press", "kg")),
            listOf(ServerProgram("p1", "ppl", "PPL", 1, "2026-11-02", null, active, listOf(
                ServerProgramWorkout("w1", "push-1", "Push 1", 0, 1, listOf(
                    ServerPrescription("r1", 0, "e1", 3, 8, 12, 1, 2, 120, 180, 25.0, "kg")))))))
        assertNull(db.reference().activeProgram())
        db.reference().replaceAll(snapshot(true))
        db.reference().replaceAll(snapshot(true))
        val program = db.reference().activeProgram()!!
        assertEquals("DB press", program.workouts.single().exercises.single().exerciseName)
        db.reference().replaceAll(snapshot(false))
        assertNull(db.reference().activeProgram())
    }

    @Test fun recordedValuesSurviveDatabaseReopen() = runBlocking {
        val name = "persistence-test.db"
        context.deleteDatabase(name)
        var fileDb = Room.databaseBuilder(context, WorkoutDatabase::class.java, name).build()
        try {
            fileDb.logSet("session", "set", 32.5)
            fileDb.close()
            fileDb = Room.databaseBuilder(context, WorkoutDatabase::class.java, name).build()
            assertEquals(32.5, fileDb.sessions().sets("session").single().load!!, 0.0)
        } finally {
            fileDb.close()
            context.deleteDatabase(name)
        }
    }
}
