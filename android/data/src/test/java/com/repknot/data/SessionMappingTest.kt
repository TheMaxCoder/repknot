package com.repknot.data

import com.repknot.domain.LoadUnit
import com.repknot.domain.Prefill
import com.repknot.domain.Prescription
import com.repknot.domain.Program
import com.repknot.domain.ProgramWorkout
import com.repknot.domain.prefill
import com.repknot.domain.todaysWorkout
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.encodeToJsonElement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test
import java.time.LocalDate

class SessionMappingTest {
    /** Shaped like `session_snapshot` / the aliased select: extra columns, unordered positions. */
    private val snapshotJson = """
        {"id":"s1","number":7,"performed_on":"2026-11-02","title":"Push 1","notes":null,"revision":4,
         "client_revision":2,"program_id":"p1","program_workout_key":"push-1","user_id":"u","source":"android",
         "exercises":[
           {"id":"x2","position":1,"exercise_id":"pull-up-id","exercise_name":"Pull-up","session_id":"s1",
            "planned_sets":2,"planned_unit":"bodyweight","substituted_for":null,"notes":null,
            "sets":[{"id":"b","position":0,"load":null,"unit":"bodyweight","reps":5.5,"duration_s":null,"rir":2,"raw":null}]},
           {"id":"x1","position":0,"exercise_id":"press-id","exercise_name":"DB press",
            "planned_sets":3,"planned_rep_min":8,"planned_rep_max":12,"planned_rir_min":1,"planned_rir_max":2,
            "planned_load":25,"planned_unit":"kg","substituted_for":"deck-id","notes":"felt good",
            "sets":[{"id":"a2","position":5,"load":25,"unit":"kg","reps":8,"rir":1},
                    {"id":"a1","position":2,"load":25,"unit":"kg","reps":10,"rir":2,"notes":"easy"}]}]}
    """.trimIndent()
    private val remote = codec.decodeFromString<ServerSession>(snapshotJson)

    @Test fun serverSnapshotBecomesOrderedRowsKeepingIdsAndParents() {
        assertEquals(listOf(
            SessionExerciseRow("x1", "s1", 0, "press-id", "DB press", 3, 8, 12, 1, 2, 25.0, "kg", "deck-id", "felt good"),
            SessionExerciseRow("x2", "s1", 1, "pull-up-id", "Pull-up", plannedSets = 2, plannedUnit = "bodyweight"),
        ), remote.exerciseRows())
        assertEquals(listOf(
            SetRow("b", "s1", "x2", 0, null, "bodyweight", 5.5, null, 2, null),
            SetRow("a1", "s1", "x1", 0, 25.0, "kg", 10.0, null, 2, "easy"),
            SetRow("a2", "s1", "x1", 1, 25.0, "kg", 8.0, null, 1, null),
        ), remote.setRows())
    }

    @Test fun pushPayloadRoundTripsTheAdoptedSnapshotInOrder() {
        val row = SessionRow("s1", "2026-11-02", "Push 1", "note", "finished", 3, 2, 4, null, 7, null, "p1", "push-1")
        val payload = SessionSnapshot(row, remote.exerciseRows().reversed(), remote.setRows().reversed()).pushPayload()
        assertEquals(listOf("x1", "x2"), payload.exercises.map { it.id })
        assertEquals("press-id", payload.exercises[0].exercise)
        assertEquals("deck-id", payload.exercises[0].substitutedFor)
        assertEquals(listOf("a1", "a2"), payload.exercises[0].sets.map { it.id })
        assertEquals(listOf("b"), payload.exercises[1].sets.map { it.id })
        val json = codec.encodeToJsonElement(payload).jsonObject
        assertEquals("p1", json["program_id"]!!.jsonPrimitive.content)
        assertEquals("false", json["light"]!!.jsonPrimitive.content)
        assertEquals("true", codec.encodeToJsonElement(SessionSnapshot(row.copy(light = true), emptyList(), emptyList()).pushPayload()).jsonObject["light"]!!.jsonPrimitive.content)
        val firstExercise = json["exercises"]!!.jsonArray[0].jsonObject
        assertEquals("press-id", firstExercise["exercise"]!!.jsonPrimitive.content)
        assertEquals("10.0", firstExercise["sets"]!!.jsonArray[0].jsonObject["reps"]!!.jsonPrimitive.content)
    }

