package com.repknot.data

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import kotlinx.serialization.Serializable
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.util.UUID

@Serializable private data class ExerciseId(val id: String)

/** Opt-in test with the synthetic test account; credentials are supplied at runtime. */
class SharedSessionIntegrationTest {
    @Test fun sharedSnapshotSavedOnServerIsAdoptedOrConflictsAfterEdit() = runBlocking {
        val args = InstrumentationRegistry.getArguments()
        fun value(name: String): String = args.getString(name)?.let {
            String(android.util.Base64.decode(it, android.util.Base64.NO_WRAP), Charsets.UTF_8)
        }.orEmpty()
        val url = value("supabase_url")
        val key = value("supabase_key")
        val email = value("test_email")
        val password = value("test_password")
        assumeTrue("Synthetic account credentials not supplied", listOf(url, key, email, password).all { it.isNotBlank() })
        val cloud = CloudAccount(url, key)
        cloud.signIn(email, password)
        withTimeout(30000) { cloud.state.first { it is CloudState.SignedIn } }
        val client = cloud.requireClient()
        val db = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext, WorkoutDatabase::class.java).build()
        // Sessions reference the account's own exercises; keep one synthetic exercise around.
        client.from("exercises").upsert(buildJsonObject { put("slug", "integration-bench"); put("name", "Bench (integration)"); put("default_unit", "kg") }) {
            onConflict = "user_id,slug"; ignoreDuplicates = true
        }
        val bench = client.from("exercises").select { filter { eq("slug", "integration-bench") } }.decodeSingle<ExerciseId>().id
        suspend fun log(sessionId: String, setId: String, load: Double) = db.logSet(sessionId, setId, load, exerciseId = bench)
        val ids = mutableListOf<String>()
        try {
            // A finished session that was never marked for upload stays local and never reaches the server.
            val localOnly = UUID.randomUUID().toString(); ids += localOnly
            log(localOnly, UUID.randomUUID().toString(), 30.0); db.sessions().finish(localOnly)
            SessionSync(cloud, db.sessions(), db.reference()).refreshAndPush()
            assertEquals("finished", db.sessions().get(localOnly)!!.status)
            assertEquals(0, client.from("workout_sessions").select { filter { eq("id", localOnly) } }.decodeList<ServerSession>().size)
            db.sessions().takeDeleted(localOnly); db.sessions().put(draft(localOnly).copy(status = "pushed")); db.sessions().deleteCached(localOnly)
            for (edited in listOf(false, true)) {
                val id = UUID.randomUUID().toString(); val setId = UUID.randomUUID().toString(); ids += id
                log(id, setId, 32.5)
                db.sessions().shareSnapshot(id)
                if (edited) log(id, setId, 35.0)
                db.sessions().finish(id); db.sessions().requestUpload(id)
                val params = buildJsonObject {
                    putJsonObject("payload") {
                        put("ref", id); put("performed_on", "2026-10-31"); put("title", "Shared integration test")
                        put("notes", "server note")
                        putJsonArray("exercises") { add(buildJsonObject {
                            put("exercise", "integration-bench")
                            putJsonArray("sets") { add(buildJsonObject {
                                put("load", 32.5); put("unit", "kg"); put("reps", 7); put("rir", 3); put("notes", "set note")
                            }) }
                        }) }
                    }
                }
                client.postgrest.rpc("save_session", params)
                SessionSync(cloud, db.sessions(), db.reference()).refreshAndPush()
                val local = db.sessions().get(id)!!
                assertEquals(if (edited) "conflict" else "pushed", local.status)
                if (edited) {
                    assertEquals(35.0, db.sessions().getSet(setId)!!.load!!, 0.0)
                    assertNotNull(local.serverSnapshot)
                } else {
                    assertEquals("server note", local.notes)
                    assertEquals("set note", db.sessions().sets(id).single().notes)
                }
                val remote = client.from("workout_sessions").select { filter { eq("id", id) } }.decodeList<ServerSession>()
                assertEquals(1, remote.size)
            }
            // A server deletion must not stop unrelated pending uploads, even when
            // a different session's payload is rejected by the server.
            val deletedId = UUID.randomUUID().toString(); val deletedSet = UUID.randomUUID().toString(); ids += deletedId
            log(deletedId, deletedSet, 10.0)
            db.sessions().finish(deletedId); db.sessions().requestUpload(deletedId)
            val sync = SessionSync(cloud, db.sessions(), db.reference())
            sync.refreshAndPush()
            client.from("workout_sessions").delete { filter { eq("id", deletedId) } }
            log(deletedId, deletedSet, 12.0)
            val badId = UUID.randomUUID().toString(); val goodId = UUID.randomUUID().toString()
            val badSet = UUID.randomUUID().toString(); val goodSet = UUID.randomUUID().toString()
            ids += badId; ids += goodId
            log(badId, badSet, 1.0); db.sessions().finish(badId); db.sessions().requestUpload(badId)
            val badRow = db.sessions().sets(badId).single()
            db.sessions().putSets(listOf(badRow.copy(unit = "INVALID")))  // rejected by the server's unit domain
            log(goodId, goodSet, 2.0); db.sessions().finish(goodId); db.sessions().requestUpload(goodId)
            val order = db.sessions().pending().map { it.id }
            assertTrue(order.indexOf(badId) < order.indexOf(goodId))
            assertTrue(runCatching { sync.refreshAndPush() }.isFailure)
            assertEquals("conflict_deleted", db.sessions().get(deletedId)!!.status)
            assertEquals("finished", db.sessions().get(badId)!!.status)
            assertEquals("pushed", db.sessions().get(goodId)!!.status)
            // Keep mine recreates using the same ID; a second deletion can then be
            // accepted, removing both local session and its sets.
            runCatching { sync.keepMine(deletedId) }
            assertEquals("pushed", db.sessions().get(deletedId)!!.status)
            client.from("workout_sessions").delete { filter { eq("id", deletedId) } }
            log(deletedId, deletedSet, 14.0)
            runCatching { sync.refreshAndPush() }
            assertEquals("conflict_deleted", db.sessions().get(deletedId)!!.status)
            sync.takeTheirs(deletedId)
            assertNull(db.sessions().get(deletedId))
            assertEquals(0, db.sessions().sets(deletedId).size)
        } finally {
            for (id in ids) client.from("workout_sessions").delete { filter { eq("id", id) } }
            db.close()
            cloud.signOut()
        }
    }

    @Test fun checkInsDownloadAndUploadOnlyWhenMarked() = runBlocking {
        val args = InstrumentationRegistry.getArguments()
        fun value(name: String): String = args.getString(name)?.let {
            String(android.util.Base64.decode(it, android.util.Base64.NO_WRAP), Charsets.UTF_8)
        }.orEmpty()
        val url = value("supabase_url"); val key = value("supabase_key")
        val email = value("test_email"); val password = value("test_password")
        assumeTrue("Synthetic account credentials not supplied", listOf(url, key, email, password).all { it.isNotBlank() })
        val cloud = CloudAccount(url, key)
        cloud.signIn(email, password)
        withTimeout(30000) { cloud.state.first { it is CloudState.SignedIn } }
        val client = cloud.requireClient()
        val db = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext, WorkoutDatabase::class.java).build()
        val serverId = UUID.randomUUID().toString(); val phoneId = UUID.randomUUID().toString()
        try {
            client.from("checkins").insert(buildJsonObject { put("id", serverId); put("checked_on", "2026-10-30"); put("waist_cm", 90.5); put("source", "cli") })
            db.checkIns().add(CheckInRow(phoneId, "2026-10-31", weightKg = 70.25, waistCm = 90.0))
            // The server row is downloaded; the phone row reaches the server once, even when refreshed again.
            val sync = SessionSync(cloud, db.sessions(), db.reference(), db.checkIns())
            sync.refreshAndPush(); sync.refreshAndPush()
            assertEquals(90.5, db.checkIns().all().single { it.id == serverId }.waistCm!!, 0.0)
            assertEquals(listOf(false), db.checkIns().all().filter { it.id == phoneId }.map { it.pending })
            val remote = client.from("checkins").select { filter { eq("id", phoneId) } }.decodeList<ServerCheckIn>()
            assertEquals(70.25, remote.single().weightKg!!, 0.0)
            // A server deletion removes the cached row on the next refresh.
            client.from("checkins").delete { filter { eq("id", serverId) } }
            sync.refreshAndPush()
            assertNull(db.checkIns().all().firstOrNull { it.id == serverId })
        } finally {
            for (id in listOf(serverId, phoneId)) client.from("checkins").delete { filter { eq("id", id) } }
            db.close()
            cloud.signOut()
        }
    }
}
