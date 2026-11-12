package com.repknot.app

import com.repknot.domain.CoachNoteItem
import org.junit.Assert.assertEquals
import org.junit.Test

class CoachNotesStateTest {
    @Test fun activeItemsMatchExerciseAndExcludeDoneInOrder() {
        val items = listOf(CoachNoteItem("focus", "a", "x", 0), CoachNoteItem("hold", "b", "x", 2),
            CoachNoteItem("focus", "c", "y", 3), CoachNoteItem("focus", "d", "x", 4))
        val state = CoachNotesUiState(CoachNoteUi("n", "", null, "", items), done = setOf(2))
        assertEquals(listOf(items[0], items[3]), state.activeItemsFor("x"))
        assertEquals(emptyList<CoachNoteItem>(), CoachNotesUiState().activeItemsFor("x"))
    }

    @Test fun doneKeysPruneOtherBriefsAndEncodeIndex() {
        val keys = setOf("old:0", "new:1", "new:3")
        assertEquals(setOf("new:1", "new:3", "new:5"), updatedDoneCoachItems(keys, "new", 5, true))
        assertEquals(setOf("new:3"), updatedDoneCoachItems(keys, "new", 1, false))
    }
}
