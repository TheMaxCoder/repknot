package com.repknot.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.repknot.app.*
import com.repknot.data.SessionSnapshot
import com.repknot.data.SetRow
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private fun title(s: SessionSnapshot) = s.row.title.ifBlank { "Workout" }
private fun month(date: String): String = runCatching { LocalDate.parse(date).format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.getDefault())) }.getOrDefault(date)
private fun duration(s: SessionSnapshot): String? {
    val start = s.row.startedAt ?: return null
    val end = s.row.finishedAt ?: return null
    if (end < start) return null
    return "${(end - start) / 60000} min"
}
private fun setText(set: SetRow): String {
    val values = mutableListOf<String>()
    set.load?.let { values.add("$it ${set.unit?.takeIf { unit -> unit.isNotBlank() } ?: "unit unknown"}") }
    if (set.load == null) set.unit?.let { values.add(it) }
    set.reps?.let { values.add("$it reps") }
    set.durationS?.let { values.add("$it sec") }
    set.rir?.let { values.add("RIR $it") }
    if (values.isEmpty()) values.add("Missing values")
    set.notes?.takeIf { it.isNotBlank() }?.let { values.add(it) }
    return values.joinToString(" · ")
}

@Composable
private fun ArchiveLayout(back: (() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (back != null) TextButton(onClick = back) { Text("Back") }
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
    ArchiveLayout {
        Text("History", style = MaterialTheme.typography.headlineMedium)
        PrimaryTabRow(selectedTabIndex = tab) {
            listOf("Workouts", "Exercises").forEachIndexed { index, name -> Tab(selected = tab == index, onClick = { tab = index }, text = { Text(name) }) }
        }
        ArchiveState(state) {
            if (tab == 0) {
                if (state.sessions.isEmpty()) Text("No completed workouts yet.")
                state.sessions.groupBy { month(it.row.performedOn) }.forEach { (group, sessions) ->
                    Text(group, style = MaterialTheme.typography.titleMedium)
                    sessions.forEach { s -> ArchiveCard({ openWorkout(s.row.id) }) {
                        Text(title(s), style = MaterialTheme.typography.titleMedium)
                        Text("${s.row.performedOn} · ${s.exercises.size} exercises" + if (s.row.light) " · Light day" else "")
                        Text(historyStatus(s), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } }
                }
            } else {
                OutlinedTextField(query, { query = it }, label = { Text("Search exercises") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                val exercises = historyExercises(state.sessions, query)
                if (exercises.isEmpty()) Text(if (query.isBlank()) "No recorded exercises yet." else "No exercises found.")
                exercises.forEach { exercise -> ArchiveCard({ openExercise(exercise.id) }) {
                    Text(exercise.name, style = MaterialTheme.typography.titleMedium)
                    Text(exercise.performedOn, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } }
            }
        }
    }
}

@Composable
private fun ExerciseRecords(s: SessionSnapshot, exerciseId: String? = null, openExercise: ((String) -> Unit)? = null) {
    val exercises = s.exercises.filter { exerciseId == null || it.exerciseId == exerciseId }
    if (exercises.isEmpty()) Text("No exercises in this workout.")
    exercises.forEach { exercise ->
        if (openExercise != null) TextButton(onClick = { openExercise(exercise.exerciseId) }) { Text(exercise.exerciseName) }
        else Text(exercise.exerciseName, style = MaterialTheme.typography.titleSmall)
        exercise.notes?.takeIf { it.isNotBlank() }?.let { Text("Notes: $it") }
        val sets = s.sets.filter { it.sessionExerciseId == exercise.id }
        if (sets.isEmpty()) Text("No recorded sets.")
        sets.forEachIndexed { index, set -> Text("${index + 1}. ${setText(set)}") }
    }
}

@Composable
fun WorkoutHistoryScreen(state: HistoryUiState, id: String, back: () -> Unit, openExercise: (String) -> Unit) {
    ArchiveLayout(back) { ArchiveState(state) {
        val s = state.sessions.firstOrNull { it.row.id == id }
        if (s == null) Text("Workout no longer available.") else {
            Text(title(s), style = MaterialTheme.typography.headlineMedium)
            Text(listOfNotNull(s.row.performedOn, historyStatus(s), if (s.row.light) "Light day" else null, duration(s)).joinToString(" · "))
            s.row.notes?.takeIf { it.isNotBlank() }?.let { Text("Notes: $it") }
            ExerciseRecords(s, openExercise = openExercise)
        }
    } }
}

@Composable
fun ExerciseHistoryScreen(state: HistoryUiState, progress: ProgressUiState, id: String, back: () -> Unit, openWorkout: (String) -> Unit) {
    ArchiveLayout(back) { ArchiveState(state) {
        val sessions = state.sessions.filter { s -> s.exercises.any { ex -> ex.exerciseId == id && s.sets.any { it.sessionExerciseId == ex.id } } }
        val item = progress.items.firstOrNull { it.exerciseId == id }
        val name = item?.name ?: historyExercises(sessions).firstOrNull { it.id == id }?.name
        if (name == null) Text("Exercise no longer available.") else {
            Text(name, style = MaterialTheme.typography.headlineMedium)
            if (item != null) {
                Text(groupLabel(item), color = MaterialTheme.colorScheme.primary)
                Text(reasonText(item.reason))
                flagText(item).forEach { Text(it) }
            } else Text("Recorded history")
            if (sessions.isEmpty()) Text("No recorded sessions yet.")
            sessions.forEach { s ->
                ArchiveCard({ openWorkout(s.row.id) }) {
                    Text("${s.row.performedOn} · ${title(s)}" + if (s.row.light) " · Light day" else "", style = MaterialTheme.typography.titleSmall)
                    if (item != null && s.row.id in item.bestSessionIds) Text("Best")
                    s.row.notes?.takeIf { it.isNotBlank() }?.let { Text("Notes: $it") }
                    ExerciseRecords(s, exerciseId = id)
                }
            }
        }
    } }
}
