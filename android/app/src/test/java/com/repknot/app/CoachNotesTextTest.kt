package com.repknot.app

import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CoachNotesTextTest {
    @Test fun dates() {
        val today = LocalDate.of(2026, 11, 12)
        assertEquals("12 Nov", coachNoteDate("2026-11-12T12:00:00Z", ZoneId.of("UTC"), today))
        assertEquals("12 Nov 2025", coachNoteDate("2025-11-12T12:00:00Z", ZoneId.of("UTC"), today))
        assertEquals("12 Nov", coachNoteDate("2026-11-11T23:30:00Z", ZoneId.of("Europe/Paris"), today))
        assertNull(coachNoteDate("invalid", ZoneId.of("UTC"), today))
    }
    @Test fun labels() {
        assertEquals(listOf("Go up", "Hold", "Technique", "Recovery", "Focus", "Focus"),
            listOf("progress", "hold", "technique", "recovery", "focus", "other").map(::kindLabel))
    }
}
