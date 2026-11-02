package com.repknot.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ExerciseSelectionTest {
    @Test fun selectionAndFallbackFollowSessionOrder() {
        val exercises = listOf(ExerciseCompletion("a", 2, 2), ExerciseCompletion("b", 3, 1), ExerciseCompletion("c", null, 0))
        assertEquals("b", selectedExerciseId(null, exercises))
        assertEquals("a", selectedExerciseId("a", exercises))
        assertEquals("c", selectedExerciseId("missing", exercises.reversed()))
        assertEquals("c", selectedExerciseId(null, listOf(ExerciseCompletion("a", 1, 1), ExerciseCompletion("c", 2, 2))))
        assertEquals("a", selectedExerciseId(null, listOf(ExerciseCompletion("a", 0, 4))))
        assertNull(selectedExerciseId("a", emptyList()))
    }

    @Test fun freshScreenReturnsToTheLastLoggedExerciseEvenWhenComplete() {
        val exercises = listOf(ExerciseCompletion("a", 3, 3), ExerciseCompletion("b", 4, 0))
        assertEquals("a", selectedExerciseId(null, exercises, lastLogged = "a"))
        assertEquals("b", selectedExerciseId("b", exercises, lastLogged = "a"))
        assertEquals("b", selectedExerciseId(null, exercises, lastLogged = "removed"))
        assertEquals("b", selectedExerciseId(null, exercises, lastLogged = null))
    }

}
