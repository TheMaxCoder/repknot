package com.repknot.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.repknot.app.CoachNotesUiState
import com.repknot.app.ProgressUiState
import com.repknot.app.R
import com.repknot.app.WorkoutUiState
import com.repknot.app.checkInDue
import com.repknot.app.dipAlertText
import java.time.LocalDate
import java.time.LocalDateTime
import kotlinx.coroutines.delay

@Composable
fun AttentionRows(progress: ProgressUiState, openProgress: () -> Unit, openCheckIn: () -> Unit) {
    var now by remember { mutableStateOf(LocalDateTime.now()) }
    LaunchedEffect(Unit) { while (true) { now = LocalDateTime.now(); delay(60_000) } }
    if (!progress.loading && checkInDue(now, progress.checkIns.mapNotNull { runCatching { LocalDate.parse(it.checkedOn) }.getOrNull() }))
        AttentionRow("Weight & waist check-in due", R.drawable.ic_nav_progress, openCheckIn)
    val alert = progress.dipAlert?.let { dipAlertText(it, progress.names) }
        ?: if (progress.declines > 0) pluralStringResource(R.plurals.home_declines, progress.declines, progress.declines) else null
    if (alert != null) AttentionRow(alert, R.drawable.ic_note_focus, openProgress)
}

@Composable
private fun AttentionRow(title: String, icon: Int, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
        Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(painterResource(icon), null)
            Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
            Icon(painterResource(R.drawable.ic_expand_more), null, Modifier.rotate(-90f))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(today: WorkoutUiState.Today, progress: ProgressUiState,
               coachNotes: CoachNotesUiState, markSeen: () -> Unit, toggleDone: (Int) -> Unit,
               onPreview: (String) -> Unit, onStart: (String) -> Unit, openProgress: () -> Unit, openCheckIn: () -> Unit) {
    var choosing by remember { mutableStateOf(false) }
    val workout = today.suggested
    val exercises = workout.exercises.sortedBy { it.position }
    Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(R.string.home_next_rotation), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium)
                Text(workout.name, style = MaterialTheme.typography.displaySmall)
                val count = pluralStringResource(R.plurals.preview_exercises, exercises.size, exercises.size)
                Text(count + (exercises.firstOrNull()?.let { " · ${it.exerciseName}" } ?: ""),
                    color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { onPreview(workout.key) }, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Text("Preview") }
                Button(onClick = { onStart(workout.key) }, enabled = exercises.isNotEmpty(), modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Text("Start") }
            }
            TextButton(onClick = { choosing = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Other workouts") }
        }
    }
    CoachNotesCard(coachNotes, workout.name, exercises.map { it.exerciseId }.toSet(), markSeen, toggleDone)
    AttentionRows(progress, openProgress, openCheckIn)
    if (choosing) {
        ModalBottomSheet(onDismissRequest = { choosing = false }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
            Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp)) {
                Text("Other workouts", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(12.dp))
                Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
                    today.workouts.forEach { item ->
                        Column(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(item.name, style = MaterialTheme.typography.titleMedium)
                                if (item.key == workout.key) Text(stringResource(R.string.home_next), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium)
                            }
                            Text(pluralStringResource(R.plurals.preview_exercises, item.exercises.size, item.exercises.size), style = MaterialTheme.typography.bodySmall)
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(onClick = { choosing = false; onPreview(item.key) }, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Text("Preview") }
                                Button(onClick = { choosing = false; onStart(item.key) }, enabled = item.exercises.isNotEmpty(), modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Text("Start") }
                            }
                        }
                    }
                }
            }
        }
    }
}
