package com.repknot.app

import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CheckInReminderTest {
    // 2026-11-16 is a Monday.
    private fun at(day: Int, hour: Int, minute: Int = 0) = LocalDateTime.of(2026, 11, day, hour, minute)

    @Test fun dueRule() {
        assertFalse(checkInDue(at(16, 7, 59), emptyList()))
        assertFalse(checkInDue(at(17, 8), emptyList()))
        assertFalse(checkInDue(at(16, 8), listOf(at(16, 8).toLocalDate())))
        assertTrue(checkInDue(at(16, 8), emptyList()))
    }

    @Test fun beforeEightOnAReminderDayIsThatMorning() =
        assertEquals(at(16, 8), nextCheckInReminder(at(16, 7, 59)))

    @Test fun exactlyEightMovesToTheNextReminderDay() =
        assertEquals(at(18, 8), nextCheckInReminder(at(16, 8)))

    @Test fun afterEightMondayIsWednesday() =
        assertEquals(at(18, 8), nextCheckInReminder(at(16, 21)))

    @Test fun tuesdayIsWednesday() =
        assertEquals(at(18, 8), nextCheckInReminder(at(17, 6)))

    @Test fun wednesdayEveningIsFriday() =
        assertEquals(at(20, 8), nextCheckInReminder(at(18, 9)))

    @Test fun fridayAfterEightSkipsTheWeekendToMonday() =
        assertEquals(at(23, 8), nextCheckInReminder(at(20, 8, 1)))

    @Test fun sundayIsMonday() =
        assertEquals(at(23, 8), nextCheckInReminder(at(22, 23, 59)))

    @Test fun crossesMonthEnd() =
        assertEquals(LocalDateTime.of(2026, 12, 2, 8, 0), nextCheckInReminder(at(30, 9)))
}
