package com.repknot.domain

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

class ProgressTest {
    private val day = LocalDate.of(2026, 1, 1)
    private fun set(reps: Double? = 10.0, load: Double? = 20.0, rir: Int? = 2,
        unit: LoadUnit = LoadUnit.KG, duration: Int? = null, pos: Int = 0) =
        PastSet(pos, load, unit, reps, duration, rir)
    private fun session(n: Int, sets: List<PastSet> = listOf(set()), id: String = "x", light: Boolean = false,
        date: LocalDate = day.plusDays(n.toLong())) = PastSession("s$n", null, null,
        date.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli(), listOf(PastExercise(0, id, sets, "Name", 2)), light)
    private fun exp(vararg sets: PastSet) = Exposure("s", day, "Name", LoadUnit.KG, sets.toList(), 2, false)
    private fun p(sets: Int = 1, repMin: Int? = 8, repMax: Int? = 10, rirMin: Int? = 2,
        unit: LoadUnit = LoadUnit.KG) = Prescription(0, "x", "Name", sets, repMin, repMax, rirMin, 3, null, unit)
    private fun status(vararg s: PastSession, today: LocalDate = day.plusDays(12)) = progress(s.toList(), listOf(p()), today).single()

    @Test fun comparisons() {
        val base = exp(set())
        assertEquals(Comparison.BETTER, compare(exp(set(11.0)), base))
        assertEquals(Comparison.SAME, compare(exp(set(10.5)), base))
        assertEquals(Comparison.WORSE, compare(exp(set(9.0)), base))
        assertEquals(Comparison.WORSE, compare(exp(set(95.0, rir = null)), exp(set(100.0, rir = null))))
        assertEquals(Comparison.BETTER, compare(exp(set(9.0, rir = 4)), base))
        assertEquals(Comparison.WORSE, compare(exp(set(9.0, rir = null)), base))
        assertEquals(Comparison.NOT_COMPARABLE, compare(exp(set(load = 21.0)), base))
        val t = exp(set(null, duration = 30))
        assertEquals(Comparison.BETTER, compare(exp(set(null, duration = 35)), t))
        assertEquals(Comparison.SAME, compare(exp(set(null, duration = 34)), t))
        assertEquals(Comparison.WORSE, compare(exp(set(null, duration = 25)), t))
    }

    @Test fun lineageMergesFiltersAndSkipsEmptyLatest() {
        val s = session(1).copy(exercises = listOf(
            PastExercise(1, "x", listOf(set(8.0, unit = LoadUnit.LB)), "second"),
            PastExercise(0, "x", listOf(set(9.0), set(10.0, pos = 1)), "first", 3)))
        val empty = session(2, emptyList())
        val result = status(s, empty)
        assertEquals(LoadUnit.KG, result.unit)
        assertEquals(listOf(9.0, 10.0), result.exposures.single().sets.map { it.reps })
        assertEquals("first", result.name)
        assertEquals(3, result.exposures.single().plannedSets)
        val lb = session(3, listOf(set(unit = LoadUnit.LB)))
        assertEquals(LoadUnit.LB, status(s, empty, lb).unit)
        assertEquals(2, status(s, empty, lb).exposures.size)
    }

