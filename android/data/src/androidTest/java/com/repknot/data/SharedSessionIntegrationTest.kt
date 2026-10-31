package com.repknot.data

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.repknot.domain.LoadUnit
import com.repknot.domain.SetValues
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.util.UUID

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
        val ids = mutableListOf<String>()
        try {
            for (edited in listOf(false, true)) {
                val id = UUID.randomUUID().toString(); val setId = UUID.randomUUID().toString(); ids += id
                db.sets().save(setId, id, SetValues(32.5, LoadUnit.KG, 7, 3))
                db.sessions().shareSnapshot(id)
                if (edited) db.sets().save(setId, id, SetValues(35.0, LoadUnit.KG, 7, 3))
                db.sessions().finish(id)
                val params = buildJsonObject {
                    putJsonObject("payload") {
                        put("ref", id); put("performed_on", "2026-10-31"); put("title", "Shared integration test")
                        put("notes", "server note")
                        putJsonArray("sets") { add(buildJsonObject {
                            put("exercise_name", "Bench"); put("load", 32.5); put("unit", "kg"); put("reps", 7); put("rir", 3); put("notes", "set note")
                        }) }
                    }
                }
                client.postgrest.rpc("save_session", params)
                SessionSync(cloud, db.sessions()).refreshAndPush()
                val local = db.sessions().get(id)!!
                assertEquals(if (edited) "conflict" else "pushed", local.status)
                if (edited) {
                    assertEquals(35.0, db.sets().get(setId)!!.load, 0.0)
                    assertNotNull(local.serverSnapshot)
                } else {
                    assertEquals("server note", local.notes)
                    assertEquals("set note", db.sets().get(setId)!!.notes)
                }
                val remote = client.from("workout_sessions").select { filter { eq("id", id) } }.decodeList<ServerSession>()
                assertEquals(1, remote.size)
            }
        } finally {
            for (id in ids) client.from("workout_sessions").delete { filter { eq("id", id) } }
            db.close()
            cloud.signOut()
        }
    }
}
