package com.repknot.app

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay
import com.repknot.app.ui.HomeScreen
import com.repknot.app.ui.SettingsButton
import com.repknot.app.ui.WavyLinearProgress
import com.repknot.app.ui.coachNoteStyle
import com.repknot.domain.CoachNoteItem
import com.repknot.data.ExerciseRow
import com.repknot.data.SessionExerciseRow
import com.repknot.data.SessionRow
import com.repknot.data.SetRow
import com.repknot.data.SyncStep
import com.repknot.domain.LoadUnit
import com.repknot.domain.Prefill
import com.repknot.domain.SetDelta
import java.time.LocalDate

@Composable
fun WorkoutScreen(vm: WorkoutViewModel, progress: ProgressUiState, coachNotes: CoachNotesUiState, expanded: Boolean, setExpanded: (Boolean) -> Unit, markSeen: () -> Unit, toggleDone: (Int) -> Unit, openProgress: () -> Unit, openCheckIn: () -> Unit, signedOut: Boolean, openSettings: () -> Unit) {
    val state by vm.state.collectAsState()
    val error by vm.error.collectAsState()
    val session = state as? WorkoutUiState.Session
    val preview = state as? WorkoutUiState.Preview
    var editing by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(session?.row?.id, session?.row?.status, preview?.workout?.key) {
        if (session?.row?.status != "in_progress" && preview == null) editing = false
    }
    val overrides by vm.restOverrides.collectAsState()
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(session?.row?.id, session?.row?.status, lifecycle) {
        if (session?.row?.status == "in_progress") lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) { now = System.currentTimeMillis(); delay(1000) }
        }
    }
    // End-of-rest alert: rescheduled whenever the newest logged set or its target changes,
    // cancelled when there is no running rest (finished, discarded, no sets, past target).
    val alert = session?.let { restAlertPlan(it, overrides) }
    val alertText = alert?.let { stringResource(R.string.rest_alert_text, it.exerciseName, restText(it.targetS.toLong())) }
    LaunchedEffect(alert, alertText) {
        if (alert != null && alertText != null && alert.atMillis > System.currentTimeMillis())
            RestAlert.schedule(context, alert.atMillis, alertText)
        else RestAlert.cancel(context)
    }
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    LaunchedEffect(session?.row?.status == "in_progress") {
        if (session?.row?.status == "in_progress" && Build.VERSION.SDK_INT >= 33 &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
    Scaffold(bottomBar = {
        if (session != null && !editing) SessionActions(session, vm, { now }, overrides)
        if (session != null && editing) RestStrip(session, vm, { now }, overrides)
        if (preview != null && !editing) Surface(color = MaterialTheme.colorScheme.surfaceContainer, tonalElevation = 3.dp) {
            Button(onClick = { vm.start(preview.workout.key) }, modifier = Modifier.fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 12.dp)) {
                Text(stringResource(R.string.start_workout))
            }
        }
    }) { padding ->
        if (editing && (preview != null || session?.row?.status == "in_progress")) {
            WorkoutEditor(session, preview, vm, Modifier.padding(padding).consumeWindowInsets(padding)) { editing = false }
        } else Column(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding).imePadding().verticalScroll(rememberScrollState()).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            if (session == null && preview != null) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                SettingsButton(signedOut, openSettings)
            }
            error?.let { Text(stringResource(it), color = MaterialTheme.colorScheme.error) }
            when (val current = state) {
                WorkoutUiState.Loading -> CircularProgressIndicator()
                WorkoutUiState.NoProgram -> Text(stringResource(R.string.no_program))
                is WorkoutUiState.Today -> HomeScreen(current, progress, coachNotes, expanded, setExpanded, markSeen, toggleDone,
                    onPreview = vm::preview, onStart = vm::start, openProgress = openProgress, openCheckIn = openCheckIn)
                is WorkoutUiState.Session -> SessionScreen(current, vm, coachNotes, { editing = true }) { now }
                is WorkoutUiState.Preview -> PreviewScreen(current, vm, coachNotes) { editing = true }
            }
        }
    }
}

