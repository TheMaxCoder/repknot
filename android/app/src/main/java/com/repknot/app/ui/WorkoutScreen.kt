package com.repknot.app

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.repknot.data.ExerciseRow
import com.repknot.data.SessionExerciseRow
import com.repknot.data.SetRow
import com.repknot.domain.LoadUnit
import com.repknot.domain.Prefill
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Composable
fun WorkoutScreen(vm: WorkoutViewModel, openSettings: () -> Unit) {
    val state by vm.state.collectAsState()
    val error by vm.error.collectAsState()
    val session = state as? WorkoutUiState.Session
    Scaffold(bottomBar = { if (session != null) SessionActions(session, vm) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).imePadding().verticalScroll(rememberScrollState()).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                if (session != null) Text(runCatching { LocalDate.parse(session.row.performedOn).format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)) }.getOrDefault(session.row.performedOn), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                else Spacer(Modifier.weight(1f))
                TextButton(onClick = openSettings) { Text(stringResource(R.string.settings)) }
            }
            error?.let { Text(stringResource(it), color = MaterialTheme.colorScheme.error) }
            when (val current = state) {
                WorkoutUiState.Loading -> CircularProgressIndicator()
                WorkoutUiState.NoProgram -> Text(stringResource(R.string.no_program))
                is WorkoutUiState.Today -> {
                    Text(current.programName, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.titleSmall)
                    Text(stringResource(R.string.next_workout))
                    Text(current.suggested.name, style = MaterialTheme.typography.headlineMedium)
                    Button(onClick = { vm.preview(current.suggested.key) }, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.preview_workout, current.suggested.name))
                    }
                    Text(stringResource(R.string.other_workouts), style = MaterialTheme.typography.titleMedium)
                    current.workouts.filter { it.key != current.suggested.key }.forEach { workout ->
                        OutlinedButton(onClick = { vm.preview(workout.key) }, modifier = Modifier.fillMaxWidth()) { Text(workout.name) }
                    }
                }
                is WorkoutUiState.Session -> SessionScreen(current, vm)
                is WorkoutUiState.Preview -> PreviewScreen(current, vm)
            }
        }
    }
}

@Composable
private fun PreviewScreen(preview: WorkoutUiState.Preview, vm: WorkoutViewModel) {
    BackHandler(onBack = vm::closePreview)
    Text(preview.programName, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.titleSmall)
    if (preview.suggested) Text(stringResource(R.string.next_workout))
    Text(preview.workout.name, style = MaterialTheme.typography.headlineMedium)
    preview.exercises.forEach { exercise ->
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val prescription = exercise.prescription
                Text(prescription.exerciseName, style = MaterialTheme.typography.titleLarge)
                PrescriptionLine(prescription.sets, prescription.repMin, prescription.repMax,
                    prescription.rirMin, prescription.rirMax, prescription.load, prescription.unit.name.lowercase())
                PreviousHint(exercise.previous, exercise.previousOn)
            }
        }
    }
    Button(onClick = { vm.start(preview.workout.key) }, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.start_workout))
    }
    OutlinedButton(onClick = vm::closePreview, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.back)) }
}