    @Test fun statusesAndLight() {
        assertEquals(ProgressReason.FewSessions(1), status(session(0), session(1, light = true)).reason)
        assertEquals(2, status(session(0), session(1, light = true)).exposures.size)
        assertEquals(ProgressReason.FewSessions(0), status(session(0, light = true)).reason)
        assertEquals(ProgressReason.BackAfterGap(14), status(session(0), session(14)).reason)
        val down = status(session(0), session(1, listOf(set(load = 15.0))), session(2, listOf(set(load = 15.0))))
        assertEquals(ProgressReason.LoadDown(20.0, 15.0, LoadUnit.KG), down.reason)
        val below = status(session(0), session(1), session(2, listOf(set(8.0))), session(3, listOf(set(8.0))))
        assertEquals(ProgressReason.BelowUsual(20.0, LoadUnit.KG), below.reason)
        val improved = status(session(0), session(1, listOf(set(11.0))), session(2, listOf(set(11.0))))
        assertEquals(ProgressStatus.IMPROVING, improved.status)
        assertTrue(improved.reason is ProgressReason.MoreReps)
        val moved = status(session(0), session(1, listOf(set(load = 25.0))), session(2, listOf(set(load = 25.0))))
        assertTrue(moved.reason is ProgressReason.MovedUp)
        val weak = status(session(0), session(1, listOf(set(7.0, 25.0))), session(2, listOf(set(7.0, 25.0))))
        assertEquals(ProgressStatus.MAINTAINING, weak.status)
        val oldBest = status(session(0), session(1, listOf(set(11.0))), session(2, listOf(set(11.0))),
            session(3, listOf(set(11.0))), today = day.plusDays(16))
        assertEquals(ProgressStatus.MAINTAINING, oldBest.status)
    }

    @Test fun readinessAndTarget() {
        val history = listOf(session(0, listOf(set(load = 25.0))), session(1), session(2))
        assertEquals(2, progress(history, listOf(p()), day.plusDays(3)).single().readyStreak)
        assertEquals(TodayTarget.MoveUp(20.0, 25.0, LoadUnit.KG, 8), todayTarget(p(), history))
        assertEquals(TodayTarget.MoveUp(20.0, null, LoadUnit.KG, 8), todayTarget(p(), history.drop(1)))
        assertEquals(TodayTarget.Repeat(20.0, LoadUnit.KG, listOf(9.0), 2, 3), todayTarget(p(), listOf(session(0, listOf(set(9.0))))))
        assertFalse(readyToMoveUp(exp(set(rir = 1)), p()))
        assertFalse(readyToMoveUp(exp(set(rir = null)), p()))
        assertNull(todayTarget(p(repMax = null), history))
        assertNull(todayTarget(p(), emptyList()))
        assertNull(todayTarget(p(), listOf(session(0, listOf(set(null, duration = 30))))))
        val bw = listOf(session(0, listOf(set(load = null, unit = LoadUnit.BODYWEIGHT))))
        assertEquals(TodayTarget.MoveUp(null, null, LoadUnit.BODYWEIGHT, 8), todayTarget(p(unit = LoadUnit.BODYWEIGHT), bw))
    }

    @Test fun deltas() {
        val h = listOf(session(0))
        assertEquals(SetDelta.UP, setDelta(set(11.0), 0, "x", h))
        assertEquals(SetDelta.DOWN, setDelta(set(9.0), 0, "x", h))
        assertEquals(SetDelta.SAME, setDelta(set(10.0), 0, "x", h))
        assertEquals(SetDelta.NONE, setDelta(set(rir = null), 0, "x", h))
        assertEquals(SetDelta.NONE, setDelta(set(load = 19.0), 0, "x", h))
        assertEquals(SetDelta.UP, setDelta(set(load = 21.0), 0, "x", h))
        assertEquals(SetDelta.NONE, setDelta(set(), 1, "x", h))
        val t = listOf(session(0, listOf(set(null, duration = 30))))
        assertEquals(SetDelta.UP, setDelta(set(null, duration = 35), 0, "x", t))
        assertEquals(SetDelta.DOWN, setDelta(set(null, duration = 25), 0, "x", t))
        assertEquals(SetDelta.SAME, setDelta(set(null, duration = 34), 0, "x", t))
    }

