package com.repknot.data

import com.repknot.domain.CoachNoteItem
import org.junit.Assert.assertEquals
import org.junit.Test

class CoachNotesStoreTest {
    @Test fun parsesLeniently() {
        val input = """[{"kind":"hold","text":"First","exercise_id":"x","extra":true},
            {"text":" "},{"text":"Second"},{"kind":12,"text":"Third"},{"text":" "},{"kind":"focus"},42,null]"""
        assertEquals(listOf(CoachNoteItem("hold", "First", "x", 0), CoachNoteItem("focus", "Second", null, 2),
            CoachNoteItem("focus", "Third", null, 3)), parseCoachNoteItems(input))
        assertEquals(emptyList<CoachNoteItem>(), parseCoachNoteItems("[broken"))
    }
}
