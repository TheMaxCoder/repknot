package com.repknot.app

import com.repknot.data.*
import com.repknot.domain.*
import org.junit.Assert.*
import org.junit.Test

class WorkoutStateTest {
    private val row = SessionRow("s", "2026-11-01", "Workout", null, "in_progress", 0, 0, null, null, null, null)
    private val exercise = SessionExerciseRow("e", "s", 1, "x", "Press", plannedSets = 3, plannedRepMin = 6, plannedRepMax = 10, plannedLoad = 20.0)
    private val first = ProgramWorkout("a", "A", 0, emptyList())
    private val second = ProgramWorkout("b", "B", 1, emptyList())
    private val pastSet = PastSet(0, 30.0, LoadUnit.LB, 8.5, null, null)
    private val past = PastSession("old", "p", "a", 1, listOf(PastExercise(0, "x", listOf(pastSet))))

    @Test fun todayUsesRotationAndFallbackName() {
        assertEquals(WorkoutUiState.NoProgram, todayState(null, null, emptyList()))
        val state = todayState(Program("p", listOf(second, first)), null, listOf(past)) as WorkoutUiState.Today
        assertEquals("p", state.programId)
        assertEquals("Program", state.programName)
        assertEquals(second, state.suggested)
        assertEquals(listOf(first, second), state.workouts)
    }
    @Test fun previewShowsPrescriptionsWithPreviousValuesAndMarksTheSuggestion() {
        val press = Prescription(1, "x", "Press", 3, 6, 10, 1, 2, 20.0, LoadUnit.LB)
        val deck = Prescription(0, "y", "Deck", 2, 12, 20, 1, 2, 12.0, LoadUnit.PLATES)
        val program = Program("p", listOf(first.copy(exercises = listOf(press, deck)), second))
        val preview = previewState(program, "PPL", "a", listOf(past))!!
        assertEquals("PPL", preview.programName)
        assertFalse(preview.suggested)  // "a" was done last, so "b" is suggested
        assertEquals(listOf("Deck", "Press"), preview.exercises.map { it.prescription.exerciseName })
        assertEquals(Prefill.Previous("old", listOf(pastSet)), preview.exercises[1].previous)
        assertNotNull(preview.exercises[1].target)
        assertEquals(Prefill.StartLoad(12.0, LoadUnit.PLATES), preview.exercises[0].previous)
        assertNull(preview.exercises[0].previousOn)
        assertEquals(java.time.LocalDate.of(1970, 1, 1), preview.exercises[1].previousOn)
        assertTrue(previewState(program, null, "b", listOf(past))!!.suggested)
        assertNull(previewState(program, null, "missing", emptyList()))
    }
    @Test fun prescriptionAndSuggestions() {
        val p = prescriptionFor(exercise, LoadUnit.LB)
        assertEquals(3, p.sets); assertEquals(LoadUnit.LB, p.unit); assertEquals(10, p.repMax)
        assertEquals(0, prescriptionFor(exercise.copy(plannedSets = null, plannedUnit = "plates"), LoadUnit.KG).sets)
        assertEquals(LoadUnit.PLATES, prescriptionFor(exercise.copy(plannedUnit = "plates"), LoadUnit.KG).unit)
        val previous = Prefill.Previous("old", listOf(pastSet, pastSet.copy(load = 40.0)))
        assertEquals(SetSuggestion(30.0, LoadUnit.LB, 8.5), suggestionFor(previous, 0, exercise, LoadUnit.LB))
        assertEquals(SetSuggestion(40.0, LoadUnit.LB, 8.5), suggestionFor(previous, 9, exercise, LoadUnit.LB))
        assertEquals(SetSuggestion(20.0, LoadUnit.KG, 10.0), suggestionFor(Prefill.StartLoad(20.0, LoadUnit.KG), 0, exercise, LoadUnit.KG))
        assertEquals(SetSuggestion(null, LoadUnit.BODYWEIGHT, null), suggestionFor(Prefill.StartLoad(4.0, LoadUnit.BODYWEIGHT), 0, exercise.copy(plannedRepMax = null), LoadUnit.BODYWEIGHT))
    }
    @Test fun sessionOrdersAndExcludesSelfAndUsesChoicesForUnit() {
        val early = exercise.copy(id = "f", position = 0, exerciseId = "y")
        val choices = listOf(ExerciseRow("x", "x", "Press", "lb", false), ExerciseRow("y", "y", "Other", null, false))
        val sets = listOf(SetRow("2", "s", "e", 1, 1.0, "lb", 1.0), SetRow("1", "s", "e", 0, 1.0, "lb", 1.0))
        val self = past.copy(id = "s", finishedAtMillis = 2, exercises = listOf(PastExercise(0, "x", listOf(pastSet.copy(load = 99.0)))))
        val state = sessionState(SessionSnapshot(row, listOf(exercise, early), sets), listOf(past, self), choices, emptyList(), emptyList())
        assertEquals(listOf("f", "e"), state.exercises.map { it.row.id })
        assertEquals(listOf("1", "2"), state.exercises[1].sets.map { it.id })
        assertEquals(Prefill.Previous("old", listOf(pastSet)), state.exercises[1].previous)
        assertEquals(SetSuggestion(30.0, LoadUnit.LB, 8.5), state.exercises[1].suggestion)
        assertNull(state.exercises[0].previousOn)
        assertEquals(java.time.LocalDate.of(1970, 1, 1), state.exercises[1].previousOn)
        assertEquals(LoadUnit.KG, state.exercises[0].suggestion.unit)
        assertEquals(choices, state.choices)
    }
    @Test fun targetsAndDeltasUsePreviousHistoryAndRecordedUnits() {
        val previousSet = pastSet.copy(rir = 1)
        val history = listOf(past.copy(exercises = listOf(PastExercise(0, "x", listOf(previousSet)))))
        val logged = listOf(SetRow("1", "s", "e", 0, 35.0, "lb", 8.5, rir = 1),
            SetRow("2", "s", "e", 1, 35.0, null, 8.5, rir = 1))
        val choices = listOf(ExerciseRow("x", "x", "Press", "lb", false))
        fun state(e: SessionExerciseRow) = sessionState(SessionSnapshot(row, listOf(e), logged), history, choices, emptyList(), emptyList()).exercises.single()
        val prescribed = state(exercise)
        assertNotNull(prescribed.target)
        assertEquals(listOf(SetDelta.UP, SetDelta.NONE), prescribed.deltas)
        assertEquals(prescribed.sets.size, prescribed.deltas.size)
        assertNull(state(exercise.copy(substitutedFor = "original")).target)
    }
    @Test fun configuredUnitsAndSubstitutionsDoNotRelabelLoads() {
        val catalog = listOf(ExerciseRow("x", "x", "Press", "plates", false))
        fun state(e: SessionExerciseRow) = sessionState(SessionSnapshot(row, listOf(e), emptyList()), emptyList(), catalog, emptyList(), emptyList()).exercises.single()
        assertEquals(LoadUnit.PLATES, state(exercise).suggestion.unit)
        assertEquals(LoadUnit.KG, state(exercise.copy(plannedUnit = "kg")).suggestion.unit)
        val substituted = state(exercise.copy(plannedUnit = "kg", substitutedFor = "original"))
        assertEquals(LoadUnit.PLATES, substituted.suggestion.unit)
        assertNull(substituted.suggestion.load)
        val bodyweight = sessionState(SessionSnapshot(row, listOf(exercise), emptyList()), emptyList(),
            listOf(ExerciseRow("x", "x", "Press", "bodyweight", false)), emptyList(), emptyList()).exercises.single()
        assertEquals(LoadUnit.BODYWEIGHT, bodyweight.suggestion.unit)
        assertNull(bodyweight.suggestion.load)
    }
}