    @Test fun boundariesAndOrdering() {
        val two = exp(set(10.0), set(10.0, pos = 1))
        assertEquals(Comparison.WORSE, compare(exp(set(9.0), set(9.0, pos = 1)), two))
        assertEquals(Comparison.SAME, compare(exp(set(9.0), set(9.5, pos = 1)), two))
        assertEquals(Comparison.NOT_COMPARABLE, compare(exp(set(reps = null)), two))
        assertEquals(p(), prescriptionFor("x", listOf(p().copy(position = 3), p())))
        assertNull(prescriptionFor("missing", listOf(p())))
        val gap = status(session(0), session(14), session(15))
        assertEquals(ProgressReason.BackAfterGap(14), gap.reason)
        val freshBest = status(session(0), session(1, listOf(set(11.0))), session(2, listOf(set(11.0))),
            session(3, listOf(set(11.0))), today = day.plusDays(15))
        assertEquals(ProgressStatus.IMPROVING, freshBest.status)
        val oneAfter = status(session(0), session(1, listOf(set(11.0))), session(2, listOf(set(11.0))),
            today = day.plusDays(30))
        assertEquals(ProgressStatus.IMPROVING, oneAfter.status)
        assertEquals(setOf("s1"), oneAfter.bestSessionIds)
        assertEquals(0, progress(listOf(session(0), session(1, listOf(set(load = 25.0, rir = 1)))), listOf(p()), day).single().readyStreak)
        assertFalse(readyToMoveUp(exp(set()), p(sets = 2)))
        val unknown = listOf(session(0, listOf(set(10.0))), session(1, listOf(set(load = 25.0)), light = true))
        assertEquals(TodayTarget.MoveUp(20.0, 25.0, LoadUnit.KG, 8), todayTarget(p(), unknown))
        assertEquals(SetDelta.NONE, setDelta(set(reps = null), 0, "x", listOf(session(0))))
        assertEquals(SetDelta.NONE, setDelta(set(), -1, "x", listOf(session(0))))
    }

    @Test fun exposureAndComparisonEdges() {
        val merged = session(0).copy(exercises = listOf(
            PastExercise(2, "x", listOf(set(12.0, pos = 1), set(11.0, pos = 0))),
            PastExercise(0, "x", listOf(set(8.0), set(9.0, unit = LoadUnit.LB, pos = 1)), "first", 4)))
        val e = status(merged).exposures.single()
        assertEquals(listOf(8.0, 11.0, 12.0), e.sets.map { it.reps })
        assertEquals(listOf(8.0, 11.0, 12.0), e.topSets.map { it.reps })
        assertEquals(4, e.plannedSets)
        val bw = exp(set(8.0, load = null), set(reps = null, load = null), set(9.0, load = null))
        assertEquals(listOf(8.0, 9.0), bw.topSets.map { it.reps })
        assertEquals(Comparison.NOT_COMPARABLE, compare(e, e.copy(unit = LoadUnit.LB)))
        assertEquals(Comparison.NOT_COMPARABLE, compare(e, exp(set(null, duration = 30))))
        assertEquals(Comparison.WORSE, compare(exp(set(null, duration = 25), set(null, duration = 25)),
            exp(set(null, duration = 30), set(null, duration = 30))))
        assertEquals(Comparison.SAME, compare(exp(set(null, duration = 25), set(null, duration = 26)),
            exp(set(null, duration = 30), set(null, duration = 30))))
        assertEquals(Comparison.WORSE, compare(exp(set(null, duration = 95)), exp(set(null, duration = 100))))
        val unfinished = session(9).copy(finishedAtMillis = null)
        assertTrue(progress(listOf(unfinished), emptyList(), day).isEmpty())
        val sameTime = session(1).copy(id = "later", exercises = listOf(PastExercise(0, "x", listOf(set(unit = LoadUnit.LB)), "later")))
        val tied = status(session(1), sameTime)
        assertEquals(LoadUnit.LB, tied.unit)
        assertEquals("later", tied.name)
        assertEquals("x", status(session(1).copy(exercises = listOf(PastExercise(0, "x", listOf(set()))))).name)
    }

