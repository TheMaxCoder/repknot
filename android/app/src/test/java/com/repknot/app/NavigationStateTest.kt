package com.repknot.app

import org.junit.Assert.*
import org.junit.Test

class NavigationStateTest {
    @Test fun preservesOriginStacksAndReselection() {
        val home = NavigationState().push("preview")
        val history = home.select(RootTab.HISTORY).push("exercise:id").push("session:id")
        assertEquals(listOf("exercise:id"), history.back()!!.details)
        val progress = history.select(RootTab.PROGRESS).push("settings")
        assertEquals(history.details, progress.select(RootTab.HISTORY).details)
        assertEquals(listOf("preview"), progress.select(RootTab.HOME).details)
        assertTrue(history.select(RootTab.HISTORY).details.isEmpty())
        assertEquals(RootTab.HOME, NavigationState(selected = RootTab.HISTORY).back()!!.selected)
        assertNull(NavigationState().back())
    }

    @Test fun reminderOnColdStartKeepsBodyWhenTheWorkoutLoadsLater() {
        // Activity starts: workout still loading, the reminder opens Body, then the workout loads.
        val f = LoggerFocus().sessionLoaded(null, loading = true)
            .reminderOpened(loading = true)
            .sessionLoaded("w1", loading = false)
        assertFalse(f.focused)
        assertFalse(f.holdForReminder)
        // A workout started afterwards takes focus as usual.
        assertTrue(f.sessionLoaded("w2", loading = false).focused)
    }

    @Test fun reminderWithTheWorkoutAlreadyLoadedOrAbsent() {
        val loaded = LoggerFocus().sessionLoaded("w1", loading = false)
        assertTrue(loaded.focused)
        val warm = loaded.reminderOpened(loading = false)
        assertFalse(warm.focused)
        assertFalse(warm.holdForReminder)
        assertTrue(warm.sessionLoaded("w2", loading = false).focused)
        // No current workout: the hold ends once loading finishes without one.
        val none = LoggerFocus().reminderOpened(loading = true).sessionLoaded(null, loading = false)
        assertFalse(none.holdForReminder)
        assertTrue(none.sessionLoaded("w3", loading = false).focused)
    }

    @Test fun newWorkoutTakesFocusAndTheSameOneDoesNotRefocus() {
        val f = LoggerFocus().sessionLoaded("w1", loading = false).copy(focused = false)
        assertFalse(f.sessionLoaded("w1", loading = false).focused)
        assertTrue(f.sessionLoaded(null, loading = false).sessionLoaded("w1", loading = false).focused)
    }
}
