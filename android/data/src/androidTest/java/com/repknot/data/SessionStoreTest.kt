package com.repknot.data

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.repknot.domain.LoadUnit
import com.repknot.domain.SetValues
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
    private fun remote(revision: Long, client: Long? = null) = ServerSession(id, 42, "2026-10-31", "Push", "session note", revision, client,
        listOf(ServerSet("00000000-0000-4000-a000-000000000003", "Bench", 32.5, "kg", 7, 3, "set note")))
    private suspend fun edit(load: Double) = db.sets().save(setId, id, SetValues(load, LoadUnit.KG, 7, 3))

    @Test fun acknowledgementDoesNotClearAnEditMadeDuringUpload() = runBlocking {
        edit(20.0); db.sessions().finish(id)
        val uploading = db.sessions().snapshot(id)!!.first.localRevision
        edit(22.5)
        db.sessions().acknowledge(id, uploading, 5, 42)
        val row = db.sessions().get(id)!!
        assertEquals(2L, row.localRevision); assertEquals(1L, row.pushedLocalRevision)
        assertEquals("finished", row.status); assertEquals(1, db.sessions().pending().size)
    }
    @Test fun refreshPreservesDraftAndRefreshesPushedWithoutResettingRevision() = runBlocking {
        edit(20.0); db.sessions().replaceCache(listOf(remote(5, 9)))
        assertEquals(20.0, db.sets().get(setId)!!.load, 0.0)
        db.sessions().finish(id); db.sessions().acknowledge(id, 1, 5, 42)
        db.sessions().replaceCache(listOf(remote(7, 9)))
        assertEquals(9L, db.sessions().get(id)!!.localRevision)
        assertEquals(32.5, db.sets().get(setId)!!.load, 0.0)
        assertEquals("set note", db.sets().get(setId)!!.notes)
        edit(35.0); assertEquals(10L, db.sessions().get(id)!!.localRevision)
    }
    @Test fun sharedUnchangedIsAdoptedButAnEditAfterSharingConflicts() = runBlocking {
        edit(20.0); db.sessions().shareSnapshot(id); db.sessions().finish(id)
        db.sessions().replaceCache(listOf(remote(2)))
        assertEquals("pushed", db.sessions().get(id)!!.status)
        assertEquals(32.5, db.sets().get(setId)!!.load, 0.0)
        db.sessions().put(newSession(id)); edit(20.0); db.sessions().shareSnapshot(id); edit(25.0); db.sessions().finish(id)
        db.sessions().replaceCache(listOf(remote(2)))
        assertEquals("conflict", db.sessions().get(id)!!.status)
        assertEquals(25.0, db.sets().get(setId)!!.load, 0.0)
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
}
