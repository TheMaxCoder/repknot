package com.repknot.app

import com.repknot.domain.*
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class ProgressTextTest {
    private val day = LocalDate.of(2026, 10, 26)
    private fun item(status: ProgressStatus = ProgressStatus.NOT_ENOUGH_DATA, ready: Int = 0, days: Long = 0) =
        ExerciseProgress("id", "Name", LoadUnit.KG, status, ProgressReason.FewSessions(0), ready, days, emptyList(), emptySet())

    @Test fun groups() {
        assertEquals("Not trained recently", groupLabel(item(ProgressStatus.POSSIBLE_DECLINE, 2, 15)))
        assertEquals("Needs a look", groupLabel(item(ProgressStatus.POSSIBLE_DECLINE, 2)))
        assertEquals("Ready to move up", groupLabel(item(ProgressStatus.IMPROVING, 2)))
        assertEquals("Improving", groupLabel(item(ProgressStatus.IMPROVING)))
        assertEquals("Holding steady", groupLabel(item(ProgressStatus.MAINTAINING)))
        assertEquals("Not enough data yet", groupLabel(item()))
        assertEquals("Needs a look", groupLabel(item(ProgressStatus.POSSIBLE_DECLINE, days = 14)))
    }

    @Test fun reasons() {
        assertEquals("Back after 14 days — judged again after 2 sessions", reasonText(ProgressReason.BackAfterGap(14)))
        assertEquals("1 session so far — needs 3", reasonText(ProgressReason.FewSessions(1)))
        assertEquals("2 sessions so far — needs 3", reasonText(ProgressReason.FewSessions(2)))
        assertEquals("Moved up to 27.5 kg on Oct 26", reasonText(ProgressReason.MovedUp(27.5, LoadUnit.KG, day)))
        assertEquals("More reps at 9p on Oct 26", reasonText(ProgressReason.MoreReps(9.0, LoadUnit.PLATES, day)))
        assertEquals("More reps on Oct 26", reasonText(ProgressReason.MoreReps(null, LoadUnit.KG, day)))
        assertEquals("More reps on Oct 26", reasonText(ProgressReason.MoreReps(1.0, LoadUnit.BODYWEIGHT, day)))
        assertEquals("Below your usual at 5 kg for 2 sessions", reasonText(ProgressReason.BelowUsual(5.0, LoadUnit.KG)))
        assertEquals("Below your usual for 2 sessions", reasonText(ProgressReason.BelowUsual(null, LoadUnit.KG)))
        assertEquals("Load down from 25 kg to 21 kg for 2 sessions", reasonText(ProgressReason.LoadDown(25.0, 21.0, LoadUnit.KG)))
        assertEquals("Holding steady — last best Oct 26, 1 session since", reasonText(ProgressReason.NoNewBestSince(day, 1)))
        assertEquals("Holding steady — last best Oct 26, 2 sessions since", reasonText(ProgressReason.NoNewBestSince(day, 2)))
        assertEquals("Holding steady — no best yet", reasonText(ProgressReason.NoNewBestSince(null, 0)))
    }

    @Test fun flags() {
        val ready = "Top of the range 2 sessions in a row — move up, or change tempo or pause if the stack is maxed"
        val absent = "Not trained for 15 days"
        assertEquals(emptyList<String>(), flagText(item()))
        assertEquals(listOf(ready), flagText(item(ready = 2)))
        assertEquals(listOf(absent), flagText(item(days = 15)))
        assertEquals(listOf(ready, absent), flagText(item(ready = 2, days = 15)))
    }

    @Test fun exposuresAndDips() {
        val set = PastSet(0, 25.0, LoadUnit.KG, 10.0, null, null)
        val e = Exposure("s", day, "Name", LoadUnit.KG, listOf(set), null, false)
        assertEquals("Oct 26 · 1 set · 25 kg × 10", exposureLine(e))
        assertEquals("Oct 26 · 1 set · 25 kg × 10 · light", exposureLine(e.copy(light = true)))
        val dip = SessionDip("s", day, 7, listOf("a", "b"))
        assertEquals("Broad dip on Oct 26 — 2 of 7 exercises below usual (Row, b). Check sleep, food and recovery.", dipText(dip, mapOf("a" to "Row")))
        assertEquals("Broad dip on Oct 26 — 2 of 7 exercises below usual (Row, Curl). Check sleep, food and recovery.", dipText(dip, mapOf("a" to "Row", "b" to "Curl")))
    }
}
