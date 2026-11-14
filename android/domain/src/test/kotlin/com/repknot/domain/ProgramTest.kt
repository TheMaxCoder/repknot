package com.repknot.domain

import org.junit.Assert.*
import org.junit.Test

class ProgramTest {
    private val a = ProgramWorkout("a", "A", 1, emptyList())
    private val b = ProgramWorkout("b", "B", 2, emptyList())
    private val c = ProgramWorkout("c", "C", 3, emptyList())
    private val program = Program("p", listOf(c, a, b))
    private val prescription = Prescription(1, "squat", "Squat", 3, 5, 8, 1, 2, 20.0, LoadUnit.KG)
    private fun set(position: Int, unit: LoadUnit = LoadUnit.KG) =
        PastSet(position, position.toDouble(), unit, 5.5, null, 1)
    private fun exercise(position: Int, id: String = "squat", vararg sets: PastSet) =
        PastExercise(position, id, sets.toList())
    private fun session(id: String, time: Long?, key: String? = "a", programId: String? = "p",
        exercises: List<PastExercise> = emptyList()) = PastSession(id, programId, key, time, exercises)

    @Test fun sequenceSortsAndEmptyProgramThrows() {
        assertEquals(a, todaysWorkout(program, emptyList()))
        assertThrows(IllegalArgumentException::class.java) { todaysWorkout(Program("p", emptyList()), emptyList()) }
    }

    @Test fun ignoresUnfinishedOtherProgramAdHocAndUnknownKeys() {
        val sessions = listOf(session("valid", 1, "a"), session("unfinished", null, "b"),
            session("other", 9, "b", "other"), session("adhoc", 10, "b", null),
            session("unknown", 11, "unknown"), session("null-key", 12, null))
        assertEquals(b, todaysWorkout(program, sessions))
        assertEquals(a, todaysWorkout(program, sessions.drop(1)))
    }

    @Test fun rotationContinuesAcrossVersionsOfTheSameProgram() {
        val republished = Program("p2", listOf(c, a, b), setOf("p", "p2"))
        val sessions = listOf(session("before-republish", 5, "b", "p"), session("older", 4, "a", "p2"),
            session("other-program", 9, "a", "other"))
        assertEquals(c, todaysWorkout(republished, sessions))
        assertEquals(b, todaysWorkout(Program("p2", listOf(c, a, b)), sessions.drop(1)))
        assertEquals(a, todaysWorkout(Program("p2", listOf(c, a, b)), sessions.take(1)))
    }

    @Test fun latestFinishedWorkoutAdvancesWrapsAndTiesUseInputOrder() {
        assertEquals(c, todaysWorkout(program, listOf(session("new", 5, "b"), session("old", 4, "c"))))
        assertEquals(a, todaysWorkout(program, listOf(session("first", 5, "b"), session("last", 5, "c"))))
        assertEquals(c, todaysWorkout(program, listOf(session("first", 5, "c"), session("last", 5, "b"))))
    }

    @Test fun prefillUsesLatestFinishedMatchingExerciseAndUnitAcrossPrograms() {
        val kg = set(1)
        val sessions = listOf(
            session("old", 1, exercises = listOf(exercise(1, sets = *arrayOf(kg)))),
            session("adhoc", 3, programId = null, exercises = listOf(exercise(1, sets = *arrayOf(kg)))),
            session("other", 4, programId = "other", exercises = listOf(exercise(1, sets = *arrayOf(kg)))),
            session("wrong unit", 5, exercises = listOf(exercise(1, sets = *arrayOf(set(1, LoadUnit.LB))))),
            session("wrong exercise", 6, exercises = listOf(exercise(1, "press", kg))),
            session("no sets", 7, exercises = listOf(exercise(1))),
            session("unfinished", null, exercises = listOf(exercise(1, sets = *arrayOf(kg)))))
        assertEquals(Prefill.Previous("other", listOf(kg)), prefill(prescription, sessions))
        assertEquals(Prefill.Previous("adhoc", listOf(kg)), prefill(prescription, sessions.filter { it.id != "other" }))
    }

    @Test fun prefillTieUsesLaterInputAndOrdersMatchingSetsAcrossExercises() {
        val one = set(1); val two = set(2); val three = set(3)
        val sessions = listOf(session("first", 10, exercises = listOf(exercise(1, sets = *arrayOf(one)))),
            session("last", 10, exercises = listOf(exercise(2, sets = *arrayOf(three, set(1, LoadUnit.LB))),
                exercise(1, sets = *arrayOf(two, one)), exercise(0, "press", set(1)))))
        assertEquals(Prefill.Previous("last", listOf(one, two, three)), prefill(prescription, sessions))
    }

    @Test fun missingHistoryReturnsPrescribedLoadIncludingNull() {
        assertEquals(Prefill.StartLoad(20.0, LoadUnit.KG), prefill(prescription, emptyList()))
        assertEquals(Prefill.StartLoad(null, LoadUnit.BODYWEIGHT),
            prefill(prescription.copy(load = null, unit = LoadUnit.BODYWEIGHT), emptyList()))
    }

    @Test fun inputsAreNotMutatedAndResultsAreDeterministic() {
        val sets = mutableListOf(set(2), set(1))
        val exercises = mutableListOf(PastExercise(1, "squat", sets))
        val sessions = mutableListOf(session("s", 1, exercises = exercises))
        val workouts = mutableListOf(c, a, b)
        val mutableProgram = Program("p", workouts)
        val beforeSets = sets.toList(); val beforeExercises = exercises.toList()
        val beforeSessions = sessions.toList(); val beforeWorkouts = workouts.toList()
        assertEquals(todaysWorkout(mutableProgram, sessions), todaysWorkout(mutableProgram, sessions))
        assertEquals(prefill(prescription, sessions), prefill(prescription, sessions))
        assertEquals(beforeSets, sets); assertEquals(beforeExercises, exercises)
        assertEquals(beforeSessions, sessions); assertEquals(beforeWorkouts, workouts)
    }
}
