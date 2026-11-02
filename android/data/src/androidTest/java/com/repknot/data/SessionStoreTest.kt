package com.repknot.data

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class SessionStoreTest {
    private lateinit var db: WorkoutDatabase
    private val id = "00000000-0000-4000-a000-000000000001"
    private val setId = "00000000-0000-4000-a000-000000000002"
    @Before fun open() { db = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext, WorkoutDatabase::class.java).build() }
    @After fun close() = db.close()
    private fun exercise(id: String, name: String, vararg sets: ServerSet, position: Int = 0) =
        ServerSessionExercise(id, position, "$name-id", name, sets = sets.toList())
    private fun remote(revision: Long, client: Long? = null) = ServerSession(id, 42, "2026-10-31", "Push", "session note", revision, client,
        exercises = listOf(exercise("ex-remote", "Bench", ServerSet("00000000-0000-4000-a000-000000000003", 0, 32.5, "kg", 7.0, rir = 3, notes = "set note"))))
    private suspend fun edit(load: Double) = db.logSet(id, db.sessions().sets(id).firstOrNull()?.id ?: setId, load)

    @Test fun resumeReopensOnlyAFinishedWorkoutThatWasNeverUploaded() = runBlocking {
        edit(20.0); db.sessions().finishAt(id, 1000L)
        assertEquals(1, db.sessions().resume(id))
        val row = db.sessions().get(id)!!
        assertEquals("in_progress", row.status); assertNull(row.finishedAt)
        assertEquals(0, db.sessions().pending().size)
        assertTrue(db.sessions().discard(id))
        // Uploaded once (server number + base revision): it cannot be reopened.
        edit(20.0); db.sessions().finish(id); db.sessions().acknowledge(id, 1, 5, 42)
        assertEquals(0, db.sessions().resume(id)); assertEquals("pushed", db.sessions().get(id)!!.status)
    }
    @Test fun acknowledgementDoesNotClearAnEditMadeDuringUpload() = runBlocking {
        edit(20.0); db.sessions().finish(id)
        val uploading = db.sessions().snapshot(id)!!.row.localRevision
        edit(22.5)
        db.sessions().acknowledge(id, uploading, 5, 42)
        val row = db.sessions().get(id)!!
        assertEquals(2L, row.localRevision); assertEquals(1L, row.pushedLocalRevision)
        assertEquals("finished", row.status); assertEquals(1, db.sessions().pending().size)
    }
    @Test fun refreshPreservesDraftAndRefreshesPushedWithoutResettingRevision() = runBlocking {
        edit(20.0); db.sessions().replaceCache(listOf(remote(5, 9)))
        assertEquals(20.0, db.sessions().sets(id).first().load!!, 0.0)
        db.sessions().finish(id); db.sessions().acknowledge(id, 1, 5, 42)
        db.sessions().replaceCache(listOf(remote(7, 9)))
        assertEquals(9L, db.sessions().get(id)!!.localRevision)
        assertEquals(32.5, db.sessions().sets(id).first().load!!, 0.0)
        assertEquals("set note", db.sessions().sets(id).first().notes)
        edit(35.0); assertEquals(10L, db.sessions().get(id)!!.localRevision)
    }
    @Test fun sharedUnchangedIsAdoptedButAnEditAfterSharingConflicts() = runBlocking {
        edit(20.0); db.sessions().shareSnapshot(id); db.sessions().finish(id)
        db.sessions().replaceCache(listOf(remote(2)))
        assertEquals("pushed", db.sessions().get(id)!!.status)
        assertEquals(32.5, db.sessions().sets(id).first().load!!, 0.0)
        db.sessions().put(draft(id)); edit(20.0); db.sessions().shareSnapshot(id); edit(25.0); db.sessions().finish(id)
        db.sessions().replaceCache(listOf(remote(2)))
        assertEquals("conflict", db.sessions().get(id)!!.status)
        assertEquals(25.0, db.sessions().sets(id).first().load!!, 0.0)
    }
    @Test fun keepMineUsesANewRevisionEvenAfterLostAcknowledgement() = runBlocking {
        edit(20.0); db.sessions().finish(id)
        db.sessions().conflict(id, remote(8, 1).json())
        db.sessions().keepMine(id, 8, 1)
        val row = db.sessions().get(id)!!
        assertEquals(2L, row.localRevision)
        assertEquals(8L, row.baseRevision)
        assertEquals("finished", row.status)
    }
    @Test fun adoptionKeepsServerIdsWithTheirContentsAfterReordering() = runBlocking {
        val first = ServerSet("first", 0, 20.0, "kg", 8.0)
        val second = ServerSet("second", 1, 40.0, "kg", 10.0)
        val bench = exercise("bench", "Bench", first, second)
        val row = exercise("row", "Row", position = 1)
        db.sessions().adopt(remote(2).copy(exercises = listOf(bench, row)), 0)
        db.sessions().adopt(remote(3).copy(exercises = listOf(row.copy(position = 0),
            bench.copy(position = 1, sets = listOf(second.copy(position = 0), first.copy(position = 1))))), 0)
        assertEquals(listOf("row", "bench"), db.sessions().exercises(id).map { it.id })
        val sets = db.sessions().sets(id)
        assertEquals(listOf("second", "first"), sets.map { it.id })
        assertEquals(listOf(40.0, 20.0), sets.map { it.load })
        assertTrue(sets.all { it.sessionExerciseId == "bench" })
        db.sessions().adopt(remote(4).copy(exercises = listOf(bench.copy(sets = listOf(first)))), 0)
        assertEquals(listOf("bench"), db.sessions().exercises(id).map { it.id })
        assertEquals(listOf("first"), db.sessions().sets(id).map { it.id })
    }
}
