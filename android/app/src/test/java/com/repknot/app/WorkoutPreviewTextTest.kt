package com.repknot.app

import com.repknot.domain.LoadUnit
import com.repknot.domain.PastSet
import com.repknot.domain.Prefill
import com.repknot.domain.Prescription
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class WorkoutPreviewTextTest {
    private val prescription = Prescription(0, "id", "Press", 3, 8, 12, 1, 2, 25.0, LoadUnit.KG)
    private fun set(position: Int, load: Double? = 25.0, unit: LoadUnit = LoadUnit.KG,
                    reps: Double? = 10.0, seconds: Int? = null, rir: Int? = 1) =
        PastSet(position, load, unit, reps, seconds, rir)

    @Test fun planShowsBoundsAndUnits() {
        assertEquals(PreviewValues("3 × 8–12", "1–2", "25 kg"), previewPlan(prescription))
        assertEquals(PreviewValues("3 × 12", "–", "7.5 lb"), previewPlan(prescription.copy(repMin = null, rirMin = null, rirMax = null, load = 7.5, unit = LoadUnit.LB)))
        assertEquals(PreviewValues("3 × –", "1", "12p"), previewPlan(prescription.copy(repMin = null, repMax = null, rirMax = null, load = 12.0, unit = LoadUnit.PLATES)))
        assertEquals("bodyweight", previewPlan(prescription.copy(load = null, unit = LoadUnit.BODYWEIGHT)).load)
        assertEquals("5 bodyweight", previewPlan(prescription.copy(load = 5.0, unit = LoadUnit.BODYWEIGHT)).load)
        assertEquals("–", previewPlan(prescription.copy(load = null)).load)
    }

    @Test fun lastPreservesAllValuesAndPositions() {
        val previous = Prefill.Previous("s", listOf(set(1), set(2, reps = null, seconds = 30, rir = null), set(3, reps = 8.5, seconds = 20, rir = 0)))
        assertEquals(listOf(PreviewValues("10, 30 s, 8.5 / 20 s", "1, –, 0", "25 kg")), previewLast(previous))
        assertEquals(emptyList<PreviewValues>(), previewLast(Prefill.StartLoad(null, LoadUnit.KG)))
        assertEquals(emptyList<PreviewValues>(), previewLast(Prefill.Previous("s", emptyList())))
    }

    @Test fun mixedLoadsUseAlignedRows() {
        val previous = Prefill.Previous("s", listOf(set(1), set(2, load = 25.0, unit = LoadUnit.LB, reps = null, rir = null), set(3, load = null, unit = LoadUnit.BODYWEIGHT)))
        assertEquals(listOf(PreviewValues("10", "1", "25 kg"), PreviewValues("–", "–", "25 lb"), PreviewValues("10", "1", "bodyweight")), previewLast(previous))
        assertEquals("–", previewLast(Prefill.Previous("s", listOf(set(1, load = null, unit = LoadUnit.PLATES)))).single().load)
    }

    @Test fun datesAreCompactAndUnambiguous() {
        val today = LocalDate.of(2026, 11, 1)
        assertEquals("–", previewDate(null, today))
        assertEquals("today", previewDate(today, today))
        for (days in 1L..6L) assertEquals("${days}d ago", previewDate(today.minusDays(days), today))
        assertEquals("Oct 25", previewDate(today.minusDays(7), today))
        assertEquals("Dec 31, 2025", previewDate(LocalDate.of(2025, 12, 31), today))
        assertEquals("Nov 2", previewDate(today.plusDays(1), today))
        assertEquals("Jan 1, 2027", previewDate(LocalDate.of(2027, 1, 1), today))
    }
}
