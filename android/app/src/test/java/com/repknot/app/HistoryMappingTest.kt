package com.repknot.app

import com.repknot.data.SessionExerciseRow
import com.repknot.data.SessionRow
import com.repknot.data.SessionSnapshot
import com.repknot.data.SetRow
import org.junit.Assert.*
import org.junit.Test

class HistoryMappingTest {
    private fun snapshot(id: String, status: String = "finished", date: String = "2026-01-01", finish: Long? = null,
                         number: Long? = null, upload: Boolean = false, exercises: List<SessionExerciseRow> = emptyList(),
                         sets: List<SetRow> = emptyList()) = SessionSnapshot(
        SessionRow(id, date, "", null, status, 0, 0, null, null, number, null, finishedAt = finish, uploadRequested = upload), exercises, sets)

    @Test fun completionOrderAndLabels() {
        val rows = listOf(snapshot("d", "in_progress"), snapshot("e", "unknown"), snapshot("c"),
            snapshot("b", number = 1), snapshot("a", number = 1), snapshot("f", finish = 1),
            snapshot("g", date = "2027-01-01"))
        assertEquals(listOf("g", "f", "c", "a", "b"), completedHistory(rows).map { it.row.id })
        assertEquals(listOf("Uploaded", "Waiting to upload", "On this phone", "Sync conflict", "Deleted on server · local copy retained"),
            listOf(snapshot("1", "pushed"), snapshot("2", upload = true), snapshot("3"), snapshot("4", "conflict"), snapshot("5", "conflict_deleted")).map(::historyStatus))
    }

    @Test fun directoryOrdersLatestNamesAndStableTies() {
        fun recorded(session: String, date: String, id: String, name: String) = snapshot(session, date = date,
            exercises = listOf(SessionExerciseRow("x$session", session, 0, id, name)),
            sets = listOf(SetRow("s$session", session, "x$session", 0, null, null, null)))
        val rows = listOf(recorded("1", "2025-01-01", "a", "Old"),
            recorded("2", "2026-01-01", "a", "New"), recorded("3", "2026-01-01", "b", "New"),
            recorded("4", "2027-01-01", "c", "First"))
        assertEquals(listOf("c", "a", "b"), historyExercises(rows).map { it.id })
        assertEquals(listOf("a", "b"), historyExercises(rows, " NEW ").map { it.id })
        assertTrue(historyExercises(rows, "Old").isEmpty())
    }

    @Test fun snapshotRowsUsePositionThenIdForTies() {
        val exercises = listOf(SessionExerciseRow("b", "s", 0, "e", "Exercise"), SessionExerciseRow("a", "s", 0, "e", "Exercise"))
        val sets = listOf(SetRow("z", "s", "b", 0, null, null, null), SetRow("a", "s", "a", 0, 1.25, "plates", 2.5))
        val result = completedHistory(listOf(snapshot("s", exercises = exercises, sets = sets))).single()
        assertEquals(listOf("a", "b"), result.exercises.map { it.id })
        assertEquals(listOf("a", "z"), result.sets.map { it.id })
        assertEquals(2.5, result.sets.first().reps!!, 0.0)
        assertEquals("plates", result.sets.first().unit)
    }

    @Test fun directoryUsesRecordedInstancesAndRetainsPartialSets() {
        val exercises = listOf(SessionExerciseRow("x2", "s", 1, "ex", "Squat"),
            SessionExerciseRow("x1", "s", 0, "ex", "Squat"), SessionExerciseRow("x3", "s", 2, "unused", "Unused"))
        val sets = listOf(SetRow("s2", "s", "x2", 1, 10.5, "lb", null), SetRow("s1", "s", "x1", 0, 2.25, null, null))
        val history = completedHistory(listOf(snapshot("s", exercises = exercises, sets = sets)))
        assertEquals(listOf("x1", "x2", "x3"), history.single().exercises.map { it.id })
        assertEquals(sets.reversed(), history.single().sets)
        assertEquals(listOf(HistoryExercise("ex", "Squat", "2026-01-01")), historyExercises(history, "  sQU  "))
        assertTrue(historyExercises(history, "unused").isEmpty())
        assertTrue(historyExercises(emptyList()).isEmpty())
    }
}