@Composable
private fun PreviewScreen(preview: WorkoutUiState.Preview, vm: WorkoutViewModel, notes: CoachNotesUiState, edit: () -> Unit) {
    BackHandler(onBack = vm::closePreview)
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
        Column(Modifier.weight(1f)) {
            if (preview.suggested) Text(stringResource(R.string.next_workout), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(preview.workout.name, style = MaterialTheme.typography.headlineMedium)
        }
        Column(horizontalAlignment = Alignment.End) {
            TextButton(onClick = edit) { Text(stringResource(R.string.edit)) }
            Text(pluralStringResource(R.plurals.preview_exercises, preview.exercises.size, preview.exercises.size),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    preview.exercises.forEachIndexed { index, exercise ->
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            HorizontalDivider()
            Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text((index + 1).toString().padStart(2, '0'), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium)
                Text(exercise.prescription.exerciseName, style = MaterialTheme.typography.titleSmall)
            }
            notes.activeItemsFor(exercise.prescription.exerciseId).firstOrNull()?.let { CoachNoteMarker(it) }
            val last = previewLast(exercise.previous)
            val date = previewDate(exercise.previousOn, LocalDate.now())
            BoxWithConstraints {
                val stacked = maxWidth.value / LocalDensity.current.fontScale < 250f
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (!stacked) PreviewTableRow("", PreviewValues(stringResource(R.string.preview_sets_reps), stringResource(R.string.label_rir), stringResource(R.string.label_load)), "", header = true)
                    val row = preview.rows.getOrNull(index)
                    // An exercise added in the editor has no prescription: show dashes, not "0 × –".
                    val plan = previewPlan(exercise.prescription).let { if (row != null && row.plannedSets == null) it.copy(reps = "–", rir = "–") else it }
                    PreviewTableRow(stringResource(R.string.preview_plan),
                        if (row?.substitutedFor != null) plan.copy(load = "–") else plan, "", stacked = stacked)
                    if (last.isEmpty()) Text(stringResource(R.string.no_previous_session), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    else last.forEachIndexed { i, values ->
                        PreviewTableRow(stringResource(R.string.preview_last), values, if (i == 0) date else "", stacked = stacked)
                    }
                }
            }
        }
    }
}

@Composable
private fun PreviewTableRow(label: String, values: PreviewValues, date: String, stacked: Boolean = false, header: Boolean = false) {
    val color = if (header) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
    val style = if (header) MaterialTheme.typography.labelSmall else MaterialTheme.typography.bodySmall
    if (stacked) {
        Column(Modifier.fillMaxWidth()) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(label, style = style, color = color)
                Text(date, style = style, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text("${stringResource(R.string.preview_sets_reps)}: ${values.reps}", style = style, color = color)
            Text("${stringResource(R.string.label_rir)}: ${values.rir}", style = style, color = color)
            Text("${stringResource(R.string.label_load)}: ${values.load}", style = style, color = color)
        }
    } else Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(label, Modifier.weight(0.75f), style = style, color = color)
        Text(values.reps, Modifier.weight(1.8f), style = style, color = color)
        Text(values.rir, Modifier.weight(1.1f), style = style, color = color)
        Text(values.load, Modifier.weight(1.25f), style = style, color = color)
        Text(date, Modifier.weight(1.1f), style = style, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = androidx.compose.ui.text.style.TextAlign.End)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SessionScreen(session: WorkoutUiState.Session, vm: WorkoutViewModel, notes: CoachNotesUiState, edit: () -> Unit, now: () -> Long) {
    var picker by rememberSaveable(session.row.id) { mutableStateOf<String?>(null) }
    var selected by rememberSaveable(session.row.id) { mutableStateOf<String?>(null) }
    var showAll by rememberSaveable(session.row.id) { mutableStateOf(false) }
    val holder = rememberSaveableStateHolder()
    val selectedId = selectedExerciseId(selected, session.exercises.map { ExerciseCompletion(it.row.id, it.row.plannedSets, it.sets.size) },
        newestTimedSet(session.exercises.flatMap { it.sets })?.sessionExerciseId)
    LaunchedEffect(selectedId) { selected = selectedId }
    val index = session.exercises.indexOfFirst { it.row.id == selectedId }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(session.row.title, modifier = Modifier.weight(1f), style = MaterialTheme.typography.headlineMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            ElapsedText(session.row, now)
            if (index >= 0) Text(stringResource(R.string.exercise_position, index + 1, session.exercises.size),
                style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            WorkoutMenu(session, vm, session.exercises.getOrNull(index), edit) { picker = it }
        }
    }
    if (session.row.light) Text(stringResource(R.string.light_day), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
    if (index >= 0) {
        val exercise = session.exercises[index]
        holder.SaveableStateProvider(exercise.row.id) {
            ExerciseCard(exercise, session.choices, vm, notes)
        }
        FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            TextButton(enabled = index > 0, onClick = {
                session.exercises.getOrNull(index - 1)?.let { selected = it.row.id; showAll = false }
            }) { Text(stringResource(R.string.previous_exercise)) }
            TextButton(onClick = { showAll = !showAll }) { Text(stringResource(if (showAll) R.string.hide_exercises else R.string.view_all_exercises)) }
            TextButton(enabled = index < session.exercises.lastIndex, onClick = {
                session.exercises.getOrNull(index + 1)?.let { selected = it.row.id; showAll = false }
            }) { Text(stringResource(R.string.next_exercise)) }
        }
        session.exercises.getOrNull(index + 1)?.let { next ->
            Column(Modifier.fillMaxWidth().clickable(role = Role.Button,
                onClickLabel = stringResource(R.string.open_exercise, next.row.exerciseName),
                onClick = { selected = next.row.id; showAll = false }).padding(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.up_next), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(next.row.exerciseName, style = MaterialTheme.typography.titleMedium)
                notes.activeItemsFor(next.row.exerciseId).firstOrNull()?.let { CoachNoteMarker(it) }
                Prescription(next.row)
                PreviousHint(next.previous, next.previousOn)
            }
        }
    }
    if (index < 0) TextButton(onClick = { showAll = !showAll }) { Text(stringResource(if (showAll) R.string.hide_exercises else R.string.view_all_exercises)) }
    if (showAll) session.exercises.forEach { exercise ->
        TextButton(onClick = { selected = exercise.row.id; showAll = false }, modifier = Modifier.fillMaxWidth()) {
            Text("${exercise.row.exerciseName} · ${exercise.sets.size}${exercise.row.plannedSets?.let { " / $it" } ?: ""}")
        }
    }
    if (showAll && session.row.status == "in_progress") TextButton(onClick = edit, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.edit_workout))
    }
    if (picker != null) ExercisePicker(session.choices, session.exercises.map { it.row.exerciseId }.toSet(), onDismiss = { picker = null }) { id ->
        picker?.let { vm.replace(it, id) }
        picker = null
    }
}

