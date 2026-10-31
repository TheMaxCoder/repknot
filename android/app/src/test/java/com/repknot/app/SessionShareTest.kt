package com.repknot.app

import com.repknot.data.SessionRow
import com.repknot.data.SetRow
import org.junit.Assert.assertEquals
import org.junit.Test

class SessionShareTest {
    @Test fun sharedTextContainsReferenceNumbersAndNotes() {
        val session = SessionRow("ref-123", "2026-10-31", "Push A", "short on time", "finished", 1, 0, null, null, 42, null)
        val set = SetRow("set", "ref-123", 32.5, "KG", 7, 3, "Dumbbell bench press", "grip slipping")
        assertEquals("Workout #42 · Sat 2026-10-31 · Push A\nref: ref-123\nDumbbell bench press\n  1. 32.5 kg × 7 · RIR 3 — grip slipping\nSession notes: short on time", formatSession(session, listOf(set)))
    }
}
