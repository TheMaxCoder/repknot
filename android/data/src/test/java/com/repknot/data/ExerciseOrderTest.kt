package com.repknot.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ExerciseOrderTest {
    private val rows = listOf("a", "b", "c").mapIndexed { i, id ->
        SessionExerciseRow(id, "session", i, id, id, plannedSets = 3, notes = "note")
    }
    private val extra = SessionExerciseRow("d", "session", 99, "d", "d")

    private fun check(rows: List<SessionExerciseRow>, vararg ids: String) {
        assertEquals(ids.toList(), rows.map { it.id })
        assertEquals(rows.indices.toList(), rows.map { it.position })
    }

    @Test fun moves() {
        check(moveTo(rows, "a", 2), "b", "c", "a")
        check(moveTo(rows, "c", 0), "c", "a", "b")
        assertEquals(rows, moveTo(rows, "b", 1))
        check(moveTo(rows, "a", 99), "b", "c", "a")
        check(moveTo(rows, "missing", 0), "a", "b", "c")
        assertEquals(rows[0].copy(position = 2), moveTo(rows, "a", 2).last())
    }

    @Test fun insertsAndRemoves() {
        check(insertAt(rows, extra, 0), "d", "a", "b", "c")
        check(insertAt(rows, extra, 1), "a", "d", "b", "c")
        check(insertAt(rows, extra, 99), "a", "b", "c", "d")
        check(remove(rows, "b"), "a", "c")
        check(remove(rows, "missing"), "a", "b", "c")
    }

    @Test fun replacementRetainsOriginalAndClearsOnReturn() {
        val changed = replace(rows, "b", "x", "X")
        check(changed, "a", "b", "c")
        assertEquals("b", changed[1].substitutedFor)
        assertEquals(rows[1].plannedSets, changed[1].plannedSets)
        val replacedAgain = replace(changed, "b", "y", "Y")
        assertEquals("b", replacedAgain[1].substitutedFor)
        assertNull(replace(replacedAgain, "b", "b", "b")[1].substitutedFor)
        assertEquals(rows, replace(rows, "missing", "x", "X"))
    }
}