/** Reads the ticking clock here only, so the rest of the session screen does not recompose every second. */
@Composable
private fun ElapsedText(row: SessionRow, now: () -> Long) {
    elapsedWorkoutText(row.startedAt, now(), row.status)?.let {
        Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RestLine(session: WorkoutUiState.Session, vm: WorkoutViewModel, now: () -> Long, overrides: Map<String, Int>) {
    val rest = restState(now(), session.row.status, session.exercises.flatMap { it.sets })
    val restedExercise = session.exercises.firstOrNull { it.row.id == rest?.sessionExerciseId }
    if (rest == null || restedExercise == null) return
    val target = restTargetS(restedExercise.row.exerciseId, restedExercise.programRestS, overrides)
    val description = stringResource(R.string.rest_target_description, restedExercise.row.exerciseName)
    Column(Modifier.fillMaxWidth()) {
        FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalArrangement = Arrangement.Center) {
            Text(stringResource(R.string.rest_elapsed, restText(rest.elapsedS)),
                color = if (rest.elapsedS >= target) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelMedium, modifier = Modifier.align(Alignment.CenterVertically))
            TextButton(onClick = { vm.toggleRestTarget(restedExercise.row.exerciseId, target) },
                modifier = Modifier.semantics { contentDescription = description },
                contentPadding = PaddingValues(horizontal = 8.dp)) { Text(restText(target.toLong())) }
        }
        WavyLinearProgress(progress = { restProgress(rest.elapsedS, target) },
            modifier = Modifier.fillMaxWidth(),
            color = if (rest.elapsedS >= target) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.surfaceContainerHighest)
    }
}

@Composable
private fun RestStrip(session: WorkoutUiState.Session, vm: WorkoutViewModel, now: () -> Long, overrides: Map<String, Int>) {
    // Read the clock only here, so the rest of the footer does not tick.
    val rest = restState(now(), session.row.status, session.exercises.flatMap { it.sets })
    if (rest != null && session.exercises.any { it.row.id == rest.sessionExerciseId }) {
        Surface(Modifier.imePadding(), color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                RestLine(session, vm, now, overrides)
            }
        }
    }
}

