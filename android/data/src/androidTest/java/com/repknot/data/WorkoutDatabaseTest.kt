package com.repknot.data

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.repknot.domain.LoadUnit
import com.repknot.domain.SetValues
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
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
        assertNull(repo.observeFirstSet("session").first())
        repo.save("set", "session", SetValues(20.5, LoadUnit.KG, 8, 2))
        repo.save("set", "session", SetValues(45.0, LoadUnit.LB, 10, null))
        val saved = repo.observeFirstSet("session").first()!!
        assertEquals(SetValues(45.0, LoadUnit.LB, 10, null), saved.values)
        assertEquals("set", saved.id)
        assertEquals("session", saved.sessionId)
    }

    @Test fun eachSaveAdvancesTheLocalRevision() = runBlocking {
        repo.save("set", "session", SetValues(20.0, LoadUnit.KG, 8, null))
        assertEquals(1L, db.sessions().get("session")!!.localRevision)
        repo.save("set", "session", SetValues(22.5, LoadUnit.KG, 8, null))
        assertEquals(2L, db.sessions().get("session")!!.localRevision)
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
            val saved = RoomSetRepository(fileDb.sets()).observeFirstSet("session").first()!!
            assertEquals(values, saved.values)
            } finally {
            fileDb.close()
            context.deleteDatabase(name)
        }
    }
}
