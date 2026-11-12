package com.repknot.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.repknot.app.*
import com.repknot.data.SessionSnapshot
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private fun month(date: String): String = runCatching { LocalDate.parse(date).format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH)) }.getOrDefault(date)
private fun duration(s: SessionSnapshot): String? {
    val start = s.row.startedAt ?: return null
    val end = s.row.finishedAt ?: return null
    if (end < start) return null
    return "${(end - start) / 60000} min"
}
@Composable
private fun ArchiveLayout(content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        content()
    }
}

@Composable
private fun ArchiveCard(onClick: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Surface(onClick = onClick, shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(5.dp), content = content)
    }
}

@Composable
private fun ArchiveState(state: HistoryUiState, content: @Composable () -> Unit) {
    when { state.loading -> CircularProgressIndicator(); state.error -> Text("Could not load history."); else -> content() }
}

@Composable
fun HistoryScreen(state: HistoryUiState, openWorkout: (String) -> Unit, openExercise: (String) -> Unit) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var query by rememberSaveable { mutableStateOf("") }
    val workoutScroll = rememberLazyListState()
    val exerciseScroll = rememberLazyListState()
    Column(Modifier.fillMaxSize().imePadding()) {
        Text("History", style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp))
        PrimaryTabRow(selectedTabIndex = tab) {
            listOf("Workouts", "Exercises").forEachIndexed { index, name -> Tab(selected = tab == index, onClick = { tab = index }, text = { Text(name) }) }
        }
        ArchiveState(state) {
            if (tab == 0) {
                LazyColumn(Modifier.weight(1f).fillMaxWidth(), state = workoutScroll,
                    contentPadding = PaddingValues(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (state.sessions.isEmpty()) item { Text("No completed workouts yet.") }
                    state.sessions.groupBy { month(it.row.performedOn) }.forEach { (group, sessions) ->
                        item(key = "month-$group") { Text(group, style = MaterialTheme.typography.titleMedium) }
                        items(sessions, key = { it.row.id }) { s -> ArchiveCard({ openWorkout(s.row.id) }) {
                            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                                val date = runCatching { LocalDate.parse(s.row.performedOn) }.getOrNull()
                                if (date != null) Column(Modifier.width(36.dp), horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
                                    Text(date.dayOfMonth.toString(), style = MaterialTheme.typography.headlineSmall)
                                    Text(date.format(DateTimeFormatter.ofPattern("MMM", Locale.ENGLISH)),
                                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(historyTitle(s), style = MaterialTheme.typography.titleMedium)
                                    Text("${s.row.performedOn} · ${s.exercises.size} exercises" + if (s.row.light) " · Light day" else "",
                                        style = MaterialTheme.typography.bodySmall)
                                    Text(historyStatus(s), color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        style = MaterialTheme.typography.bodySmall)
                                    if (s.row.id in state.dipSessionIds) BroadDipLabel()
                                }
                                Text("›", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        } }
                    }
                }
            } else {
                OutlinedTextField(query, { query = it }, label = { Text("Search exercises") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp))
                val exercises = historyExercises(state.sessions, query)
                LazyColumn(Modifier.weight(1f).fillMaxWidth(), state = exerciseScroll,
                    contentPadding = PaddingValues(start = 24.dp, end = 24.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (exercises.isEmpty()) item { Text(if (query.isBlank()) "No recorded exercises yet." else "No exercises found.") }
                    items(exercises, key = { it.id }) { exercise -> ArchiveCard({ openExercise(exercise.id) }) {
                        Text(exercise.name, style = MaterialTheme.typography.titleMedium)
                        Text("Last trained ${exercise.performedOn}", color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall)
                    } }
                }
            }
        }
    }
}

@Composable
private fun ExerciseRecords(s: SessionSnapshot, openExercise: (String) -> Unit) {
    val exercises = s.exercises
    if (exercises.isEmpty()) Text("No exercises in this workout.")
    exercises.forEach { exercise ->
        TextButton(onClick = { openExercise(exercise.exerciseId) }) { Text(exercise.exerciseName) }
        exercise.notes?.takeIf { it.isNotBlank() }?.let { Text("Notes: $it") }
        val sets = s.sets.filter { it.sessionExerciseId == exercise.id }
        if (sets.isEmpty()) Text("No recorded sets.")
        sets.forEachIndexed { index, set -> Text("${index + 1}. ${historySetText(set)}") }
    }
}

@Composable
private fun BroadDipLabel() {
    Text("Broad dip", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
fun WorkoutHistoryScreen(state: HistoryUiState, id: String, openExercise: (String) -> Unit) {
    ArchiveLayout { ArchiveState(state) {
        val s = state.sessions.firstOrNull { it.row.id == id }?.let(::historyDisplay)
        if (s == null) Text("Workout no longer available.") else {
            Text(historyTitle(s), style = MaterialTheme.typography.headlineMedium)
            Text(listOfNotNull(s.row.performedOn, historyStatus(s), if (s.row.light) "Light day" else null, duration(s)).joinToString(" · "))
            if (s.row.id in state.dipSessionIds) BroadDipLabel()
            s.row.notes?.takeIf { it.isNotBlank() }?.let { Text("Notes: $it") }
            ExerciseRecords(s, openExercise = openExercise)
        }
    } }
}

@Composable
fun ExerciseHistoryScreen(state: HistoryUiState, progress: ProgressUiState, id: String, openWorkout: (String) -> Unit) {
    ArchiveLayout { ArchiveState(state) {
        val sessions = state.sessions.filter { s -> s.exercises.any { ex -> ex.exerciseId == id && s.sets.any { it.sessionExerciseId == ex.id } } }
        val item = progress.items.firstOrNull { it.exerciseId == id }
        val name = item?.name ?: state.sessions.firstOrNull { s -> s.exercises.any { it.exerciseId == id } }
            ?.exercises?.firstOrNull { it.exerciseId == id }?.exerciseName?.ifBlank { "Exercise" }
        if (name == null) Text("Exercise no longer available.") else {
            // Same compact presentation as the pre-030 Progress exercise screen (owner follow-up).
            Text(name, style = MaterialTheme.typography.headlineSmall)
            if (item != null) {
                Text(groupLabel(item), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                Text(reasonText(item.reason), style = MaterialTheme.typography.bodyMedium)
                flagText(item).forEach { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary) }
            } else Text("Recorded history", style = MaterialTheme.typography.bodyMedium)
            if (sessions.isEmpty()) Text("No recorded sessions yet.")
            Column {
                sessions.forEach { s ->
                    Text(exerciseHistoryLine(s, id) + if (item != null && s.row.id in item.bestSessionIds) " · best" else "",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                            .clickable(role = Role.Button, onClickLabel = "Open workout") { openWorkout(s.row.id) }
                            .padding(vertical = 6.dp))
                }
            }
        }
    } }
}