@Composable
private fun SessionActions(session: WorkoutUiState.Session, vm: WorkoutViewModel, now: () -> Long, overrides: Map<String, Int>) {
    var confirmDiscard by rememberSaveable(session.row.id) { mutableStateOf(false) }
    var confirmFinish by rememberSaveable(session.row.id) { mutableStateOf(false) }
    val keyboardOpen = WindowInsets.ime.getBottom(LocalDensity.current) > 0
    if (keyboardOpen) {
        RestStrip(session, vm, now, overrides)
    } else Surface(color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding()) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                RestLine(session, vm, now, overrides)
                FinishSection(session.row, vm, { confirmDiscard = true }) { confirmFinish = true }
            }
        }
    }
    // Finish sits at the bottom of a footer whose height changes, so one stray tap must not end the workout.
    if (confirmFinish && session.row.status == "in_progress") AlertDialog(
        onDismissRequest = { confirmFinish = false }, title = { Text(stringResource(R.string.finish_confirm_title)) },
        text = { Text(pluralStringResource(R.plurals.finish_confirm_message, session.exercises.sumOf { it.sets.size },
            session.exercises.sumOf { it.sets.size })) },
        confirmButton = { TextButton(onClick = { vm.finish(); confirmFinish = false }) { Text(stringResource(R.string.finish)) } },
        dismissButton = { TextButton(onClick = { confirmFinish = false }) { Text(stringResource(R.string.cancel)) } },
    )
    if (confirmDiscard && session.row.discardable()) DiscardDialog(vm) { confirmDiscard = false }
}

/** The logger's only ⋯, with editing and workout actions. */
@Composable
private fun WorkoutMenu(session: WorkoutUiState.Session, vm: WorkoutViewModel, current: ExerciseUi?, edit: () -> Unit, substitute: (String) -> Unit) {
    var menuOpen by rememberSaveable(session.row.id) { mutableStateOf(false) }
    var notesOpen by rememberSaveable(session.row.id) { mutableStateOf(false) }
    var confirmDiscard by rememberSaveable(session.row.id) { mutableStateOf(false) }
    var notes by rememberSaveable(session.row.id) { mutableStateOf(session.row.notes.orEmpty()) }
    IconButton(onClick = { menuOpen = true }) {
        Icon(painterResource(R.drawable.ic_more_vert), contentDescription = stringResource(R.string.workout_menu))
    }
    if (menuOpen) WorkoutOptionsSheet(session, current, vm,
        onSubstitute = { menuOpen = false; substitute(it) },
        onEdit = { menuOpen = false; edit() },
        onNotes = { menuOpen = false; notes = session.row.notes.orEmpty(); notesOpen = true },
        onDiscard = { menuOpen = false; confirmDiscard = true }) { menuOpen = false }
    if (confirmDiscard && session.row.discardable()) DiscardDialog(vm) { confirmDiscard = false }
    if (notesOpen) AlertDialog(onDismissRequest = { notesOpen = false }, title = { Text(stringResource(R.string.session_notes)) },
        text = { OutlinedTextField(notes, { notes = it }, modifier = Modifier.fillMaxWidth()) },
        confirmButton = { TextButton(onClick = { vm.saveSessionNotes(notes); notesOpen = false }) { Text(stringResource(R.string.save_notes)) } },
        dismissButton = { TextButton(onClick = { notesOpen = false }) { Text(stringResource(R.string.cancel)) } })
}

