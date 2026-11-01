package com.repknot.app

import com.repknot.domain.LoadUnit
import com.repknot.domain.PastSet
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class PreviousSummaryTest {
    private val today = LocalDate.of(2026, 11, 1)
    private val set = PastSet(0, 25.0, LoadUnit.KG, 10.0, null, null)

    @Test fun labels() {
        listOf(0L to "today", 1L to "yesterday", 2L to "2 days ago", 6L to "6 days ago", 7L to "Oct 25", 20L to "Oct 12").forEach { (days, label) ->
            assertEquals("Last · $label", lastLabel(today.minusDays(days), today))
        }
    }

    @Test fun summaries() {
        assertEquals("3 sets · 25 kg × 10, 10, 9", previousSummary(listOf(set, set, set.copy(reps = 9.0))))
        assertEquals("2 sets · 25 kg × 10, 27.5 kg × 8", previousSummary(listOf(set, set.copy(load = 27.5, reps = 8.0))))
        assertEquals("2 sets · 9p × 12, 11", previousSummary(listOf(set.copy(load = 9.0, unit = LoadUnit.PLATES, reps = 12.0), set.copy(load = 9.0, unit = LoadUnit.PLATES, reps = 11.0))))
        assertEquals("2 sets · bodyweight × 8, 6", previousSummary(listOf(set.copy(load = null, unit = LoadUnit.BODYWEIGHT, reps = 8.0), set.copy(load = null, unit = LoadUnit.BODYWEIGHT, reps = 6.0))))
        assertEquals("1 set · 25 kg × 40 s", previousSummary(listOf(set.copy(reps = null, durationS = 40))))
        assertEquals("3 sets · 25 kg × 8.5, 10, 10 · RIR 2, –, 1", previousSummary(listOf(set.copy(reps = 8.5, rir = 2), set, set.copy(rir = 1))))
        assertEquals("1 set · 25 kg × 10 · RIR 1", previousSummary(listOf(set.copy(rir = 1))))
    }
}
