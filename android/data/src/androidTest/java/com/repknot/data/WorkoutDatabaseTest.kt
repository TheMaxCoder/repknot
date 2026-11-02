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

    @Test fun exerciseEditsKeepOrderTrackTheOriginalAndBumpTheRevision() = runBlocking {
        val dao = db.sessions()
        dao.start(draft("s"), listOf(
            SessionExerciseRow("a", "s", 0, "press-id", "Press"), SessionExerciseRow("b", "s", 1, "deck-id", "Deck")))
        dao.addExercise(SessionExerciseRow("c", "s", 99, "row-id", "Row"))
        assertEquals(listOf("a" to 0, "b" to 1, "c" to 2), dao.exercises("s").map { it.id to it.position })
        dao.moveExercise("c", -1); dao.moveExercise("a", -1)
        assertEquals(listOf("a", "c", "b"), dao.exercises("s").map { it.id })
        dao.saveSet(SetRow("set-b", "s", "b", 0, 12.0, "plates", 12.0))
        dao.removeExercise("c")
        assertEquals(listOf("a" to 0, "b" to 1), dao.exercises("s").map { it.id to it.position })
        dao.substituteExercise("b", "fly-id", "Fly")
        assertEquals("deck-id", dao.getExercise("b")!!.substitutedFor)
        dao.substituteExercise("b", "cable-id", "Cable")
        assertEquals("deck-id", dao.getExercise("b")!!.substitutedFor)
        dao.substituteExercise("b", "deck-id", "Deck")
        assertNull(dao.getExercise("b")!!.substitutedFor)
        assertEquals(listOf("set-b"), dao.sets("s").map { it.id })
        dao.saveExerciseNotes("a", "elbow ok")
        assertEquals("elbow ok", dao.getExercise("a")!!.notes)
        // add, move c, set, remove, 3 substitutes, notes; moving the first item up is a no-op
        assertEquals(8L, dao.get("s")!!.localRevision)
    }

        @Test fun discardRemovesOnlyAWorkoutThatNeverLeftThePhone() = runBlocking {
        val dao = db.sessions()
        db.logSet("draft", "set", 20.0)
        assertTrue(dao.discard("draft"))
        assertNull(dao.get("draft")); assertTrue(dao.sets("draft").isEmpty()); assertTrue(dao.exercises("draft").isEmpty())
        // Finished but Upload not tapped (a test run): discardable.
        db.logSet("done", "set2", 20.0); dao.finish("done")
        assertTrue(dao.discard("done")); assertNull(dao.get("done"))
        // Upload tapped (maybe still in flight): kept.
        db.logSet("marked", "set3", 20.0); dao.finish("marked"); dao.requestUpload("marked")
        assertFalse(dao.discard("marked"))
        dao.put(draft("uploaded").copy(number = 7, baseRevision = 3))
        assertFalse(dao.discard("uploaded"))
        assertFalse(dao.discard("missing"))
    }

        @Test fun onlyWorkoutsMarkedForUploadArePending() = runBlocking {
        val dao = db.sessions()
        db.logSet("s1", "set", 20.0)
        assertEquals(0, dao.requestUpload("s1"))  // in progress: cannot be marked
        dao.finish("s1")
        assertTrue(dao.pending().isEmpty())
        assertEquals(1, dao.requestUpload("s1"))
        assertEquals(listOf("s1"), dao.pending().map { it.id })
        // Resume (never uploaded) clears the mark; finishing again needs a new tap.
        assertEquals(1, dao.resume("s1")); dao.finish("s1")
        assertTrue(dao.pending().isEmpty())
        // Once acknowledged, later edits sync without another tap.
        dao.requestUpload("s1"); dao.acknowledge("s1", dao.get("s1")!!.localRevision, 5, 42)
        db.logSet("s1", "set", 22.5)
        assertEquals(listOf("s1"), dao.pending().map { it.id })
    }

        @Test fun checkInsUploadOnlyWhenMarkedAndUnmarkedOnesCanBeDeleted() = runBlocking {
        val dao = db.checkIns()
        dao.add(CheckInRow("c1", "2026-11-02", waistCm = 91.0)); dao.add(CheckInRow("c2", "2026-11-02", weightKg = 75.0))
        assertTrue(dao.pending().isEmpty())
        assertEquals(1, dao.requestUpload("c1"))
        assertEquals(listOf("c1"), dao.pending().map { it.id })
        assertEquals(0, dao.deleteLocal("c1"))  // marked: kept
        assertEquals(1, dao.deleteLocal("c2"))
        assertEquals(listOf("c1"), dao.all().map { it.id })
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