@Composable
private fun DiscardDialog(vm: WorkoutViewModel, dismiss: () -> Unit) = AlertDialog(
    onDismissRequest = dismiss, title = { Text(stringResource(R.string.discard_confirm_title)) },
    text = { Text(stringResource(R.string.discard_confirm_message)) },
    confirmButton = { TextButton(onClick = { vm.discard(); dismiss() }) { Text(stringResource(R.string.discard)) } },
    dismissButton = { TextButton(onClick = dismiss) { Text(stringResource(R.string.cancel)) } },
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ExerciseCard(exercise: ExerciseUi, choices: List<ExerciseRow>, vm: WorkoutViewModel, notes: CoachNotesUiState) {
    val row = exercise.row
    var editing by rememberSaveable(row.id) { mutableStateOf<String?>(null) }
    // An update succeeded when the edited set's values change in state: leave edit mode then.
    val edited = exercise.sets.firstOrNull { it.id == editing }
    var editStart by remember(editing) { mutableStateOf(edited) }
    LaunchedEffect(edited) { if (editing != null && edited != editStart) { editing = null; editStart = null } }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            // Exercise actions live in the header menu (task 039: one ⋯ per screen).
            Text(row.exerciseName, style = MaterialTheme.typography.titleLarge)
            notes.activeItemsFor(row.exerciseId).forEach { item -> key(item.index) { CoachNoteBox(item, row.id, notes.note?.id.orEmpty()) } }
            row.substitutedFor?.let { id ->
                Text(stringResource(R.string.instead_of, choices.firstOrNull { it.id == id }?.name
                    ?: stringResource(R.string.prescribed_exercise)))
            }
            BoxWithConstraints {
                val stacked = maxWidth.value / LocalDensity.current.fontScale < 250f
                val plan = PreviewValues("${row.plannedSets?.toString() ?: "–"} × ${range(row.plannedRepMin, row.plannedRepMax) ?: "–"}",
                    range(row.plannedRirMin, row.plannedRirMax) ?: "–", sessionPlanLoad(row.plannedLoad, row.plannedUnit, row.substitutedFor != null) ?: "–")
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (!stacked) PreviewTableRow("", PreviewValues(stringResource(R.string.preview_sets_reps), stringResource(R.string.label_rir), stringResource(R.string.label_load)), "", header = true)
                    PreviewTableRow(stringResource(R.string.preview_plan), plan, "", stacked = stacked)
                    val last = previewLast(exercise.previous)
                    if (last.isEmpty()) Text(stringResource(R.string.no_previous_session), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    else last.forEachIndexed { i, values -> PreviewTableRow(stringResource(R.string.preview_last), values, if (i == 0) previewDate(exercise.previousOn, LocalDate.now()) else "", stacked = stacked) }
                }
            }
            HorizontalDivider()
            Text(stringResource(R.string.logged_sets, exercise.sets.size.toString() + (row.plannedSets?.let { " / $it" } ?: "")), style = MaterialTheme.typography.titleSmall)
            exercise.sets.forEachIndexed { setIndex, set ->
                Column(Modifier.fillMaxWidth()) {
                    val description = buildString {
                        append("${setIndex + 1}. ${formatSet(set)}")
                        set.rir?.let { append(" · RIR $it") }
                        set.notes?.let { append(" — $it") }
                    }
                    var setMenu by remember(set.id) { mutableStateOf(false) }
                    val details: @Composable (Modifier) -> Unit = { modifier ->
                        FlowRow(modifier, horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.Center) {
                            Text(description)
                            DeltaHint(exercise.deltas[setIndex])
                        }
                    }
                    val actions: @Composable () -> Unit = {
                        TextButton(onClick = { editing = set.id }) { Text(stringResource(R.string.edit)) }
                        Box {
                            val label = stringResource(R.string.set_menu, setIndex + 1)
                            IconButton(onClick = { setMenu = true }, modifier = Modifier.semantics { contentDescription = label }) { Text("⋯") }
                            DropdownMenu(expanded = setMenu, onDismissRequest = { setMenu = false }) {
                                DropdownMenuItem(text = { Text(stringResource(R.string.delete)) }, onClick = { setMenu = false; vm.deleteSet(set.id); if (editing == set.id) editing = null })
                            }
                        }
                    }
                    BoxWithConstraints(Modifier.fillMaxWidth()) {
                        if (maxWidth.value / LocalDensity.current.fontScale < 240f) {
                            Column {
                                details(Modifier.fillMaxWidth())
                                Row(verticalAlignment = Alignment.CenterVertically) { actions() }
                            }
                        } else {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                                details(Modifier.weight(1f))
                                actions()
                            }
                        }
                    }
                }
            }
            HorizontalDivider()
            Text(stringResource(if (editing == null) R.string.next_set else R.string.edit_set), style = MaterialTheme.typography.titleSmall)
            SetInput(exercise, exercise.sets.firstOrNull { it.id == editing }, vm) { editing = null }
            var notes by rememberSaveable(row.id) { mutableStateOf(row.notes.orEmpty()) }
            var notesExpanded by rememberSaveable(row.id) { mutableStateOf(false) }
            TextButton(onClick = { notesExpanded = !notesExpanded }) { Text(notesLabel(row.notes)) }
            if (notesExpanded) {
                OutlinedTextField(notes, { notes = it }, label = { Text(stringResource(R.string.exercise_notes)) }, modifier = Modifier.fillMaxWidth())
                OutlinedButton(onClick = { vm.saveExerciseNotes(row.id, notes) }) { Text(stringResource(R.string.save_notes)) }
            }
    }
}

