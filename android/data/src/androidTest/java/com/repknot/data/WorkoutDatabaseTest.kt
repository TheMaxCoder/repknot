package com.repknot.data

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.repknot.domain.LoadUnit
import com.repknot.domain.SetValues
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
    private lateinit var repo: RoomSetRepository

    @Before fun openInMemory() {
        db = Room.inMemoryDatabaseBuilder(context, WorkoutDatabase::class.java).build()
        repo = RoomSetRepository(db.sets())
    }

    @After fun close() = db.close()

    @Test fun emptyDatabaseDoesNotLogSuggestionsAndUpdatesStayPending() = runBlocking {
        assertNull(repo.observeSet("set").first())
        repo.save("set", "session", SetValues(20.5, LoadUnit.KG, 8, 2))
        repo.save("set", "session", SetValues(45.0, LoadUnit.LB, 10, null))
        val saved = repo.observeSet("set").first()!!
        assertEquals(SetValues(45.0, LoadUnit.LB, 10, null), saved.values)
        assertTrue(saved.pendingSync)
        assertEquals("set", saved.id)
        assertEquals("session", saved.sessionId)
    }

    @Test fun eachSaveAdvancesTheLocalRevision() = runBlocking {
        repo.save("set", "session", SetValues(20.0, LoadUnit.KG, 8, null))
        assertEquals(1L, db.sets().get("set")!!.localRevision)
        repo.save("set", "session", SetValues(22.5, LoadUnit.KG, 8, null))
        assertEquals(2L, db.sets().get("set")!!.localRevision)
    }

    @Test fun lateAcknowledgementDoesNotClearNewerEdit() = runBlocking {
        repo.save("set", "session", SetValues(20.0, LoadUnit.KG, 8, null)) // revision 1 uploading
        repo.save("set", "session", SetValues(22.5, LoadUnit.KG, 8, null)) // revision 2 edited meanwhile
        db.sets().markSynced("set", 1)
        assertTrue(repo.observeSet("set").first()!!.pendingSync)

        db.sets().markSynced("set", 2)
        assertFalse(repo.observeSet("set").first()!!.pendingSync)

        db.sets().markSynced("set", 1) // a stale duplicate acknowledgement never moves backwards
        assertEquals(2L, db.sets().get("set")!!.syncedRevision)
    }

    @Test fun recordedValuesSurviveDatabaseReopen() = runBlocking {
        val name = "persistence-test.db"
        context.deleteDatabase(name)
        val values = SetValues(32.5, LoadUnit.KG, 7, 3)
        var fileDb = Room.databaseBuilder(context, WorkoutDatabase::class.java, name).build()
        try {
            RoomSetRepository(fileDb.sets()).save("set", "session", values)
            fileDb.close()
            fileDb = Room.databaseBuilder(context, WorkoutDatabase::class.java, name).build()
            val saved = RoomSetRepository(fileDb.sets()).observeSet("set").first()!!
            assertEquals(values, saved.values)
            assertTrue(saved.pendingSync)
        } finally {
            fileDb.close()
            context.deleteDatabase(name)
        }
    }
}
