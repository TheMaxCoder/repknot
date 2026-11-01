package com.repknot.app

import com.repknot.data.CheckInRow
import com.repknot.domain.BodySummary
import com.repknot.domain.BodyTrend
import com.repknot.domain.BodyVerdict
import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class BodyTextTest {
    private val today = LocalDate.parse("2026-11-09")
    private val waist = BodyTrend(91.5, today, -0.6)
    private val weight = BodyTrend(74.6, today, -0.1)
    private val s = BodySummary(waist, weight, 80.0, 11.5, LocalDate.parse("2026-12-20"), -0.2, BodyVerdict.ON_TRACK)

    @Test fun waistAndTrend() {
        assertEquals("Waist 91.5 cm · 11.5 cm to go", waistLine(s))
        assertEquals("Waist 79.5 cm · goal reached (80 cm)", waistLine(s.copy(waist = waist.copy(latest = 79.5), toGoalCm = -0.5)))
        assertEquals("No waist measurement yet", waistLine(s.copy(waist = null)))
        assertEquals("Waist 91.5 cm", waistLine(s.copy(toGoalCm = null)))
        assertEquals("−0.6 cm/week over 4 weeks · 80 cm around Dec 20", rateLine(s))
        assertEquals("+0.6 cm/week over 4 weeks", rateLine(s.copy(waist = waist.copy(perWeek = 0.6), goalDate = null)))
        assertEquals("Not enough check-ins for a trend (needs 4 over 2+ weeks)", rateLine(s.copy(waist = waist.copy(perWeek = null))))
        assertEquals("", rateLine(s.copy(waist = null)))
    }
    @Test fun summaryMetrics() {
        assertEquals("91.75 cm", bodyMeasurement(91.75, "cm"))
        assertEquals("74.6 kg", bodyMeasurement(74.6, "kg"))
        assertEquals("Waist · 11.5 cm to go", waistCaption(s))
        assertEquals("Waist · goal reached (79.75 cm)", waistCaption(s.copy(goalCm = 79.75, toGoalCm = 0.0)))
        assertEquals("Waist", waistCaption(s.copy(toGoalCm = null)))
        assertEquals("−0.1 kg/week (−0.2 %/week)", weightTrendLine(s))
        assertEquals("", weightTrendLine(s.copy(weight = null)))
    }
    @Test fun weightAndDates() {
        assertEquals("Weight 74.6 kg · −0.1 kg/week (−0.2 %/week)", weightLine(s))
        assertEquals("Weight 74.6 kg · −0.1 kg/week", weightLine(s.copy(weightPctPerWeek = null)))
        assertEquals("Weight 74.6 kg", weightLine(s.copy(weight = weight.copy(perWeek = null))))
        assertEquals("", weightLine(s.copy(weight = null)))
        assertEquals("Last check-in Nov 2", lastCheckInLine(today.minusDays(7), today))
        assertEquals("Last check-in Oct 31 · 9 days ago — time for a check-in", lastCheckInLine(today.minusDays(9), today))
    }
    @Test fun verdicts() {
        assertEquals("On track — waist down, strength holding.", verdictLine(s, 0, false))
        assertEquals("On track — waist down; keep an eye on the exercises below.", verdictLine(s, 1, false))
        assertEquals("On track — waist down; keep an eye on the exercises below.", verdictLine(s, 0, true))
        assertEquals("Waist hasn't moved in 4 weeks — consider tightening the deficit.", verdictLine(s.copy(verdict = BodyVerdict.STALLED), 0, false))
        assertEquals("Losing over 1 % of bodyweight a week while strength slips — consider eating a bit more.", verdictLine(s.copy(verdict = BodyVerdict.TOO_FAST), 0, false))
        assertEquals("Goal reached — time to set the next phase.", verdictLine(s.copy(verdict = BodyVerdict.GOAL_REACHED), 0, false))
        assertEquals("", verdictLine(s.copy(verdict = BodyVerdict.NOT_ENOUGH_DATA), 0, false))
    }
    @Test fun rowsAndParsing() {
        val row = CheckInRow("id", "2026-10-31", 74.6, 91.5, pending = true)
        assertEquals("Oct 31 · 74.6 kg · 91.5 cm · not uploaded", checkInLine(row))
        assertEquals("Oct 31 · waist 91.5 cm", checkInLine(row.copy(weightKg = null, pending = false)))
        assertEquals("Oct 25 · 74.5 kg · 91.75 cm", checkInLine(row.copy(checkedOn = "2026-10-25", weightKg = 74.5, waistCm = 91.75, pending = false)))
        assertEquals("Sep 30 · chest 104 · arms 36.5", checkInLine(row.copy(checkedOn = "2026-09-30", weightKg = null, waistCm = null, chestCm = 104.0, armsCm = 36.5, pending = false)))
        assertEquals("Oct 31 · 74.6 kg", checkInLine(row.copy(waistCm = null, pending = false)))
        val parsed = parseCheckIn("2026-10-31", "74,6", "", " hi ")!!
        assertEquals(74.6, parsed.weightKg!!, 0.0)
        assertEquals("hi", parsed.notes)
        listOf("0", "1000", "NaN", "x").forEach { assertNull(parseCheckIn("2026-10-31", it, "80", "")) }
        assertNull(parseCheckIn("bad", "74", "", ""))
        assertNull(parseCheckIn("2026-10-31", "", "", ""))
    }
}
