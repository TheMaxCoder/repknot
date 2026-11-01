package com.repknot.app

import com.repknot.domain.LoadUnit
import org.junit.Assert.*
import org.junit.Test

class SetInputTest {
    @Test fun acceptsValues() {
        assertEquals(SetInput(20.5, LoadUnit.KG, 8.5, 2), parseSetInput(" 20,5 ", LoadUnit.KG, " 8,5 ", " 2 "))
        assertEquals(SetInput(0.0, LoadUnit.PLATES, 1.0, null), parseSetInput("0", LoadUnit.PLATES, "1", ""))
        assertEquals(SetInput(null, LoadUnit.BODYWEIGHT, 5.5, null), parseSetInput(" ", LoadUnit.BODYWEIGHT, "5.5", ""))
        assertEquals(SetInput(1.0, LoadUnit.LB, 1.0, 0), parseSetInput("1", LoadUnit.LB, "1", "0"))
    }
    @Test fun rejectsInvalidFields() {
        for (load in listOf("", "abc", "1,000.5", "-1", "NaN", "Infinity")) assertNull(parseSetInput(load, LoadUnit.KG, "8", ""))
        assertNull(parseSetInput("0", LoadUnit.BODYWEIGHT, "8", ""))
        for (reps in listOf("", "0", "-1", "8.3", "NaN", "Infinity")) assertNull(parseSetInput("1", LoadUnit.KG, reps, ""))
        for (rir in listOf("x", "-1", "1.5")) assertNull(parseSetInput("1", LoadUnit.KG, "8", rir))
    }
}
