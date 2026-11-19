package com.repknot.app

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import kotlinx.coroutines.delay
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.repknot.app.ui.*

@Composable
fun AppNavigation(workout: WorkoutViewModel, progress: ProgressViewModel,
                  history: HistoryViewModel, coachNotes: CoachNotesViewModel,
                  checkInRequested: Boolean, checkInOpened: () -> Unit, signedOut: Boolean, settings: @Composable () -> Unit) {
    val coachNotesState by coachNotes.state.collectAsState()
    var coachExpanded by rememberSaveable(coachNotesState.note?.id) { mutableStateOf(true) }
    val progressState by progress.state.collectAsState()
    val historyState by history.state.collectAsState()
    val workoutState by workout.state.collectAsState()
    val holder = rememberSaveableStateHolder()
    val saver = remember { mapSaver(
        save = { state: NavigationState -> mapOf("selected" to state.selected.name, "home" to ArrayList(state.stacks[RootTab.HOME].orEmpty()), "history" to ArrayList(state.stacks[RootTab.HISTORY].orEmpty()), "progress" to ArrayList(state.stacks[RootTab.PROGRESS].orEmpty())) },
        restore = { map -> @Suppress("UNCHECKED_CAST") NavigationState(RootTab.valueOf(map["selected"] as String), mapOf(RootTab.HOME to (map["home"] as List<String>), RootTab.HISTORY to (map["history"] as List<String>), RootTab.PROGRESS to (map["progress"] as List<String>))) }
    ) }
    var nav by rememberSaveable(stateSaver = saver) { mutableStateOf(NavigationState()) }
    val focusSaver = remember { mapSaver(
        save = { f: LoggerFocus -> mapOf("focused" to f.focused, "previousId" to f.previousId, "hold" to f.holdForReminder) },
        restore = { LoggerFocus(it["focused"] as Boolean, it["previousId"] as String?, it["hold"] as Boolean) },
    ) }
    var focus by rememberSaveable(stateSaver = focusSaver) { mutableStateOf(LoggerFocus()) }
    val session = workoutState as? WorkoutUiState.Session
    LaunchedEffect(session?.row?.id, workoutState is WorkoutUiState.Loading) {
        focus = focus.sessionLoaded(session?.row?.id, workoutState is WorkoutUiState.Loading)
    }
    var checkInDialog by rememberSaveable { mutableStateOf(false) }
    // Reminder tap: Progress → Body with the check-in dialog open; a running workout is kept (like Back).
    LaunchedEffect(checkInRequested) {
        if (checkInRequested) {
            focus = focus.reminderOpened(workoutState is WorkoutUiState.Loading)
            nav = nav.copy(selected = RootTab.PROGRESS, stacks = nav.stacks + (RootTab.PROGRESS to listOf("body")))
            checkInDialog = true
            checkInOpened()
        }
    }
    val openCheckIn = {
        focus = focus.reminderOpened(workoutState is WorkoutUiState.Loading)
        nav = nav.copy(selected = RootTab.PROGRESS, stacks = nav.stacks + (RootTab.PROGRESS to listOf("body")))
        checkInDialog = true
    }
    val showingLogger = session != null && focus.focused
    val keyboardVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0
    val route = nav.details.lastOrNull()
    val back = { nav = nav.back() ?: nav }
    BackHandler(enabled = showingLogger || nav.back() != null) {
        if (showingLogger) { focus = focus.copy(focused = false); nav = nav.copy(selected = RootTab.HOME, stacks = nav.stacks + (RootTab.HOME to emptyList())) } else back()
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
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    // Home root: the program name shares the top row with Settings.
                    val today = workoutState as? WorkoutUiState.Today
                    if (nav.selected == RootTab.HOME && route == null && today != null)
                        Text(today.programName, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge,
                            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 24.dp))
                    Spacer(Modifier.weight(1f))
                    SettingsButton(signedOut) { nav = nav.push("settings") }
                }
            }
            Box(Modifier.weight(1f)) {
                if (showingLogger) holder.SaveableStateProvider("workout") {
                    WorkoutScreen(workout, progressState, coachNotesState, coachExpanded, { coachExpanded = it }, coachNotes::markSeen, coachNotes::toggleDone, { nav = nav.select(RootTab.PROGRESS) }, openCheckIn, signedOut) { nav = nav.push("settings") }
                } else if (route == "settings") holder.SaveableStateProvider("settings-${nav.selected}") { settings() }
                else if (route == "body") holder.SaveableStateProvider("body") {
                    BodyScreen(progressState, checkInDialog, { checkInDialog = it }, progress::addCheckIn, progress::deleteCheckIn)
                } else if (route?.startsWith("exercise:") == true) holder.SaveableStateProvider("${nav.selected}-${nav.details.lastIndex}-$route") {
                    ExerciseHistoryScreen(historyState, progressState, route.removePrefix("exercise:")) { nav = nav.push("workout:$it") }
                } else if (route?.startsWith("workout:") == true) holder.SaveableStateProvider("${nav.selected}-${nav.details.lastIndex}-$route") {
                    WorkoutHistoryScreen(historyState, route.removePrefix("workout:")) { nav = nav.push("exercise:$it") }
                } else when (nav.selected) {
                    RootTab.HOME -> if (session != null) {
                        var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
                        LaunchedEffect(session.row.id, session.row.status) {
                            while (session.row.status == "in_progress") { now = System.currentTimeMillis(); delay(1000) }
                        }
                        val status = when (session.row.status) {
                            "in_progress" -> listOfNotNull("In progress", elapsedWorkoutText(session.row.startedAt, now, session.row.status)).joinToString(" · ")
                            "finished" -> if (session.row.uploadRequested) "Upload pending" else "Saved on this phone · not uploaded"
                            "pushed" -> "Uploaded"
                            else -> "Needs a decision"
                        }
                        val button = when (session.row.status) {
                            "in_progress" -> "Resume"
                            "finished" -> "Review & upload"
                            else -> "Open"
                        }
                        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            Surface(shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
                                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                    Text(session.row.title, style = MaterialTheme.typography.displaySmall)
                                    Text(status, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Button(onClick = { focus = focus.copy(focused = true) }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(button) }
                                }
                            }
                            CoachNotesCard(coachNotesState, session.row.title, session.exercises.map { it.row.exerciseId }.toSet(), coachExpanded, { coachExpanded = it }, coachNotes::markSeen, coachNotes::toggleDone)
                            AttentionRows(progressState, { nav = nav.select(RootTab.PROGRESS) }, openCheckIn)
                        }
                    } else holder.SaveableStateProvider("workout") {
                        WorkoutScreen(workout, progressState, coachNotesState, coachExpanded, { coachExpanded = it }, coachNotes::markSeen, coachNotes::toggleDone, { nav = nav.select(RootTab.PROGRESS) }, openCheckIn, signedOut) { nav = nav.push("settings") }
                    }
                    RootTab.HISTORY -> holder.SaveableStateProvider("history") {
                        HistoryScreen(historyState, { nav = nav.push("workout:$it") }, { nav = nav.push("exercise:$it") })
                    }
                    RootTab.PROGRESS -> holder.SaveableStateProvider("progress") {
                        ProgressScreen(progressState, { nav = nav.push("body") }, progress::addCheckIn) { nav = nav.push("exercise:$it") }
                    }
                }
            }
        }
    }
}
