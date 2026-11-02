package com.repknot.app

import com.repknot.data.ProgramExerciseRow
import com.repknot.data.ProgramWorkoutRow
import com.repknot.data.SetRow
import org.junit.Assert.*
import org.junit.Test

class WorkoutTimingTest {
    private val workout = ProgramWorkoutRow("w", "p", "key", "Workout", 0, null)
    private val prescription = ProgramExerciseRow("a", "w", 1, "x", 3, null, null, null, null, null, 180, null, "kg", null)
    private val set = SetRow("a", "s", "e", 0, null, null, null, loggedAt = 1000)

    @Test fun programLookupAndMissingBranches() {
        fun find(key: String?, program: String?, workouts: List<ProgramWorkoutRow>, rows: List<ProgramExerciseRow>, id: String = "x") =
            programRestS(key, program, workouts, rows, id)
        val workouts = listOf(workout)
        val rows = listOf(prescription)
        assertNull(find(null, "p", workouts, rows))
        assertNull(find("key", null, workouts, rows))
        assertNull(find("other", "p", workouts, rows))
        assertNull(find("key", "other", workouts, rows))
        assertNull(find("key", "p", emptyList(), rows))
        assertNull(find("key", "p", workouts, emptyList()))
        assertNull(find("key", "p", workouts, rows, "other"))
        assertNull(find("key", "p", workouts, listOf(prescription.copy(workoutId = "other"))))
        assertNull(find("key", "p", workouts, listOf(prescription.copy(restSMax = null))))
        assertEquals(180, find("key", "p", workouts, rows))
        assertEquals(120, find("key", "p", workouts, rows + prescription.copy(id = "b", position = 0, restSMax = 120)))
        assertNull(find("key", "p", workouts, rows + prescription.copy(id = "b", position = 0, restSMax = null)))
    }

    @Test fun targetAndToggle() {
        assertEquals(120, restTargetS("x", null, emptyMap()))
        assertEquals(180, restTargetS("x", 180, emptyMap()))
        assertEquals(120, restTargetS("x", 180, mapOf("x" to 120)))
        assertEquals(180, toggledRestTarget(120))
        assertEquals(120, toggledRestTarget(121))
    }

    @Test fun newestRestAndCutoff() {
        assertNull(restState(1000, "finished", listOf(set)))
        assertNull(restState(1000, "in_progress", emptyList()))
        assertNull(restState(1000, "in_progress", listOf(set.copy(loggedAt = null))))
        assertEquals(RestState("e", 0), restState(0, "in_progress", listOf(set)))
        assertEquals(RestState("e", 900), restState(901000, "in_progress", listOf(set)))
        assertNull(restState(902000, "in_progress", listOf(set)))
        val later = set.copy(id = "b", sessionExerciseId = "f", loggedAt = 2000)
        assertEquals(RestState("f", 1), restState(3000, "in_progress", listOf(later, set)))
        assertEquals(RestState("f", 2), restState(3000, "in_progress", listOf(set, later.copy(loggedAt = 1000))))
    }

    @Test fun formatRest() {
        assertEquals("0:05", restText(5))
        assertEquals("1:47", restText(107))
        assertEquals("12:00", restText(720))
    }

    @Test fun formatWorkoutElapsed() {
        assertNull(elapsedWorkoutText(null, 0, "in_progress"))
        assertNull(elapsedWorkoutText(0, 0, "finished"))
        assertEquals("0 min", elapsedWorkoutText(1000, 0, "in_progress"))
        assertEquals("59 min", elapsedWorkoutText(0, 59 * 60000L, "in_progress"))
        assertEquals("1 h 0 min", elapsedWorkoutText(0, 60 * 60000L, "in_progress"))
        assertEquals("1 h 5 min", elapsedWorkoutText(0, 65 * 60000L, "in_progress"))
        assertEquals("2 h 0 min", elapsedWorkoutText(0, 120 * 60000L, "in_progress"))
    }
}
