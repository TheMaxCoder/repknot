package com.repknot.app

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.repknot.app.ui.*

@Composable
fun AppNavigation(workout: WorkoutViewModel, progress: ProgressViewModel,
                  history: HistoryViewModel, settings: @Composable (() -> Unit) -> Unit) {
    val progressState by progress.state.collectAsState()
    val historyState by history.state.collectAsState()
    val workoutState by workout.state.collectAsState()
    val holder = rememberSaveableStateHolder()
    val saver = remember { mapSaver(
        save = { state: NavigationState -> mapOf("selected" to state.selected.name, "home" to ArrayList(state.stacks[RootTab.HOME].orEmpty()), "history" to ArrayList(state.stacks[RootTab.HISTORY].orEmpty()), "progress" to ArrayList(state.stacks[RootTab.PROGRESS].orEmpty())) },
        restore = { map -> @Suppress("UNCHECKED_CAST") NavigationState(RootTab.valueOf(map["selected"] as String), mapOf(RootTab.HOME to (map["home"] as List<String>), RootTab.HISTORY to (map["history"] as List<String>), RootTab.PROGRESS to (map["progress"] as List<String>))) }
    ) }
    var nav by rememberSaveable(stateSaver = saver) { mutableStateOf(NavigationState()) }
    var focused by rememberSaveable { mutableStateOf(true) }
    var previousId by rememberSaveable { mutableStateOf<String?>(null) }
    val session = workoutState as? WorkoutUiState.Session
    LaunchedEffect(session?.row?.id, workoutState is WorkoutUiState.Loading) {
        if (session != null && session.row.id != previousId) { focused = true; previousId = session.row.id }
        if (session == null && workoutState !is WorkoutUiState.Loading) previousId = null
    }
    val showingLogger = session != null && focused
    val keyboardVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0
    val route = nav.details.lastOrNull()
    val back = { nav = nav.back() ?: nav }
    BackHandler(enabled = showingLogger || nav.back() != null) {
        if (showingLogger) { focused = false; nav = nav.copy(selected = RootTab.HOME, stacks = nav.stacks + (RootTab.HOME to emptyList())) } else back()
    }
    Scaffold(bottomBar = {
        if (!showingLogger && !keyboardVisible) NavigationBar {
            RootTab.entries.forEach { tab -> NavigationBarItem(selected = nav.selected == tab, onClick = {
                if (tab == nav.selected && tab == RootTab.HOME && workoutState is WorkoutUiState.Preview) workout.closePreview()
                nav = nav.select(tab)
            },
                icon = { Icon(painterResource(when (tab) { RootTab.HOME -> R.drawable.ic_nav_home; RootTab.HISTORY -> R.drawable.ic_nav_history; RootTab.PROGRESS -> R.drawable.ic_nav_progress }), contentDescription = null) },
                label = { Text(tab.name.lowercase().replaceFirstChar { it.uppercase() }) }) }
        }
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding)) {
            if (!showingLogger && route != "settings" && !(nav.selected == RootTab.HOME && route == null && workoutState is WorkoutUiState.Preview)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    IconButton(onClick = { nav = nav.push("settings") }) {
                        Icon(painterResource(R.drawable.ic_settings), contentDescription = stringResource(R.string.settings))
                    }
                }
            }
            Box(Modifier.weight(1f)) {
                if (showingLogger) holder.SaveableStateProvider("workout") {
                    WorkoutScreen(workout, progressState, { nav = nav.select(RootTab.PROGRESS) }, { nav = nav.push("settings") })
                } else if (route == "settings") holder.SaveableStateProvider("settings-${nav.selected}") { settings(back) }
                else if (route == "body") holder.SaveableStateProvider("body") {
                    BodyScreen(progressState, back, progress::addCheckIn, progress::uploadCheckIn, progress::deleteCheckIn)
                } else if (route?.startsWith("exercise:") == true) holder.SaveableStateProvider("${nav.selected}-${nav.details.lastIndex}-$route") {
                    ExerciseHistoryScreen(historyState, progressState, route.removePrefix("exercise:"), back) { nav = nav.push("workout:$it") }
                } else if (route?.startsWith("workout:") == true) holder.SaveableStateProvider("${nav.selected}-${nav.details.lastIndex}-$route") {
                    WorkoutHistoryScreen(historyState, route.removePrefix("workout:"), back) { nav = nav.push("exercise:$it") }
                } else when (nav.selected) {
                    RootTab.HOME -> if (session != null) {
                        Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            Text("Current workout", style = MaterialTheme.typography.headlineMedium)
                            Surface(shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
                                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text(session.row.title, style = MaterialTheme.typography.titleMedium)
                                    Button(onClick = { focused = true }, modifier = Modifier.fillMaxWidth()) {
                                        Text(if (session.row.status == "in_progress") "Resume workout" else "View workout summary")
                                    }
                                }
                            }
                            TextButton(onClick = { nav = nav.select(RootTab.PROGRESS) }) { Text("From Progress") }
                        }
                    } else holder.SaveableStateProvider("workout") {
                        WorkoutScreen(workout, progressState, { nav = nav.select(RootTab.PROGRESS) }, { nav = nav.push("settings") })
                    }
                    RootTab.HISTORY -> holder.SaveableStateProvider("history") {
                        HistoryScreen(historyState, { nav = nav.push("workout:$it") }, { nav = nav.push("exercise:$it") })
                    }
                    RootTab.PROGRESS -> holder.SaveableStateProvider("progress") {
                        ProgressScreen(progressState, back, { nav = nav.push("body") }, progress::addCheckIn) { nav = nav.push("exercise:$it") }
                    }
                }
            }
        }
    }
}
