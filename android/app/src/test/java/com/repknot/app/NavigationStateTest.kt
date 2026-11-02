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
}