@Composable
private fun SessionScreen(session: WorkoutUiState.Session, vm: WorkoutViewModel) {
    var picker by rememberSaveable(session.row.id) { mutableStateOf<String?>(null) }
    var selected by rememberSaveable(session.row.id) { mutableStateOf<String?>(null) }
    var showAll by rememberSaveable(session.row.id) { mutableStateOf(false) }
    val holder = rememberSaveableStateHolder()
    val selectedId = selectedExerciseId(selected, session.exercises.map { ExerciseProgress(it.row.id, it.row.plannedSets, it.sets.size) })
    LaunchedEffect(selectedId) { selected = selectedId }
    val index = session.exercises.indexOfFirst { it.row.id == selectedId }
    Text(session.row.title, style = MaterialTheme.typography.headlineMedium)
    if (index >= 0) {
        val exercise = session.exercises[index]
        holder.SaveableStateProvider(exercise.row.id) {
            ExerciseCard(exercise, session.choices, index, session.exercises.size, vm) { picker = exercise.row.id }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically) {
            TextButton(enabled = index > 0, onClick = {
                session.exercises.getOrNull(index - 1)?.let { selected = it.row.id; showAll = false }
            }) { Text(stringResource(R.string.previous_exercise)) }
            Text(stringResource(R.string.exercise_position, index + 1, session.exercises.size),
                style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                Prescription(next.row)
                PreviousHint(next.previous, next.previousOn)
            }
        }
    }
    TextButton(onClick = { showAll = !showAll }) { Text(stringResource(if (showAll) R.string.hide_exercises else R.string.view_all_exercises)) }
    if (showAll) session.exercises.forEach { exercise ->
        TextButton(onClick = { selected = exercise.row.id; showAll = false }, modifier = Modifier.fillMaxWidth()) {
            Text("${exercise.row.exerciseName} · ${exercise.sets.size}${exercise.row.plannedSets?.let { " / $it" } ?: ""}")
        }
    }
    if (picker != null) ExercisePicker(session.choices, onDismiss = { picker = null }) { id ->
        picker?.let { vm.substitute(it, id) }
        picker = null
    }
}

@Composable
private fun SessionActions(session: WorkoutUiState.Session, vm: WorkoutViewModel) {
    var picker by rememberSaveable(session.row.id) { mutableStateOf(false) }
    var notesOpen by rememberSaveable(session.row.id) { mutableStateOf(false) }
    var notes by rememberSaveable(session.row.id) { mutableStateOf(session.row.notes.orEmpty()) }
    val keyboardOpen = WindowInsets.ime.getBottom(LocalDensity.current) > 0
    if (!keyboardOpen) Surface(color = MaterialTheme.colorScheme.surfaceContainer, tonalElevation = 3.dp) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().imePadding().padding(horizontal = 16.dp, vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(Modifier.fillMaxWidth()) {
                TextButton(onClick = { picker = true }, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.add_exercise)) }
                TextButton(onClick = { notes = session.row.notes.orEmpty(); notesOpen = true }, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.session_notes)) }
            }
            FinishSection(session.row.status, vm)
        }
    }
    if (picker) ExercisePicker(session.choices, onDismiss = { picker = false }) { vm.addExercise(it); picker = false }
    if (notesOpen) AlertDialog(onDismissRequest = { notesOpen = false }, title = { Text(stringResource(R.string.session_notes)) },
        text = { OutlinedTextField(notes, { notes = it }, modifier = Modifier.fillMaxWidth()) },
        confirmButton = { TextButton(onClick = { vm.saveSessionNotes(notes); notesOpen = false }) { Text(stringResource(R.string.save_notes)) } },
        dismissButton = { TextButton(onClick = { notesOpen = false }) { Text(stringResource(R.string.cancel)) } })
}

