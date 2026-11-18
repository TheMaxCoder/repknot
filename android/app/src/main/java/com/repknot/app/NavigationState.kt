package com.repknot.app

enum class RootTab { HOME, HISTORY, PROGRESS }

data class NavigationState(
    val selected: RootTab = RootTab.HOME,
    val stacks: Map<RootTab, List<String>> = emptyMap(),
) {
    val details: List<String> get() = stacks[selected].orEmpty()
    fun push(route: String): NavigationState = copy(stacks = stacks + (selected to (details + route)))
    fun select(tab: RootTab): NavigationState = if (tab == selected) {
        copy(stacks = stacks + (tab to emptyList()))
    } else copy(selected = tab)
    /** Null leaves system Back unhandled. */
    fun back(): NavigationState? = when {
        details.isNotEmpty() -> copy(stacks = stacks + (selected to details.dropLast(1)))
        selected != RootTab.HOME -> copy(selected = RootTab.HOME)
        else -> null
    }
}

/**
 * Whether the workout logger fills the screen. A newly loaded current workout takes focus, except
 * right after a reminder tap: the reminder's destination wins even when the workout finishes
 * loading later (cold start; weekly review 2026-11-18, task 040).
 */
data class LoggerFocus(val focused: Boolean = true, val previousId: String? = null, val holdForReminder: Boolean = false) {
    fun sessionLoaded(sessionId: String?, loading: Boolean): LoggerFocus = when {
        sessionId != null && sessionId != previousId ->
            if (holdForReminder) copy(previousId = sessionId, holdForReminder = false)
            else copy(focused = true, previousId = sessionId)
        sessionId == null && !loading -> copy(previousId = null, holdForReminder = false)
        else -> this
    }
    /** The reminder opens Body; a workout still loading must not take the screen back. */
    fun reminderOpened(loading: Boolean): LoggerFocus = copy(focused = false, holdForReminder = loading)
}
