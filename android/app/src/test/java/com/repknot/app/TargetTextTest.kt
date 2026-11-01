package com.repknot.app

import com.repknot.domain.LoadUnit
import com.repknot.domain.TodayTarget
import org.junit.Assert.assertEquals
import org.junit.Test

class TargetTextTest {
    @Test fun moveUpWording() {
        assertEquals("Target · 27.5 kg × 8+ — all sets hit the top last time", targetText(TodayTarget.MoveUp(25.0, 27.5, LoadUnit.KG, 8)))
        assertEquals("Target · 12p — all sets hit the top last time", targetText(TodayTarget.MoveUp(10.0, 12.0, LoadUnit.PLATES, null)))
        assertEquals("Target · move up from 25 kg — pick the next load", targetText(TodayTarget.MoveUp(25.0, null, LoadUnit.KG, 8)))
        assertEquals("Target · top of the range — add load or a harder variation", targetText(TodayTarget.MoveUp(null, null, LoadUnit.BODYWEIGHT, 8)))
        assertEquals("Target · top of the range — pick the next load", targetText(TodayTarget.MoveUp(null, null, LoadUnit.LB, 8)))
        assertEquals("Target · 30 lb × 8+ — all sets hit the top last time", targetText(TodayTarget.MoveUp(null, 30.0, LoadUnit.LB, 8)))
    }

    @Test fun repeatWording() {
        fun repeat(load: Double?, unit: LoadUnit, min: Int?, max: Int?, reps: List<Double> = listOf(10.0, 8.5)) =
            targetText(TodayTarget.Repeat(load, unit, reps, min, max))
        assertEquals("Target · 25 kg at RIR 1–2, add a rep where it's there (last 10, 8.5)", repeat(25.0, LoadUnit.KG, 1, 2))
        assertEquals("Target · bodyweight at RIR 1, add a rep where it's there (last 10, 8.5)", repeat(null, LoadUnit.BODYWEIGHT, 1, 1))
        assertEquals("Target · 12p at RIR 1+, add a rep where it's there", repeat(12.0, LoadUnit.PLATES, 1, null, emptyList()))
        assertEquals("Target · repeat at RIR ≤2, add a rep where it's there (last 10, 8.5)", repeat(null, LoadUnit.LB, null, 2))
        assertEquals("Target · 25 lb, add a rep where it's there", repeat(25.0, LoadUnit.LB, null, null, emptyList()))
    }
}
