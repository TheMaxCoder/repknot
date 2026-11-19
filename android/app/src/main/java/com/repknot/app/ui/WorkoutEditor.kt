package com.repknot.app

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.unit.dp
import com.repknot.data.SessionExerciseRow
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkoutEditor(session: WorkoutUiState.Session?, preview: WorkoutUiState.Preview?, vm: WorkoutViewModel,
                  modifier: Modifier = Modifier, done: () -> Unit) {
    val rows = session?.exercises?.map { it.row } ?: preview?.rows.orEmpty()
    val choices = session?.choices ?: preview?.choices.orEmpty()
    val counts = session?.exercises?.associate { it.row.id to it.sets.size }.orEmpty()
    var expanded by remember { mutableStateOf<String?>(null) }
    var moving by remember { mutableStateOf<String?>(null) }
    var picker by remember { mutableStateOf<String?>(null) }
    var removing by remember { mutableStateOf<String?>(null) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val undoText = stringResource(R.string.undo)
    BackHandler { done() }
    val resources = androidx.compose.ui.platform.LocalResources.current
    fun remove(row: SessionExerciseRow) {
        val removedText = resources.getString(R.string.exercise_removed, row.exerciseName)
        snackbar.currentSnackbarData?.dismiss()
        vm.removeExercise(row.id)
        expanded = null
        scope.launch {
            if (snackbar.showSnackbar(removedText, undoText) == SnackbarResult.ActionPerformed) vm.undoRemove()
        }
    }
    Scaffold(modifier = modifier.fillMaxSize(), snackbarHost = { SnackbarHost(snackbar) }, bottomBar = {
        Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { picker = "append" }, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Text(stringResource(R.string.add_exercise)) }
            Button(onClick = done, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.done)) }
        }
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp)) {
            Text(stringResource(R.string.edit_workout), style = MaterialTheme.typography.headlineMedium)
            rows.forEachIndexed { index, row ->
                val count = counts[row.id] ?: 0
                val summary = pluralStringResource(R.plurals.editor_logged_sets, count, count)
                val description = stringResource(R.string.editor_row_description, index + 1, row.exerciseName, summary)
                ListItem(headlineContent = { Text("${index + 1}. ${row.exerciseName}") },
                    supportingContent = {
                        Column {
                            row.substitutedFor?.let { original ->
                                Text(stringResource(R.string.instead_of, choices.firstOrNull { it.id == original }?.name
                                    ?: stringResource(R.string.prescribed_exercise)))
                            }
                            Text(if (count > 0) summary else if (row.plannedSets == null) stringResource(R.string.added_no_plan) else editorPlan(row))
                        }
                    }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                        .semantics(mergeDescendants = true) { contentDescription = description }
                        .clickable { expanded = if (expanded == row.id) null else row.id })
                if (expanded == row.id) Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Two even rows of actions under the expanded exercise (task 042 design).
                    val action = Modifier.heightIn(min = 48.dp)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilledTonalButton(onClick = { moving = row.id }, modifier = action.weight(1f)) { Text(stringResource(R.string.move_to)) }
                        FilledTonalButton(onClick = { picker = "after:${row.id}" }, modifier = action.weight(1f)) { Text(stringResource(R.string.add_after)) }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { picker = "replace:${row.id}" }, enabled = count == 0, modifier = action.weight(1f)) {
                            Text(stringResource(R.string.replace))
                        }
                        OutlinedButton(onClick = { if (count == 0) remove(row) else removing = row.id }, modifier = action.weight(1f),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)) {
                            Text(stringResource(R.string.remove))
                        }
                    }
                    if (count > 0) Text(stringResource(R.string.has_logged_sets), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                HorizontalDivider()
            }
        }
    }
    moving?.let { id ->
        val index = rows.indexOfFirst { it.id == id }
        ModalBottomSheet(onDismissRequest = { moving = null }) {
            Column(Modifier.verticalScroll(rememberScrollState()).navigationBarsPadding()) {
                rows.getOrNull(index)?.let { Text(stringResource(R.string.move_title, it.exerciseName), style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)) }
                rows.forEachIndexed { destination, row ->
                    if (destination != index && destination != index + 1) {
                        TextButton(onClick = {
                            vm.moveExerciseTo(id, if (destination > index) destination - 1 else destination)
                            moving = null
                        }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(stringResource(R.string.before_exercise, row.exerciseName)) }
                    }
                }
                if (index >= 0 && index != rows.lastIndex) TextButton(onClick = {
                    vm.moveExerciseTo(id, rows.lastIndex); moving = null
                }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(stringResource(R.string.at_end)) }
            }
        }
    }
    picker?.let { mode ->
        ExercisePicker(choices, rows.map { it.exerciseId }.toSet(), onDismiss = { picker = null }) { id ->
            when {
                mode == "append" -> vm.insertExercise(id, rows.lastIndex)
                mode.startsWith("after:") -> rows.indexOfFirst { it.id == mode.substringAfter(':') }.takeIf { it >= 0 }?.let { vm.insertExercise(id, it) }
                mode.startsWith("replace:") -> vm.replace(mode.substringAfter(':'), id)
            }
            picker = null
        }
    }
    removing?.let { id -> rows.firstOrNull { it.id == id }?.let { row ->
        val count = counts[id] ?: 0
        AlertDialog(onDismissRequest = { removing = null },
            text = { Text(pluralStringResource(R.plurals.remove_logged_confirm, count, row.exerciseName, count)) },
            confirmButton = { TextButton(onClick = { vm.removeExercise(id); expanded = null; removing = null }) { Text(stringResource(R.string.remove)) } },
            dismissButton = { TextButton(onClick = { removing = null }) { Text(stringResource(R.string.cancel)) } })
    } }
}

private fun editorPlan(row: SessionExerciseRow): String {
    val reps = when {
        row.plannedRepMin != null && row.plannedRepMax != null && row.plannedRepMin != row.plannedRepMax -> "${row.plannedRepMin}–${row.plannedRepMax}"
        else -> (row.plannedRepMin ?: row.plannedRepMax)?.toString() ?: "–"
    }
    return "${row.plannedSets?.toString() ?: "–"} × $reps"
}
