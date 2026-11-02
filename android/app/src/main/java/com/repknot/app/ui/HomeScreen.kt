package com.repknot.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.repknot.app.ProgressUiState
import com.repknot.app.R
import com.repknot.app.WorkoutUiState
import com.repknot.domain.ProgramWorkout

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(today: WorkoutUiState.Today, selected: ProgramWorkout, progress: ProgressUiState,
               onSelect: (String) -> Unit, onPreview: (String) -> Unit, openProgress: () -> Unit) {
    var choosing by remember { mutableStateOf(false) }
    var pending by remember { mutableStateOf(selected.key) }
    val exercises = selected.exercises.sortedBy { it.position }
    Text(today.programName, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
    Text(stringResource(R.string.home_heading), style = MaterialTheme.typography.headlineMedium)
    Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(if (selected.key == today.suggested.key) R.string.home_next_rotation else R.string.home_selection),
                    color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium)
                Text(selected.name, style = MaterialTheme.typography.displaySmall)
                Text(pluralStringResource(R.plurals.preview_exercises, exercises.size, exercises.size),
                    color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            }
            if (exercises.isEmpty()) Text(stringResource(R.string.home_empty_exercises))
            exercises.take(3).forEachIndexed { index, exercise ->
                Column {
                    HorizontalDivider()
                    Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text((index + 1).toString().padStart(2, '0'), color = MaterialTheme.colorScheme.primary)
                        Text(exercise.exerciseName)
                    }
                }
            }
            if (exercises.size > 3) Text(stringResource(R.string.home_remaining, exercises.drop(3).joinToString(", ") { it.exerciseName }),
                color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            Button(onClick = { onPreview(selected.key) }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.home_preview))
            }
        }
    }
    TextButton(onClick = { pending = selected.key; choosing = true }, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.home_choose))
    }
    Text(stringResource(R.string.home_from_progress), style = MaterialTheme.typography.titleSmall)
    Surface(onClick = openProgress, shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            val needsLook = !progress.loading && progress.declines > 0
            Text(if (needsLook) pluralStringResource(R.plurals.home_declines, progress.declines, progress.declines)
                else stringResource(R.string.home_training_body), style = MaterialTheme.typography.titleSmall)
            Text(stringResource(if (needsLook) R.string.home_review_history else R.string.home_progress_neutral),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    if (choosing) {
        ModalBottomSheet(onDismissRequest = { choosing = false }) {
            Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp)) {
                Text(stringResource(R.string.home_choose_title), style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 12.dp))
                Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).selectableGroup()) {
                    today.workouts.forEach { workout ->
                        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp)
                            .selectable(selected = pending == workout.key, role = Role.RadioButton, onClick = { pending = workout.key })
                            .padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(workout.name, style = MaterialTheme.typography.titleMedium)
                                Text(pluralStringResource(R.plurals.preview_exercises, workout.exercises.size, workout.exercises.size),
                                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                if (workout.key == today.suggested.key) Text(stringResource(R.string.home_next),
                                    style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                            }
                            RadioButton(selected = pending == workout.key, onClick = null)
                        }
                    }
                }
                Button(onClick = { onSelect(pending); choosing = false }, modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                    enabled = today.workouts.any { it.key == pending }) { Text(stringResource(R.string.home_use_selected)) }
            }
        }
    }
}
