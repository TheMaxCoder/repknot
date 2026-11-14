package com.repknot.data

import com.repknot.domain.LoadUnit
import com.repknot.domain.Prescription
import com.repknot.domain.Program
import com.repknot.domain.ProgramWorkout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class ReferenceMappingTest {
    private val json = """
        [{"id":"p1","key":"ppl","name":"Program","version":2,"starts_on":"2026-11-02",
          "rules":"rule","is_active":true,"extra":1,"program_workouts":[
          {"id":"w1","key":"push","name":"Push","sequence_position":3,"default_weekday":1,
           "ignored":true,"program_exercises":[
           {"id":"r1","position":2,"exercise_id":"e1","sets":3,"rep_min":6,"rep_max":8,
            "rir_min":1,"rir_max":2,"rest_s_min":60,"rest_s_max":90,"load":22.5,
            "unit":"kg","notes":"note","ignored":"x"},
           {"id":"r2","position":0,"exercise_id":"e2","sets":2,"load":null,"unit":"bodyweight"}]},
          {"id":"w2","key":"pull","name":"Pull","sequence_position":1,"default_weekday":null,
           "program_exercises":[]}]},
         {"id":"p2","key":"other","name":"Other","version":1,"starts_on":"2026-01-01",
          "rules":null,"is_active":false,"program_workouts":[
          {"id":"w3","key":"other","name":"Other","sequence_position":0,
           "program_exercises":[{"id":"r3","position":0,"exercise_id":"missing",
           "sets":1,"unit":"lb"}]}]}]
    """.trimIndent()
    private val exercisesJson = """
        [{"id":"e1","slug":"bench","name":"Bench","default_unit":"kg","archived_at":null,"notes":"ignored"},
         {"id":"e2","slug":"pullup","name":"Pull-up","archived_at":"2026-01-01T00:00:00Z"}]
    """.trimIndent()
    private fun snapshot() = ReferenceSnapshot(
        referenceCodec.decodeFromString(exercisesJson), referenceCodec.decodeFromString(json),
    )

    @Test fun `decode and flatten all fields and parent ids`() {
        val snapshot = snapshot()
        val rows = snapshot.toRows()
        assertEquals(listOf(
            ExerciseRow("e1", "bench", "Bench", "kg", false),
            ExerciseRow("e2", "pullup", "Pull-up", null, true),
        ), rows.exercises)
        assertEquals(listOf(
            ProgramRow("p1", "ppl", "Program", 2, "2026-11-02", "rule", true),
            ProgramRow("p2", "other", "Other", 1, "2026-01-01", null, false),
        ), rows.programs)
        assertEquals(listOf(
            ProgramWorkoutRow("w1", "p1", "push", "Push", 3, 1),
            ProgramWorkoutRow("w2", "p1", "pull", "Pull", 1, null),
            ProgramWorkoutRow("w3", "p2", "other", "Other", 0, null),
        ), rows.workouts)
        assertEquals(listOf(
            ProgramExerciseRow("r1", "w1", 2, "e1", 3, 6, 8, 1, 2, 60, 90, 22.5, "kg", "note"),
            ProgramExerciseRow("r2", "w1", 0, "e2", 2, null, null, null, null, null, null, null, "bodyweight", null),
            ProgramExerciseRow("r3", "w3", 0, "missing", 1, null, null, null, null, null, null, null, "lb", null),
        ), rows.prescriptions)
        assertFalse(rows.exercises[0].archived)
        assertTrue(rows.exercises[1].archived)
        assertEquals(null, snapshot.programs[0].workouts[0].exercises[1].repMin)
    }

    @Test fun `unit parsing is case insensitive and rejects other values`() {
        assertEquals(LoadUnit.KG, parseUnit("kG"))
        assertEquals(LoadUnit.LB, parseUnit("Lb"))
        assertEquals(LoadUnit.PLATES, parseUnit("PLATES"))
        assertEquals(LoadUnit.BODYWEIGHT, parseUnit("BodyWeight"))
        try { parseUnit("stone"); fail("expected IllegalArgumentException") }
        catch (_: IllegalArgumentException) { }
    }

    @Test fun `domain filters parents sorts prescriptions and copies values`() {
        val rows = snapshot().toRows()
        assertEquals(Program("p1", listOf(
            ProgramWorkout("push", "Push", 3, listOf(
                Prescription(0, "e2", "Pull-up", 2, null, null, null, null, null, LoadUnit.BODYWEIGHT),
                Prescription(2, "e1", "Bench", 3, 6, 8, 1, 2, 22.5, LoadUnit.KG),
            )),
            ProgramWorkout("pull", "Pull", 1, emptyList()),
        )), toDomainProgram(rows.programs[0], rows.workouts, rows.prescriptions, rows.exercises))
    }

    @Test fun `domain program always includes its own id among version ids`() {
        val rows = snapshot().toRows()
        assertEquals(setOf("p1"), toDomainProgram(rows.programs[0], rows.workouts, rows.prescriptions, rows.exercises).versionIds)
        assertEquals(setOf("p0", "p1"), toDomainProgram(rows.programs[0], rows.workouts, rows.prescriptions,
            rows.exercises, listOf("p0")).versionIds)
    }

    @Test fun `missing exercise names id in exception`() {
        val rows = snapshot().toRows()
        try {
            toDomainProgram(rows.programs[1], rows.workouts, rows.prescriptions, rows.exercises)
            fail("expected IllegalStateException")
        } catch (e: IllegalStateException) {
            assertTrue(e.message.orEmpty().contains("missing"))
        }
    }
}