    @Test fun statusBoundariesAndBestHistory() {
        val multipleGaps = status(session(0), session(14), session(30), today = day.plusDays(30))
        assertEquals(ProgressReason.BackAfterGap(16), multipleGaps.reason)
        val noBest = status(session(0), session(1), session(2))
        assertEquals(ProgressReason.NoNewBestSince(null, 3), noBest.reason)
        assertTrue(noBest.bestSessionIds.isEmpty())
        val manyBests = status(session(0), session(1, listOf(set(11.0))), session(2, listOf(set(12.0))))
        assertEquals(setOf("s1", "s2"), manyBests.bestSessionIds)
        assertEquals(ProgressReason.MoreReps(20.0, LoadUnit.KG, day.plusDays(2)), manyBests.reason)
        val lowerLoadBetterReps = status(session(0), session(1, listOf(set(11.0, 15.0))),
            session(2, listOf(set(12.0, 15.0))))
        assertTrue(lowerLoadBetterReps.bestSessionIds.isEmpty())
        val baseMajority = status(session(0), session(1), session(2), session(3, listOf(set(8.0))),
            session(4, listOf(set(8.0))))
        assertTrue(baseMajority.reason is ProgressReason.BelowUsual)
        val notBoth = status(session(0), session(1), session(2, listOf(set(8.0))), session(3))
        assertFalse(notBoth.reason is ProgressReason.BelowUsual)
        val changedLoad = progress(listOf(session(0), session(1), session(2, listOf(set(load = 25.0)))),
            listOf(p()), day.plusDays(3)).single()
        assertEquals(1, changedLoad.readyStreak)
        assertTrue(readyToMoveUp(exp(set()), p(sets = 0)))
        assertFalse(readyToMoveUp(exp(set(null, duration = 30)), p()))
    }

    @Test fun targetAndDeltaEdges() {
        assertNull(todayTarget(p(), listOf(session(0, light = true))))
        assertNull(todayTarget(p(unit = LoadUnit.LB), listOf(session(0))))
        val history = listOf(session(0), session(1, listOf(set(4.0)), light = true))
        assertEquals(SetDelta.UP, setDelta(set(11.0), 0, "x", history))
        assertEquals(SetDelta.NONE, setDelta(set(), 0, "missing", history))
        assertEquals(SetDelta.NONE, setDelta(set(), 0, "x", listOf(session(0, listOf(set(rir = null))))))
        assertEquals(SetDelta.NONE, setDelta(set(load = null), 0, "x", history))
        assertEquals(SetDelta.NONE, setDelta(set(load = 21.0, rir = null), 0, "x", history))
        assertEquals(SetDelta.SAME, setDelta(set(null, duration = 30, rir = null), 0, "x",
            listOf(session(0, listOf(set(null, duration = 30, rir = null))))))
    }

    @Test fun attentionDateTiesAndDipDetails() {
        val x = status(session(0))
        val early = x.copy(name = "A", exposures = status(session(0)).exposures)
        val laterZ = x.copy(name = "Z", exposures = status(session(1)).exposures)
        val laterB = x.copy(name = "B", exposures = status(session(1)).exposures)
        assertEquals(listOf("B", "Z", "A"), attentionOrder(listOf(early, laterZ, laterB)).map { it.name })
        fun many(n: Int, reps: Double, light: Boolean = false) = session(n, light = light).copy(
            exercises = (0..2).map { PastExercise(it, "$it", listOf(set(reps))) })
        val dips = sessionDips(listOf(many(0, 10.0), many(1, 5.0, light = true), many(2, 8.0)))
        assertEquals(listOf(SessionDip("s2", day.plusDays(2), 3, listOf("0", "1", "2"))), dips)
    }

    private fun broad(n: Int, reps: Double = 10.0, light: Boolean = false, load: Double = 20.0) =
        session(n, light = light).copy(exercises = (0..2).map { PastExercise(it, "$it", listOf(set(reps, load))) })

