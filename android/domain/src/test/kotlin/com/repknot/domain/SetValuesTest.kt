package com.repknot.domain

import org.junit.Assert.*
import org.junit.Test

class SetValuesTest {
    @Test fun bodyweightAndUnknownRirAreValid() {
        assertEquals(0.0, SetValues(0.0, LoadUnit.KG, 8, null).load, 0.0)
    }
    @Test fun rejectsInvalidActualValues() {
        listOf(-1.0, Double.NaN, Double.POSITIVE_INFINITY).forEach { load ->
            assertThrows(IllegalArgumentException::class.java) { SetValues(load, LoadUnit.KG, 8, null) }
        }
        assertThrows(IllegalArgumentException::class.java) { SetValues(20.0, LoadUnit.KG, 0, null) }
        assertThrows(IllegalArgumentException::class.java) { SetValues(20.0, LoadUnit.KG, 8, -1) }
    }
}
