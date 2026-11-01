package com.repknot.app

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.repknot.data.ExerciseRow
import com.repknot.data.SessionExerciseRow
import com.repknot.data.SetRow
import com.repknot.domain.LoadUnit
import com.repknot.domain.Prefill

@Composable
fun WorkoutScreen(vm: WorkoutViewModel, footer: @Composable () -> Unit) {
    val state by vm.state.collectAsState()
    val error by vm.error.collectAsState()
    Scaffold { padding ->
        Column(Modifier.fillMaxSize().padding(padding).imePadding().verticalScroll(rememberScrollState()).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            error?.let { Text(stringResource(it), color = MaterialTheme.colorScheme.error) }
            when (val current = state) {
                WorkoutUiState.Loading -> CircularProgressIndicator()
                WorkoutUiState.NoProgram -> Text(stringResource(R.string.no_program))
                is WorkoutUiState.Today -> {
                    Text(current.programName, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.titleSmall)
                    Text(stringResource(R.string.next_workout))
                    Text(current.suggested.name, style = MaterialTheme.typography.headlineMedium)
                    Button(onClick = { vm.start(current.suggested.key) }, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.start_workout, current.suggested.name))
                    }
                    Text(stringResource(R.string.other_workouts), style = MaterialTheme.typography.titleMedium)
                    current.workouts.filter { it.key != current.suggested.key }.forEach { workout ->
                        OutlinedButton(onClick = { vm.start(workout.key) }, modifier = Modifier.fillMaxWidth()) { Text(workout.name) }
                    }
                }
                is WorkoutUiState.Session -> SessionScreen(current, vm)
            }
            footer()
        }
    }
}

@Composable
private fun SessionScreen(session: WorkoutUiState.Session, vm: WorkoutViewModel) {
    var picker by rememberSaveable(session.row.id) { mutableStateOf<String?>(null) }
    Text(session.row.title, style = MaterialTheme.typography.headlineMedium)
    Text(session.row.performedOn)
    session.exercises.forEachIndexed { index, exercise ->
        key(exercise.row.id) {
            ExerciseCard(exercise, session.choices, index, session.exercises.size, vm) { picker = exercise.row.id }
        }
    }
    OutlinedButton(onClick = { picker = "add" }) { Text(stringResource(R.string.add_exercise)) }
    var notes by rememberSaveable(session.row.id) { mutableStateOf(session.row.notes.orEmpty()) }
    OutlinedTextField(notes, { notes = it }, label = { Text(stringResource(R.string.session_notes)) }, modifier = Modifier.fillMaxWidth())
    OutlinedButton(onClick = { vm.saveSessionNotes(notes) }) { Text(stringResource(R.string.save_notes)) }
    FinishSection(session.row.status, vm)
    if (picker != null) ExercisePicker(session.choices, onDismiss = { picker = null }) { id ->
        if (picker == "add") vm.addExercise(id) else picker?.let { vm.substitute(it, id) }
        picker = null
    }
}