    @Test fun dipRecoveryAndPersistence() {
        val dip = listOf(broad(0), broad(1, 8.0))
        assertEquals(DipState.PENDING, dipStatuses(dip).single().state)
        assertEquals(0, dipStatuses(dip + broad(2, light = true)).single().evaluated)
        assertEquals(DipState.RESOLVED, dipStatuses(dip + broad(2)).single().state)
        assertEquals(DipState.RESOLVED, dipStatuses(dip + broad(2, 8.0, load = 25.0)).single().state)
        val down = dipStatuses(dip + broad(2, 8.0, load = 15.0)).single()
        assertEquals(DipState.PENDING, down.state)
        assertEquals(0, down.evaluated)
        val stillBelow = dipStatuses(dip + broad(2, 8.0)).single()
        assertEquals(DipState.PERSISTING, stillBelow.state)
        assertEquals(listOf("0", "1", "2"), stillBelow.stillBelow)
        val mixed = broad(2).copy(exercises = (0..2).map { i ->
            PastExercise(i, "$i", listOf(if (i < 2) set(8.0) else set()))
        })
        assertEquals(DipState.PERSISTING, dipStatuses(dip + mixed).single().state)
        val timed = broad(2).copy(exercises = (0..2).map { PastExercise(it, "$it", listOf(set(null, 15.0, duration = 30))) })
        assertEquals(0, dipStatuses(dip + timed).single().evaluated)
        // Usual is the last 3 pre-dip sessions: matching an ordinary one counts as recovered even
        // when the session right before the dip was a peak.
        val peak = listOf(broad(0, 10.0), broad(1, 10.0), broad(2, 13.0), broad(3, 8.0), broad(4, 10.0))
        assertEquals(DipState.RESOLVED, dipStatuses(peak).single().state)
        val olderThanUsual = listOf(broad(0, 10.0), broad(1, 13.0), broad(2, 13.0), broad(3, 13.0), broad(4, 8.0), broad(5, 10.0))
        assertEquals(DipState.PERSISTING, dipStatuses(olderThanUsual).single().state)
    }

    @Test fun dipAlertPriorityAndBoundaries() {
        val h = listOf(broad(0), broad(1, 8.0))
        assertEquals(DipAlertKind.NOTICE, dipAlert(h, day.plusDays(11), null)?.kind)
        assertNull(dipAlert(h, day.plusDays(12), null))
        assertEquals(DipAlertKind.FAST_LOSS, dipAlert(h, day.plusDays(11), -1.01)?.kind)
        assertEquals(DipAlertKind.NOTICE, dipAlert(h, day.plusDays(11), -1.0)?.kind)
        assertNull(dipAlert(h + broad(2), day.plusDays(2), -2.0))
        val persistent = h + broad(2, 8.0)
        assertEquals(DipAlertKind.PERSISTING, dipAlert(persistent, day.plusDays(22), -2.0)?.kind)
        assertNull(dipAlert(persistent, day.plusDays(23), -2.0))
        val pattern = h + broad(2) + broad(3, 8.0)
        assertEquals(DipAlertKind.PATTERN, dipAlert(pattern, day.plusDays(3), -2.0)?.kind)
        val more = pattern + (4..9).map { broad(it) } + broad(10, light = true)
        assertNotEquals(DipAlertKind.PATTERN, dipAlert(more, day.plusDays(10), null)?.kind)
        assertNull(dipAlert(h, day, -2.0))
    }

    @Test fun dipsAndAttention() {
        fun many(n: Int, count: Int, below: Int) = session(n).copy(exercises = (0 until count).map {
            PastExercise(it, "$it", listOf(set(if (n == 1 && it < below) 8.0 else 10.0)))
        })
        assertEquals(1, sessionDips(listOf(many(0, 5, 0), many(1, 5, 3))).size)
        assertTrue(sessionDips(listOf(many(0, 3, 0), many(1, 3, 2))).isEmpty())
        assertTrue(sessionDips(listOf(many(0, 7, 0), many(1, 7, 3))).isEmpty())
        val x = status(session(0))
        val items = listOf(x.copy(name = "old", daysSinceLast = 15, status = ProgressStatus.POSSIBLE_DECLINE),
            x.copy(name = "few"), x.copy(name = "maintain", status = ProgressStatus.MAINTAINING),
            x.copy(name = "improve", status = ProgressStatus.IMPROVING), x.copy(name = "ready", readyStreak = 2),
            x.copy(name = "decline", status = ProgressStatus.POSSIBLE_DECLINE))
        assertEquals(listOf("decline", "ready", "improve", "maintain", "few", "old"), attentionOrder(items).map { it.name })
    }
}
