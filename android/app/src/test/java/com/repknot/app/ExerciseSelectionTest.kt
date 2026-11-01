package com.repknot.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ExerciseSelectionTest {
    @Test fun selectionAndFallbackFollowSessionOrder() {
        val exercises = listOf(ExerciseProgress("a", 2, 2), ExerciseProgress("b", 3, 1), ExerciseProgress("c", null, 0))
        assertEquals("b", selectedExerciseId(null, exercises))
        assertEquals("a", selectedExerciseId("a", exercises))
        assertEquals("c", selectedExerciseId("missing", exercises.reversed()))
        assertEquals("c", selectedExerciseId(null, listOf(ExerciseProgress("a", 1, 1), ExerciseProgress("c", 2, 2))))
        assertEquals("a", selectedExerciseId(null, listOf(ExerciseProgress("a", 0, 4))))
        assertNull(selectedExerciseId("a", emptyList()))
    }

    @Test fun recordedUnitsRemainUnchangedIncludingUnknown() {
        assertNull(recordedUnit(null))
        assertEquals("plates", recordedUnit("plates"))
        assertEquals("kg", recordedUnit("kg"))
    }
}
