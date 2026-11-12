package com.repknot.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class CoachNotesTest {
    @Test fun groupsPreserveOrderAndMatchExerciseIds() {
        val a = CoachNoteItem("focus", "a", "one", 0)
        val b = CoachNoteItem("hold", "b", null, 1)
        val c = CoachNoteItem("focus", "c", "two", 2)
        val d = CoachNoteItem("progress", "d", "one", 3)
        assertEquals(CoachNoteGroups(emptyList(), emptyList(), emptyList(), emptyList()), groupCoachNotes(emptyList(), setOf("one")))
        assertEquals(CoachNoteGroups(listOf(a, d), listOf(b), listOf(c), emptyList()), groupCoachNotes(listOf(a, b, c, d), setOf("one")))
        assertEquals(CoachNoteGroups(listOf(d), emptyList(), listOf(c), listOf(a, b)), groupCoachNotes(listOf(a, b, c, d), setOf("one"), setOf(0, 1)))
    }
}