@Composable
private fun CoachNoteBox(item: CoachNoteItem, sessionExerciseId: String, noteId: String) {
    var expanded by rememberSaveable(sessionExerciseId, noteId, item.index) { mutableStateOf(false) }
    val style = coachNoteStyle(item.kind)
    val description = stringResource(if (expanded) R.string.coach_expanded else R.string.coach_collapsed)
    Surface(shape = RoundedCornerShape(8.dp), color = style.container,
        modifier = Modifier.fillMaxWidth().semantics { stateDescription = description }.clickable(role = Role.Button) { expanded = !expanded }) {
        Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
            Icon(painterResource(style.icon), stringResource(style.label), Modifier.size(20.dp), tint = style.tint)
            Text(item.text, style = MaterialTheme.typography.bodyMedium, maxLines = if (expanded) Int.MAX_VALUE else 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun CoachNoteMarker(item: CoachNoteItem) {
    val style = coachNoteStyle(item.kind)
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.Top) {
        Icon(painterResource(style.icon), stringResource(style.label), Modifier.size(16.dp), tint = style.tint)
        Text(item.text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun notesLabel(notes: String?): String = stringResource(R.string.notes) +
    if (notes.isNullOrEmpty()) "" else " · ${notes.take(30)}${if (notes.length > 30) "…" else ""}"

private fun number(value: Double?): String = value?.let { if (it % 1.0 == 0.0) it.toLong().toString() else it.toString() }.orEmpty()
private fun range(min: Int?, max: Int?): String? = when {
    min == null -> max?.toString()
    max == null || min == max -> min.toString()
    else -> "$min–$max"
}

@Composable
private fun Prescription(row: SessionExerciseRow) {
    PrescriptionLine(row.plannedSets, row.plannedRepMin, row.plannedRepMax, row.plannedRirMin,
        row.plannedRirMax, sessionPlanLoad(row.plannedLoad, row.plannedUnit, row.substitutedFor != null))
}

@Composable
private fun PrescriptionLine(sets: Int?, repMin: Int?, repMax: Int?, rirMin: Int?, rirMax: Int?, load: String?) {
    val parts = listOfNotNull(
        sets?.let { pluralStringResource(R.plurals.prescription_sets, it, it) },
        range(repMin, repMax)?.let { stringResource(R.string.prescription_reps, it) },
        range(rirMin, rirMax)?.let { stringResource(R.string.prescription_rir, it) },
        load,
    )
    if (parts.isNotEmpty()) Text(parts.joinToString(" · "), color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun PreviousHint(previous: Prefill, previousOn: LocalDate?) {
    if (previous is Prefill.Previous && previousOn != null) {
        Text(lastLabel(previousOn, LocalDate.now()), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(previousSummary(previous.sets), color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun DeltaHint(delta: SetDelta) {
    val (symbol, label, color) = when (delta) {
        SetDelta.UP -> Triple("▲", R.string.delta_better, MaterialTheme.colorScheme.primary)
        SetDelta.SAME -> Triple("=", R.string.delta_same, MaterialTheme.colorScheme.onSurfaceVariant)
        SetDelta.DOWN -> Triple("▼", R.string.delta_below, MaterialTheme.colorScheme.error)
        SetDelta.NONE -> return
    }
    val description = stringResource(label)
    Text(symbol, color = color, modifier = Modifier.semantics { contentDescription = description })
}

@Composable
private fun SetInput(exercise: ExerciseUi, set: SetRow?, vm: WorkoutViewModel, cancel: () -> Unit) {
    val focusManager = LocalFocusManager.current
    val suggestion = exercise.suggestion
    // Primitive keys are saveable and represent all fields of the suggestion.
    val keys = arrayOf<Any?>(exercise.row.id, exercise.sets.size, set?.id, suggestion.load, suggestion.unit.name, suggestion.reps)
    var load by rememberSaveable(*keys) { mutableStateOf(number(set?.load ?: if (set == null) suggestion.load else null)) }
    var reps by rememberSaveable(*keys) { mutableStateOf(number(set?.reps ?: if (set == null) suggestion.reps else null)) }
    var rir by rememberSaveable(*keys) { mutableIntStateOf(set?.rir ?: 1) }
    val rirMax = maxOf(10, set?.rir ?: 1)
    val unit = set?.unit?.uppercase()?.let(LoadUnit::valueOf) ?: suggestion.unit
    if (set == null && exercise.previous is Prefill.Previous) Text(stringResource(R.string.suggested_previous), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        if (unit != LoadUnit.BODYWEIGHT) TextField(load, { load = it }, modifier = Modifier.weight(1f), label = { Text(if (set != null && set.unit == null) stringResource(R.string.label_load) else "${stringResource(R.string.label_load)} · ${unit.name.lowercase()}") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next), keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Next) }))
        TextField(reps, { reps = it }, modifier = Modifier.weight(1f), label = { Text(stringResource(R.string.label_reps)) }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done), keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }))
    }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(R.string.label_rir), modifier = Modifier.weight(1f))
        IconButton(onClick = { rir-- }, enabled = rir > 0, modifier = Modifier.size(48.dp).semantics { contentDescription = "Decrease RIR" }) { Text("−") }
        Text(rir.toString(), modifier = Modifier.padding(horizontal = 12.dp))
        IconButton(onClick = { rir++ }, enabled = rir < rirMax, modifier = Modifier.size(48.dp).semantics { contentDescription = "Increase RIR" }) { Text("+") }
    }
    Button(onClick = { focusManager.clearFocus(); vm.logSet(exercise.row.id, set?.id, load, unit, reps, rir.toString()) }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(if (set == null) R.string.log_set else R.string.update_set)) }
    if (set != null) TextButton(onClick = cancel) { Text(stringResource(R.string.cancel)) }
}

@Composable
internal fun ExercisePicker(choices: List<ExerciseRow>, inWorkout: Set<String>, onDismiss: () -> Unit, onChoose: (String) -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(stringResource(R.string.choose_exercise)) }, text = {
        Column {
            OutlinedTextField(query, { query = it }, label = { Text(stringResource(R.string.search)) }, singleLine = true)
            Column(Modifier.heightIn(max = 400.dp).verticalScroll(rememberScrollState())) {
                choices.filter { it.name.contains(query, ignoreCase = true) }.forEach { choice ->
                    TextButton(onClick = { onChoose(choice.id) }, modifier = Modifier.fillMaxWidth()) { Text(choice.name + if (choice.id in inWorkout) stringResource(R.string.in_workout) else "") }
                }
            }
        }
    }, confirmButton = {}, dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } })
}

