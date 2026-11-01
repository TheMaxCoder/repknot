package com.repknot.app

import com.repknot.data.SessionExerciseRow
import com.repknot.data.SessionRow
import com.repknot.data.SessionSnapshot
import com.repknot.data.SetRow
import org.junit.Assert.assertEquals
import org.junit.Test

class SessionShareTest {
    private val session = SessionRow("ref-123", "2026-10-31", "Push 1", "short on time", "finished", 1, 0, null, null, 42, null)

    @Test fun sharedTextGroupsSetsByExerciseInOrderWithNotes() {
        val exercises = listOf(
            SessionExerciseRow("e2", "ref-123", 1, "x2", "Pull-up", notes = "grip slipping"),
            SessionExerciseRow("e1", "ref-123", 0, "x1", "Low-incline DB press"),
        )
        val sets = listOf(
            SetRow("s2", "ref-123", "e1", 1, 25.0, "kg", 8.0, rir = 1),
            SetRow("s1", "ref-123", "e1", 0, 25.0, "kg", 10.0, rir = 2, notes = "grip slipping"),
            SetRow("s3", "ref-123", "e2", 0, null, "bodyweight", 5.5),
        )
        assertEquals("""
            Workout #42 · Sat 2026-10-31 · Push 1
            ref: ref-123
            Low-incline DB press
              1. 25 kg × 10 · RIR 2 — grip slipping
              2. 25 kg × 8 · RIR 1
            Pull-up — grip slipping
              1. bodyweight × 5.5
            Session notes: short on time
        """.trimIndent(), formatSession(SessionSnapshot(session, exercises, sets)))
    }

    @Test fun setsShowPlatesHalfLoadsAndDurations() {
        assertEquals("9p × 12", formatSet(SetRow("s", "r", "e", 0, 9.0, "plates", 12.0)))
        assertEquals("22.5 lb × 8", formatSet(SetRow("s", "r", "e", 0, 22.5, "lb", 8.0)))
        assertEquals("24 kg × 60 s", formatSet(SetRow("s", "r", "e", 0, 24.0, "kg", null, durationS = 60)))
    }
}