@Composable
private fun ExerciseCard(exercise: ExerciseUi, choices: List<ExerciseRow>, index: Int, count: Int,
                         vm: WorkoutViewModel, substitute: () -> Unit) {
    val row = exercise.row
    var editing by rememberSaveable(row.id) { mutableStateOf<String?>(null) }
    var remove by rememberSaveable(row.id) { mutableStateOf(false) }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(row.exerciseName, style = MaterialTheme.typography.titleLarge)
            row.substitutedFor?.let { id ->
                Text(stringResource(R.string.instead_of, choices.firstOrNull { it.id == id }?.name
                    ?: stringResource(R.string.prescribed_exercise)))
            }
            Prescription(row)
            PreviousHint(exercise.previous)
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
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton(onClick = { vm.moveExercise(row.id, -1) }, enabled = index > 0) { Text(stringResource(R.string.move_up)) }
                TextButton(onClick = { vm.moveExercise(row.id, 1) }, enabled = index < count - 1) { Text(stringResource(R.string.move_down)) }
                TextButton(onClick = substitute) { Text(stringResource(R.string.substitute)) }
                TextButton(onClick = { remove = true }) { Text(stringResource(R.string.remove)) }
            }
            var notes by rememberSaveable(row.id) { mutableStateOf(row.notes.orEmpty()) }
            OutlinedTextField(notes, { notes = it }, label = { Text(stringResource(R.string.exercise_notes)) }, modifier = Modifier.fillMaxWidth())
            OutlinedButton(onClick = { vm.saveExerciseNotes(row.id, notes) }) { Text(stringResource(R.string.save_notes)) }
        }
    }
    if (remove) AlertDialog(onDismissRequest = { remove = false }, title = { Text(stringResource(R.string.remove_exercise)) },
        text = { Text(stringResource(R.string.remove_exercise_confirm, row.exerciseName)) },
        confirmButton = { TextButton(onClick = { vm.removeExercise(row.id); remove = false }) { Text(stringResource(R.string.remove)) } },
        dismissButton = { TextButton(onClick = { remove = false }) { Text(stringResource(R.string.cancel)) } })
}

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
    val parts = listOfNotNull(
        row.plannedSets?.let { pluralStringResource(R.plurals.prescription_sets, it, it) },
        range(row.plannedRepMin, row.plannedRepMax)?.let { stringResource(R.string.prescription_reps, it) },
        range(row.plannedRirMin, row.plannedRirMax)?.let { stringResource(R.string.prescription_rir, it) },
        loadText(row.plannedLoad, row.plannedUnit),
    )
    if (parts.isNotEmpty()) Text(parts.joinToString(" · "), color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun PreviousHint(previous: Prefill) {
    val text = when (previous) {
        is Prefill.Previous -> stringResource(R.string.previous_sets, previous.sets.joinToString(", ") { set ->
            formatSet(SetRow("", "", "", set.position, set.load, set.unit.name.lowercase(), set.reps, set.durationS, set.rir))
        })
        is Prefill.StartLoad -> if (previous.load == null) null else loadText(previous.load, previous.unit.name.lowercase())?.let { stringResource(R.string.start_load, it) }
    }
    text?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant) }
}

@Composable
private fun SetInput(exercise: ExerciseUi, set: SetRow?, vm: WorkoutViewModel, cancel: () -> Unit) {
    val suggestion = exercise.suggestion
    // Primitive keys are saveable and represent all fields of the suggestion.
    val keys = arrayOf<Any?>(exercise.row.id, exercise.sets.size, set?.id, suggestion.load, suggestion.unit.name, suggestion.reps)
    var load by rememberSaveable(*keys) { mutableStateOf(number(set?.load ?: if (set == null) suggestion.load else null)) }
    var reps by rememberSaveable(*keys) { mutableStateOf(number(set?.reps ?: if (set == null) suggestion.reps else null)) }
    var rir by rememberSaveable(*keys) { mutableStateOf(set?.rir?.toString().orEmpty()) }
    var unit by rememberSaveable(*keys) { mutableStateOf(set?.unit?.uppercase() ?: suggestion.unit.name) }
    if (set == null && exercise.previous is Prefill.Previous) Text(stringResource(R.string.suggested_previous), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (unit != LoadUnit.BODYWEIGHT.name) OutlinedTextField(load, { load = it }, modifier = Modifier.weight(1f), label = { Text(stringResource(R.string.label_load)) }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
        OutlinedTextField(reps, { reps = it }, modifier = Modifier.weight(1f), label = { Text(stringResource(R.string.label_reps)) }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
        OutlinedTextField(rir, { rir = it }, modifier = Modifier.weight(1f), label = { Text(stringResource(R.string.label_rir)) }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
    }
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        listOf(LoadUnit.KG to R.string.unit_kg, LoadUnit.LB to R.string.unit_lb, LoadUnit.PLATES to R.string.unit_plates, LoadUnit.BODYWEIGHT to R.string.unit_bodyweight).forEach { (choice, label) ->
            FilterChip(selected = unit == choice.name, onClick = { unit = choice.name }, label = { Text(stringResource(label), style = MaterialTheme.typography.labelSmall) })
        }
    }
    Row {
        Button(onClick = { vm.logSet(exercise.row.id, set?.id, load, LoadUnit.valueOf(unit), reps, rir) }) { Text(stringResource(if (set == null) R.string.log_set else R.string.update_set)) }
        if (set != null) TextButton(onClick = cancel) { Text(stringResource(R.string.cancel)) }
    }
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
    when (status) {
        "in_progress" -> Button(onClick = vm::finish) { Text(stringResource(R.string.finish_workout)) }
        "finished" -> {
            Text(stringResource(R.string.upload_pending))
            Button(onClick = vm::finish) { Text(stringResource(R.string.retry_upload)) }
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
