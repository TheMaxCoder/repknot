package com.repknot.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NetworkValidationTest {
    @Test fun syncsOnlyOnValidationTransitionsAndNewDefaultNetworks() {
        val tracker = NetworkValidation<String>()
        assertFalse(tracker.changed("wifi", false))
        assertTrue(tracker.changed("wifi", true))
        assertFalse(tracker.changed("wifi", true))
        assertFalse(tracker.changed("wifi", false))
        assertTrue(tracker.changed("wifi", true))
        assertTrue(tracker.changed("mobile", true))
        tracker.lost("wifi")
        assertFalse(tracker.changed("mobile", true))
        tracker.lost("mobile")
        assertTrue(tracker.changed("mobile", true))
    }
}
