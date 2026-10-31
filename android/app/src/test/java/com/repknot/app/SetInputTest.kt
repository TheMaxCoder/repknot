package com.repknot.app

import com.repknot.domain.LoadUnit
import com.repknot.domain.SetValues
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SetInputTest {
    @Test fun acceptsDotAndCommaDecimals() {
        assertEquals(SetValues(20.5, LoadUnit.KG, 8, null), parseSetInput("20.5", LoadUnit.KG, "8", ""))
        assertEquals(SetValues(20.5, LoadUnit.KG, 8, 2), parseSetInput(" 20,5 ", LoadUnit.KG, " 8 ", " 2 "))
    }

    @Test fun rejectsInvalidFields() {
        assertNull(parseSetInput("", LoadUnit.KG, "8", ""))
        assertNull(parseSetInput("abc", LoadUnit.KG, "8", ""))
        assertNull(parseSetInput("1,000.5", LoadUnit.KG, "8", ""))
        assertNull(parseSetInput("-5", LoadUnit.KG, "8", ""))
        assertNull(parseSetInput("20", LoadUnit.KG, "0", ""))
        assertNull(parseSetInput("20", LoadUnit.KG, "8.5", ""))
        assertNull(parseSetInput("20", LoadUnit.KG, "8", "x"))
        assertNull(parseSetInput("20", LoadUnit.KG, "8", "-1"))
    }
}