@Composable
private fun ExerciseCard(exercise: ExerciseUi, choices: List<ExerciseRow>, index: Int, count: Int,
                         vm: WorkoutViewModel, substitute: () -> Unit) {
    val row = exercise.row
    var editing by rememberSaveable(row.id) { mutableStateOf<String?>(null) }
    var remove by rememberSaveable(row.id) { mutableStateOf(false) }
    // An update succeeded when the edited set's values change in state: leave edit mode then.
    val edited = exercise.sets.firstOrNull { it.id == editing }
    var editStart by remember(editing) { mutableStateOf(edited) }
    LaunchedEffect(edited) { if (editing != null && edited != editStart) { editing = null; editStart = null } }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            var menuExpanded by remember { mutableStateOf(false) }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(row.exerciseName, modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
                Box {
                    TextButton(onClick = { menuExpanded = true }) { Text("⋯") }
                    DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                        DropdownMenuItem(text = { Text(stringResource(R.string.move_up)) }, enabled = index > 0, onClick = { menuExpanded = false; vm.moveExercise(row.id, -1) })
                        DropdownMenuItem(text = { Text(stringResource(R.string.move_down)) }, enabled = index < count - 1, onClick = { menuExpanded = false; vm.moveExercise(row.id, 1) })
                        DropdownMenuItem(text = { Text(stringResource(R.string.substitute)) }, onClick = { menuExpanded = false; substitute() })
                        DropdownMenuItem(text = { Text(stringResource(R.string.remove)) }, onClick = { menuExpanded = false; remove = true })
                    }
                }
            }
            row.substitutedFor?.let { id ->
                Text(stringResource(R.string.instead_of, choices.firstOrNull { it.id == id }?.name
                    ?: stringResource(R.string.prescribed_exercise)))
            }
            Prescription(row)
            PreviousHint(exercise.previous, exercise.previousOn)
            exercise.sets.forEachIndexed { setIndex, set ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    val description = buildString {
                        append("${setIndex + 1}. ${formatSet(set)}")
                        set.rir?.let { append(" · RIR $it") }
                        set.notes?.let { append(" — $it") }
                    }
                    Text(description, modifier = Modifier.weight(1f))
                    TextButton(onClick = { editing = set.id }) { Text(stringResource(R.string.edit)) }
                    TextButton(onClick = { vm.deleteSet(set.id); if (editing == set.id) editing = null }) { Text(stringResource(R.string.delete)) }
                }
            }
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
    if (remove) AlertDialog(onDismissRequest = { remove = false }, title = { Text(stringResource(R.string.remove_exercise)) },
        text = { Text(stringResource(R.string.remove_exercise_confirm, row.exerciseName)) },
        confirmButton = { TextButton(onClick = { vm.removeExercise(row.id); remove = false }) { Text(stringResource(R.string.remove)) } },
        dismissButton = { TextButton(onClick = { remove = false }) { Text(stringResource(R.string.cancel)) } })
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
private fun loadText(load: Double?, unit: String?): String? = when (unit) {
    "bodyweight" -> "bodyweight"
    "plates" -> load?.let { "${number(it)}p" }
    else -> load?.let { listOf(number(it), unit).filterNotNull().joinToString(" ") }
}

@Composable
private fun Prescription(row: SessionExerciseRow) {
    PrescriptionLine(row.plannedSets, row.plannedRepMin, row.plannedRepMax, row.plannedRirMin,
        row.plannedRirMax, row.plannedLoad, row.plannedUnit)
}