@Composable
private fun FinishSection(row: SessionRow, vm: WorkoutViewModel, requestDiscard: () -> Unit, requestFinish: () -> Unit) {
    val status = row.status
    val uploadStep by vm.uploadStep.collectAsState()
    val context = LocalContext.current
    val shareTitle = stringResource(R.string.share_workout)
    val clipboardLabel = stringResource(R.string.clipboard_workout)
    // Full width with even button pairs in every state, so nothing collapses to one side (task 041).
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        val statusStyle = MaterialTheme.typography.bodyMedium
        val statusColor = MaterialTheme.colorScheme.onSurfaceVariant
        when (status) {
            "in_progress" -> {
                OutlinedButton(onClick = requestFinish, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.finish_workout)) }
            }
            "finished" -> {
                val step = uploadStep
                if (step != null) UploadProgress(step)
                else {
                    // Nothing reaches the server without this tap (task 028).
                    Text(stringResource(if (row.uploadRequested) R.string.upload_pending else R.string.saved_not_uploaded), style = statusStyle, color = statusColor)
                    Button(onClick = vm::upload, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(if (row.uploadRequested) R.string.retry_upload else R.string.upload))
                    }
                }
                val neverUploaded = row.number == null && row.baseRevision == null
                ButtonPair(
                    first = if (neverUploaded && step == null) ({ OutlinedButton(onClick = vm::resume, modifier = it) { Text(stringResource(R.string.resume_workout)) } }) else null,
                    second = when {
                        row.discardable() && step == null -> ({ OutlinedButton(onClick = requestDiscard, modifier = it) { Text(stringResource(R.string.discard)) } })
                        row.uploadRequested -> ({ OutlinedButton(onClick = vm::done, modifier = it) { Text(stringResource(R.string.done)) } })
                        else -> null
                    },
                )
            }
            "pushed" -> {
                Text(stringResource(R.string.sync_uploaded), style = statusStyle, color = MaterialTheme.colorScheme.primary)
                Button(onClick = vm::done, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.done)) }
            }
            "conflict", "conflict_deleted" -> {
                Text(stringResource(if (status == "conflict") R.string.sync_conflict else R.string.sync_deleted_conflict), style = statusStyle)
                ButtonPair(
                    { Button(onClick = vm::keepMine, modifier = it) { Text(stringResource(R.string.keep_mine)) } },
                    { OutlinedButton(onClick = vm::takeTheirs, modifier = it) { Text(stringResource(R.string.take_theirs)) } },
                )
            }
        }
        if (status != "in_progress") ButtonPair(
            { OutlinedButton(onClick = { vm.share { text ->
                val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
                context.startActivity(Intent.createChooser(send, shareTitle))
            } }, modifier = it) { Text(stringResource(R.string.share)) } },
            { OutlinedButton(onClick = { vm.share { text ->
                context.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText(clipboardLabel, text))
            } }, modifier = it) { Text(stringResource(R.string.copy)) } },
        )
    }
}

