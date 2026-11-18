package com.repknot.domain

import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class BodyTest {
    private val today = LocalDate.of(2026, 11, 1)
    private fun points(rate: Double, latest: Double = 90.0) = listOf(21L, 14L, 7L, 0L).map {
        today.minusDays(it) to (latest - rate * it / 7)
    }
    private fun summary(waistRate: Double? = -0.7, weightRate: Double = -0.2, waist: Double = 91.0,
                        declines: Int = 0, dip: Boolean = false): BodySummary {
        val weights = points(weightRate, 60.0)
        val waists = waistRate?.let { points(it, waist) } ?: listOf(today to waist)
        return bodySummary(weights.map { CheckIn(it.first, it.second, null) } +
            waists.map { CheckIn(it.first, null, it.second) }, today, 80.0, declines, dip)
    }

    @Test fun trendWindowAndLatest() {
        assertNull(bodyTrend(listOf(today.plusDays(1) to 1.0), today))
        val result = bodyTrend(points(-0.7) + listOf(today.minusDays(28) to 999.0, today to 90.0, today.plusDays(1) to 1.0), today)!!
        assertEquals(90.0, result.latest, 0.0)
        assertEquals(today, result.latestOn)
        assertEquals(-0.7, result.perWeek!!, 0.00001)
        assertEquals(-0.7, result.recentPerWeek!!, 0.00001)  // the 28-day-old outlier is outside 4 weeks
        assertEquals(5, result.weeks)
        // Long trend: 3 different weeks over 2+ weeks. Recent: 4 check-ins over 2+ weeks.
        val three = bodyTrend(points(-1.0).take(3), today)!!
        assertEquals(-1.0, three.perWeek!!, 0.00001)
        assertNull(three.recentPerWeek)
        assertNull(bodyTrend(points(-1.0).drop(2), today)!!.perWeek)
        assertNull(bodyTrend((0L..3L).map { today.minusDays(it) to it.toDouble() }, today)!!.perWeek)
        assertEquals(42.0, bodyTrend(listOf(today to 1.0, today to 42.0), today)!!.latest, 0.0)
    }

    @Test fun longTrendLooksPastARecentPlateau() {
        // Steady −0.5 cm/week for 9 weeks, then flat for 3 weeks with extra check-ins.
        val steady = (11 downTo 3).map { w -> today.minusDays(7L * w) to (90.0 - 0.5 * (11 - w)) }
        val flat = listOf(14L, 7L, 3L, 2L, 0L).map { today.minusDays(it) to 86.0 }
        val trend = bodyTrend(steady + flat, today)!!
        assertTrue("long trend ${trend.perWeek}", trend.perWeek!! < -0.4)
        assertEquals(0.0, trend.recentPerWeek!!, 0.00001)
        assertEquals(12, trend.weeks)
        // Check-ins older than 12 weeks are left out.
        assertEquals(trend, bodyTrend(steady + flat + (today.minusDays(84) to 200.0), today))
    }

    @Test fun aBusyOrBloatedWeekCannotSwingTheTrend() {
        val line = (8 downTo 0).map { w -> today.minusDays(7L * w) to (90.0 - 0.5 * (8 - w)) }
        // Five high check-ins in one week count as one weekly point, and Theil–Sen ignores it.
        val bloated = (0L..4L).map { today.minusDays(28 + it) to 95.0 }
        assertEquals(-0.5, bodyTrend(line + bloated, today)!!.perWeek!!, 0.00001)
    }

    @Test fun tooFastUsesTheRecentWeightRate() {
        // Weight barely moved for 8 weeks, then dropped fast in the last 4.
        val weights = (11 downTo 4).map { w -> CheckIn(today.minusDays(7L * w), 70.0, null) } +
            listOf(21L, 14L, 7L, 0L).map { d -> CheckIn(today.minusDays(d), 70.0 - (21 - d) / 7.0, null) }
        val waists = points(-0.5, 90.0).map { CheckIn(it.first, null, it.second) }
        val s = bodySummary(weights + waists, today, 80.0, 2, false)
        assertTrue("recent ${s.recentWeightPctPerWeek}", s.recentWeightPctPerWeek!! < -1.0)
        assertTrue("long ${s.weightPctPerWeek}", s.weightPctPerWeek!! > -1.0)  // the long trend alone would not warn
        assertEquals(BodyVerdict.TOO_FAST, s.verdict)
    }

    @Test fun verdictOrderingAndThresholds() {
        assertEquals(BodyVerdict.NOT_ENOUGH_DATA, bodySummary(emptyList(), today, 80.0, 3, true).verdict)
        assertEquals(BodyVerdict.GOAL_REACHED, summary(weightRate = -1.0, waist = 79.0, declines = 3).verdict)
        assertEquals(BodyVerdict.TOO_FAST, summary(null, -1.0, declines = 2).verdict)
        assertEquals(BodyVerdict.TOO_FAST, summary(null, -1.0, dip = true).verdict)
        assertEquals(BodyVerdict.NOT_ENOUGH_DATA, summary(null, -1.0, declines = 1).verdict)
        assertEquals(BodyVerdict.ON_TRACK, summary(-0.7, -0.2, declines = 2).verdict)
        assertEquals(BodyVerdict.STALLED, summary(0.0).verdict)
    }

    @Test fun projectionAndRates() {
        val s = summary(-0.5, -0.3)
        assertEquals(11.0, s.toGoalCm!!, 0.00001)
        assertEquals(today.plusDays(154), s.goalDate)
        assertEquals(-0.5, s.weightPctPerWeek!!, 0.00001)
        assertNull(summary(0.1).goalDate)
        assertNull(summary(-0.05, waist = 100.0).goalDate)
    }
}
