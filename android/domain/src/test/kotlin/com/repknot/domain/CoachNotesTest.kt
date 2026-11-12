package com.repknot.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class CoachNotesTest {
    @Test fun groupsPreserveOrderAndMatchExerciseIds() {
        val a = CoachNoteItem("focus", "a", "one")
        val b = CoachNoteItem("hold", "b", null)
        val c = CoachNoteItem("focus", "c", "two")
        val d = CoachNoteItem("progress", "d", "one")
        assertEquals(CoachNoteGroups(emptyList(), emptyList(), emptyList()), groupCoachNotes(emptyList(), setOf("one")))
        assertEquals(CoachNoteGroups(listOf(a, d), listOf(b), listOf(c)), groupCoachNotes(listOf(a, b, c, d), setOf("one")))
    }
}