    private val press = Prescription(1, "press-id", "DB press", 3, 8, 12, 1, 2, 25.0, LoadUnit.KG)
    private val pullUp = Prescription(0, "pull-up-id", "Pull-up", 2, null, null, 2, 2, null, LoadUnit.BODYWEIGHT)
    private val push = ProgramWorkout("push-1", "Push 1", 0, listOf(press, pullUp))
    private val pull = ProgramWorkout("pull-1", "Pull 1", 1, emptyList())
    private val program = Program("p1", listOf(push, pull))

    @Test fun startingSnapshotsThePrescriptionInProgramOrder() {
        var next = 0
        val (row, exercises) = startRows("s9", LocalDate.of(2026, 11, 3), program, push) { "id${next++}" }
        assertEquals(SessionRow("s9", "2026-11-03", "Push 1", null, "in_progress", 0, 0, null, null, null, null, "p1", "push-1"), row)
        assertEquals(listOf(
            SessionExerciseRow("id0", "s9", 0, "pull-up-id", "Pull-up", 2, null, null, 2, 2, null, "bodyweight"),
            SessionExerciseRow("id1", "s9", 1, "press-id", "DB press", 3, 8, 12, 1, 2, 25.0, "kg"),
        ), exercises)
        assertThrows(IllegalArgumentException::class.java) {
            startRows("s9", LocalDate.of(2026, 11, 3), program, ProgramWorkout("legs-9", "Legs", 2, emptyList())) { "x" }
        }
    }

    private fun session(id: String, date: String, number: Long?, key: String?, status: String = "pushed") =
        SessionRow(id, date, "t", null, status, 1, 1, null, null, number, null, key?.let { "p1" }, key)

    @Test fun historyOrdersByDateThenNumberAndDrivesDomainRules() {
        val rows = listOf(
            session("late-same-day", "2026-11-03", 12, "pull-1"),
            session("early-same-day", "2026-11-03", 11, "push-1"),
            session("unpushed", "2026-11-03", null, "push-1", status = "finished"),
            session("draft", "2026-11-04", null, "pull-1", status = "in_progress"),
            session("older", "2026-11-01", 3, null),
        )
        val history = pastSessions(rows, emptyList(), emptyList())
        assertEquals(listOf("older", "early-same-day", "late-same-day", "unpushed", "draft"), history.map { it.id })
        assertNull(history.last().finishedAtMillis)
        assertEquals(listOf(true), pastSessions(listOf(rows.first().copy(light = true)), emptyList(), emptyList()).map { it.light })
        // Unpushed same-day session counts as latest; the in-progress draft does not.
        assertEquals("pull-1", todaysWorkout(program, history).key)
        assertEquals("push-1", todaysWorkout(program, history.filter { it.id != "unpushed" }).key)
    }

    @Test fun historySkipsUnitlessSetsAndPrefillsFromSameUnit() {
        val rows = listOf(session("s1", "2026-11-02", 7, "push-1"))
        val exercises = listOf(SessionExerciseRow("x1", "s1", 0, "press-id", "DB press", plannedSets = 3))
        val sets = listOf(
            SetRow("a1", "s1", "x1", 0, 25.0, "kg", 10.0),
            SetRow("raw", "s1", "x1", 1, null, null, null),
        )
        val history = pastSessions(rows, exercises, sets)
        val exercise = history.single().exercises.single()
        assertEquals("DB press", exercise.exerciseName)
        assertEquals(3, exercise.plannedSets)
        val kept = exercise.sets.single()
        assertEquals(25.0, kept.load!!, 0.0); assertEquals(LoadUnit.KG, kept.unit)
        val result = prefill(press, history) as Prefill.Previous
        assertEquals("s1", result.sessionId)
        assertEquals(10.0, result.sets.single().reps!!, 0.0)
    }
}
