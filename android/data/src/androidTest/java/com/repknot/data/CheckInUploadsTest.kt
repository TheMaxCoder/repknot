package com.repknot.data

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Delete and upload of check-ins never interleave (weekly review 2026-11-18, task 040 item 1). */
class CheckInUploadsTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var db: WorkoutDatabase
    @Before fun openInMemory() { db = Room.inMemoryDatabaseBuilder(context, WorkoutDatabase::class.java).build() }
    @After fun close() = db.close()

    @Test fun aRowDeletedWhileAnotherUploadsIsNotSent() = runBlocking {
        val dao = db.checkIns()
        dao.add(CheckInRow("c1", "2026-11-01", waistCm = 90.0)); dao.add(CheckInRow("c2", "2026-11-02", waistCm = 89.5))
        val uploads = CheckInUploads(dao)
        val sent = mutableListOf<String>(); val remoteDeleted = mutableListOf<String>()
        val firstStarted = CompletableDeferred<Unit>(); val release = CompletableDeferred<Unit>()
        withTimeout(10_000) {
            // Sync read both rows as pending, then pauses inside c1's upload.
            val push = async { uploads.push { row -> sent += row.id; if (row.id == "c1") { firstStarted.complete(Unit); release.await() } } }
            firstStarted.await()
            val delete = async { uploads.delete("c2") { remoteDeleted += it } }
            repeat(5) { yield() }
            assertFalse("delete waits for the upload in flight", delete.isCompleted)
            release.complete(Unit)
            assertTrue(delete.await())
            assertNull(push.await())
        }
        assertEquals(listOf("c1"), sent)
        assertEquals(listOf("c2"), remoteDeleted)
        assertEquals(listOf("c1"), dao.all().map { it.id })
        assertFalse(dao.get("c1")!!.pending)
    }

    @Test fun deletingARowWhileItUploadsLeavesItUploaded() = runBlocking {
        val dao = db.checkIns()
        dao.add(CheckInRow("c1", "2026-11-01", waistCm = 90.0))
        val uploads = CheckInUploads(dao)
        val started = CompletableDeferred<Unit>(); val release = CompletableDeferred<Unit>()
        val remoteDeleted = mutableListOf<String>()
        withTimeout(10_000) {
            val push = async { uploads.push { started.complete(Unit); release.await() } }
            started.await()
            val delete = async { uploads.delete("c1") { remoteDeleted += it } }
            repeat(5) { yield() }
            release.complete(Unit)
            // The upload won: the row stays, shown as uploaded, instead of vanishing locally only.
            assertFalse(delete.await())
            assertNull(push.await())
        }
        assertFalse(dao.get("c1")!!.pending)
        assertTrue(remoteDeleted.isEmpty())
    }

    @Test fun aFailedUploadKeepsTheRowPendingAndDeletable() = runBlocking {
        val dao = db.checkIns()
        dao.add(CheckInRow("c1", "2026-11-01", waistCm = 90.0))
        val uploads = CheckInUploads(dao)
        val failure = uploads.push { error("offline") }
        assertEquals("offline", failure?.message)
        assertTrue(dao.get("c1")!!.pending)
        // A remote delete that fails (still offline) does not undo the local delete.
        assertTrue(uploads.delete("c1") { error("offline") })
        assertNull(dao.get("c1"))
    }
}