/** Up to two buttons sharing the full width evenly; one alone fills it. */
@Composable
private fun ButtonPair(first: (@Composable (Modifier) -> Unit)?, second: (@Composable (Modifier) -> Unit)?) {
    if (first == null && second == null) return
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        first?.invoke(Modifier.weight(1f))
        second?.invoke(Modifier.weight(1f))
    }
}

/** Live upload state: what the sync is doing now, with a bar that advances step by step. */
@Composable
private fun UploadProgress(step: SyncStep) {
    val label = stringResource(when (step) {
        SyncStep.ACCOUNT -> R.string.upload_step_account
        SyncStep.REFERENCE -> R.string.upload_step_reference
        SyncStep.HISTORY -> R.string.upload_step_history
        SyncStep.CHECK_INS -> R.string.upload_step_check_ins
        SyncStep.WORKOUTS -> R.string.upload_step_workout
    })
    Column(Modifier.fillMaxWidth().semantics(mergeDescendants = true) {}, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(stringResource(R.string.uploading), style = MaterialTheme.typography.titleSmall)
            Text(stringResource(R.string.upload_step_count, step.ordinal + 1, SyncStep.entries.size),
                style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        WavyLinearProgress(progress = { uploadFraction(step) }, modifier = Modifier.fillMaxWidth(),
            trackColor = MaterialTheme.colorScheme.surfaceContainerHighest)
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Mirrors SessionDao.discard: the workout never left the phone and Upload was not tapped. */
private fun SessionRow.discardable(): Boolean =
    (status == "in_progress" || status == "finished") && !uploadRequested && number == null && baseRevision == null

@Composable
private fun MenuItem(icon: Int, text: String, onClick: () -> Unit, supporting: String? = null,
                     color: Color = Color.Unspecified, trailing: (@Composable () -> Unit)? = null) = ListItem(
    headlineContent = { Text(text, color = color) },
    supportingContent = supporting?.let { { Text(it, maxLines = 1, overflow = TextOverflow.Ellipsis) } },
    leadingContent = { Icon(painterResource(icon), contentDescription = null,
        tint = if (color == Color.Unspecified) MaterialTheme.colorScheme.onSurfaceVariant else color) },
    trailingContent = trailing,
    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    modifier = Modifier.clickable(role = Role.Button, onClick = onClick).padding(horizontal = 8.dp),
)

@Composable
private fun MenuSection(title: String, subtitle: String? = null) =
    Row(Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 4.dp), verticalAlignment = Alignment.Bottom) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary,
            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
        subtitle?.let { Text("  $it", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }

/** Bottom sheet behind the header ⋯ (owner redesign of the dropdown; task 039 merged the exercise menu in). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WorkoutOptionsSheet(session: WorkoutUiState.Session, current: ExerciseUi?, vm: WorkoutViewModel,
                                onSubstitute: (String) -> Unit, onEdit: () -> Unit,
                                onNotes: () -> Unit, onDiscard: () -> Unit, onDismiss: () -> Unit) {
    val row = session.row
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).navigationBarsPadding().padding(bottom = 16.dp)) {
            if (row.status == "in_progress") {
                MenuItem(R.drawable.ic_reorder, stringResource(R.string.edit_workout), onEdit)
                current?.let { exercise ->
                    val enabled = exercise.sets.isEmpty()
                    ListItem(headlineContent = { Text(stringResource(R.string.replace_current, exercise.row.exerciseName)) },
                        supportingContent = if (enabled) null else { { Text(stringResource(R.string.has_logged_sets)) } },
                        leadingContent = { Icon(painterResource(R.drawable.ic_swap), contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        modifier = Modifier.clickable(enabled = enabled, role = Role.Button) { onSubstitute(exercise.row.id) }.padding(horizontal = 8.dp))
                }
                HorizontalDivider(Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
            }
            MenuItem(R.drawable.ic_notes, stringResource(R.string.session_notes), onNotes, supporting = row.notes?.takeIf { it.isNotBlank() })
            ListItem(
                headlineContent = { Text(stringResource(R.string.light_day)) },
                supportingContent = { Text(stringResource(R.string.light_day_description)) },
                leadingContent = { Icon(painterResource(R.drawable.ic_light_day), contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                trailingContent = { Switch(checked = row.light, onCheckedChange = null) },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                modifier = Modifier.toggleable(value = row.light, role = Role.Switch, onValueChange = vm::setLight)
                    .padding(horizontal = 8.dp),
            )
            if (row.status == "in_progress" && row.discardable()) {
                HorizontalDivider(Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
                MenuItem(R.drawable.ic_delete, stringResource(R.string.discard_workout), onDiscard, color = MaterialTheme.colorScheme.error)
            }
        }
    }
}
