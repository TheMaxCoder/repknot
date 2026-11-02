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