@Composable
private fun PrescriptionLine(sets: Int?, repMin: Int?, repMax: Int?, rirMin: Int?, rirMax: Int?, load: Double?, unit: String?) {
    val parts = listOfNotNull(
        sets?.let { pluralStringResource(R.plurals.prescription_sets, it, it) },
        range(repMin, repMax)?.let { stringResource(R.string.prescription_reps, it) },
        range(rirMin, rirMax)?.let { stringResource(R.string.prescription_rir, it) },
        loadText(load, unit),
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
private fun SetInput(exercise: ExerciseUi, set: SetRow?, vm: WorkoutViewModel, cancel: () -> Unit) {
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
        if (unit != LoadUnit.BODYWEIGHT) OutlinedTextField(load, { load = it }, modifier = Modifier.weight(1f), label = { Text(if (set != null && set.unit == null) stringResource(R.string.label_load) else "${stringResource(R.string.label_load)} · ${unit.name.lowercase()}") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
        OutlinedTextField(reps, { reps = it }, modifier = Modifier.weight(1f), label = { Text(stringResource(R.string.label_reps)) }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
    }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(R.string.label_rir), modifier = Modifier.weight(1f))
        FilledTonalIconButton(onClick = { rir-- }, enabled = rir > 0, modifier = Modifier.size(48.dp).semantics { contentDescription = "Decrease RIR" }) { Text("−") }
        Text(rir.toString(), modifier = Modifier.padding(horizontal = 12.dp))
        FilledTonalIconButton(onClick = { rir++ }, enabled = rir < rirMax, modifier = Modifier.size(48.dp).semantics { contentDescription = "Increase RIR" }) { Text("+") }
    }
    Button(onClick = { vm.logSet(exercise.row.id, set?.id, load, unit, reps, rir.toString()) }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(if (set == null) R.string.log_set else R.string.update_set)) }
    if (set != null) TextButton(onClick = cancel) { Text(stringResource(R.string.cancel)) }
}

@Composable
private fun ExercisePicker(choices: List<ExerciseRow>, onDismiss: () -> Unit, onChoose: (String) -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(stringResource(R.string.choose_exercise)) }, text = {
        Column {
            OutlinedTextField(query, { query = it }, label = { Text(stringResource(R.string.search)) }, singleLine = true)
            Column(Modifier.heightIn(max = 400.dp).verticalScroll(rememberScrollState())) {
                choices.filter { it.name.contains(query, ignoreCase = true) }.forEach { choice ->
                    TextButton(onClick = { onChoose(choice.id) }, modifier = Modifier.fillMaxWidth()) { Text(choice.name) }
                }
            }
        }
    }, confirmButton = {}, dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } })
}

@Composable
private fun FinishSection(status: String, vm: WorkoutViewModel) {
    val context = LocalContext.current
    val shareTitle = stringResource(R.string.share_workout)
    val clipboardLabel = stringResource(R.string.clipboard_workout)
    val uploadEnabled by vm.uploadEnabled.collectAsState()
    var confirmDiscard by rememberSaveable { mutableStateOf(false) }
    when (status) {
        "in_progress" -> {
            Button(onClick = vm::finish, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.finish_workout)) }
            TextButton(onClick = { confirmDiscard = true }) { Text(stringResource(R.string.discard_workout)) }
        }
        "finished" -> {
            if (uploadEnabled) {
                Text(stringResource(R.string.upload_pending))
                Button(onClick = vm::finish) { Text(stringResource(R.string.retry_upload)) }
            } else {
                Text(stringResource(R.string.saved_upload_off))
            }
            OutlinedButton(onClick = vm::done) { Text(stringResource(R.string.done)) }
        }
        "pushed" -> {
            Text(stringResource(R.string.sync_uploaded))
            OutlinedButton(onClick = vm::done) { Text(stringResource(R.string.done)) }
        }
        "conflict", "conflict_deleted" -> {
            Text(stringResource(if (status == "conflict") R.string.sync_conflict else R.string.sync_deleted_conflict))
            Row {
                Button(onClick = vm::keepMine) { Text(stringResource(R.string.keep_mine)) }
                OutlinedButton(onClick = vm::takeTheirs) { Text(stringResource(R.string.take_theirs)) }
            }
        }
    }
    if (confirmDiscard && status == "in_progress") AlertDialog(
        onDismissRequest = { confirmDiscard = false },
        title = { Text(stringResource(R.string.discard_confirm_title)) },
        text = { Text(stringResource(R.string.discard_confirm_message)) },
        confirmButton = { TextButton(onClick = { vm.discard(); confirmDiscard = false }) { Text(stringResource(R.string.discard)) } },
        dismissButton = { TextButton(onClick = { confirmDiscard = false }) { Text(stringResource(R.string.cancel)) } },
    )
    if (status != "in_progress") Row {
        OutlinedButton(onClick = { vm.share { text ->
            val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
            context.startActivity(Intent.createChooser(send, shareTitle))
        } }) { Text(stringResource(R.string.share)) }
        OutlinedButton(onClick = { vm.share { text ->
            context.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText(clipboardLabel, text))
        } }) { Text(stringResource(R.string.copy)) }
    }
}
