package com.repknot.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.input.KeyboardType
import com.repknot.app.*
import com.repknot.app.R
import com.repknot.app.dipText
import com.repknot.app.exposureLine
import com.repknot.app.flagText
import com.repknot.app.groupLabel
import com.repknot.app.reasonText
import com.repknot.domain.ExerciseProgress
import com.repknot.domain.BodyVerdict

@Composable
fun ProgressScreen(state: ProgressUiState, back: () -> Unit, openBody: () -> Unit,
                   addCheckIn: (String, String, String, String) -> Boolean, openExercise: (String) -> Unit) {
    var showDialog by rememberSaveable { mutableStateOf(false) }
    if (showDialog) CheckInDialog(state, addCheckIn) { showDialog = false }
    ProgressLayout(back, state.loading, title = {
        Text(stringResource(R.string.progress), style = MaterialTheme.typography.titleLarge)
    }) {
        when {
            state.loading -> Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            else -> {
                Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                    Column {
                        Column(Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = openBody).padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Text(stringResource(R.string.body), Modifier.weight(1f),
                                    style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                                Text("›", Modifier.clearAndSetSemantics {}, style = MaterialTheme.typography.titleLarge)
                            }
                            BodyLines(state, prominentWeight = true)
                        }
                        TextButton(onClick = { showDialog = true }, modifier = Modifier.padding(start = 8.dp, bottom = 8.dp)) {
                            Text(stringResource(R.string.add_check_in))
                        }
                    }
                }
                if (state.items.isEmpty()) Text(stringResource(R.string.no_finished_workouts))
                state.dips.forEach { dip ->
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer), modifier = Modifier.fillMaxWidth()) {
                        Text(dipText(dip, state.names), Modifier.padding(16.dp))
                    }
                }
                if (state.items.isNotEmpty()) {
                    Text(stringResource(R.string.training), style = MaterialTheme.typography.titleMedium)
                }
                state.items.forEachIndexed { index, item ->
                    val group = groupLabel(item)
                    if (index == 0 || groupLabel(state.items[index - 1]) != group) {
                        val count = state.items.drop(index).takeWhile { groupLabel(it) == group }.size
                        val marker = when (group) {
                            "Needs a look" -> MaterialTheme.colorScheme.error
                            "Ready to move up", "Improving" -> MaterialTheme.colorScheme.primary
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        }
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Box(Modifier.size(8.dp).background(marker, CircleShape).clearAndSetSemantics {})
                            Text(group, Modifier.weight(1f), style = MaterialTheme.typography.labelLarge)
                            Text(count.toString(), style = MaterialTheme.typography.labelLarge)
                        }
                    }
                    Card(Modifier.fillMaxWidth().clickable(role = Role.Button) { openExercise(item.exerciseId) }) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Text(item.name, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                                Text("›", Modifier.clearAndSetSemantics {}, style = MaterialTheme.typography.titleLarge)
                            }
                            Text(reasonText(item.reason), style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Flags(item)
                            item.exposures.takeLast(3).asReversed().forEach { exposure ->
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                                Text(exposureLine(exposure), style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun BodyScreen(state: ProgressUiState, back: () -> Unit, addCheckIn: (String, String, String, String) -> Boolean) {
    var showDialog by rememberSaveable { mutableStateOf(false) }
    if (showDialog) CheckInDialog(state, addCheckIn) { showDialog = false }
    ProgressLayout(back, state.loading, title = {
        Text(stringResource(R.string.body), style = MaterialTheme.typography.titleLarge)
    }) {
        if (state.loading) CircularProgressIndicator() else {
            BodyLines(state)
            TextButton(onClick = { showDialog = true }) { Text(stringResource(R.string.add_check_in)) }
            state.checkIns.forEach { Text(checkInLine(it)) }
        }
    }
}

@Composable
private fun BodyLines(state: ProgressUiState, prominentWeight: Boolean = false) {
    val body = state.body
    if (body == null) {
        Text(stringResource(R.string.no_check_ins), style = MaterialTheme.typography.titleLarge)
        return
    }
    Text(waistLine(body), style = MaterialTheme.typography.titleLarge)
    if (prominentWeight) {
        weightLine(body).takeIf { it.isNotEmpty() }?.let { Text(it, style = MaterialTheme.typography.titleMedium) }
    }
    rateLine(body).takeIf { it.isNotEmpty() }?.let { Text(it) }
    if (!prominentWeight) weightLine(body).takeIf { it.isNotEmpty() }?.let { Text(it) }
    state.checkIns.firstOrNull()?.let {
        Text(lastCheckInLine(java.time.LocalDate.parse(it.checkedOn), state.today),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    verdictLine(body, state.declines, state.recentDip).takeIf { it.isNotEmpty() }?.let {
        Text(it, style = MaterialTheme.typography.bodyMedium, color = when (body.verdict) {
            BodyVerdict.TOO_FAST, BodyVerdict.STALLED -> MaterialTheme.colorScheme.error
            BodyVerdict.ON_TRACK, BodyVerdict.GOAL_REACHED -> MaterialTheme.colorScheme.primary
            else -> MaterialTheme.colorScheme.onSurface
        })
    }
}

@Composable
private fun CheckInDialog(state: ProgressUiState, save: (String, String, String, String) -> Boolean, dismiss: () -> Unit) {
    var date by rememberSaveable { mutableStateOf(state.today.toString()) }
    var weight by rememberSaveable { mutableStateOf("") }
    var waist by rememberSaveable { mutableStateOf("") }
    var notes by rememberSaveable { mutableStateOf("") }
    var error by rememberSaveable { mutableStateOf(false) }
    AlertDialog(onDismissRequest = dismiss, title = { Text(stringResource(R.string.add_check_in)) }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(date, { date = it }, label = { Text(stringResource(R.string.check_in_date)) }, singleLine = true)
            OutlinedTextField(weight, { weight = it }, label = { Text(stringResource(R.string.check_in_weight)) },
                singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
            OutlinedTextField(waist, { waist = it }, label = { Text(stringResource(R.string.check_in_waist)) },
                singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
            OutlinedTextField(notes, { notes = it }, label = { Text(stringResource(R.string.notes)) })
            if (error) Text(stringResource(R.string.check_in_error), color = MaterialTheme.colorScheme.error)
        }
    }, confirmButton = {
        TextButton(onClick = { if (save(date, weight, waist, notes)) dismiss() else error = true }) {
            Text(stringResource(R.string.save))
        }
    }, dismissButton = { TextButton(onClick = dismiss) { Text(stringResource(R.string.cancel)) } })
}

@Composable
fun ExerciseProgressScreen(state: ProgressUiState, id: String, back: () -> Unit) {
    ProgressLayout(back, state.loading, title = {}) {
        if (state.loading) {
            Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        } else {
            val item = state.items.firstOrNull { it.exerciseId == id }
            if (item == null) Text(stringResource(R.string.exercise_not_found))
            else {
                Text(item.name, style = MaterialTheme.typography.headlineSmall)
                Text(groupLabel(item), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                Text(reasonText(item.reason), style = MaterialTheme.typography.bodyMedium)
                Flags(item)
                item.exposures.asReversed().forEach { exposure ->
                    Text(exposureLine(exposure) + if (exposure.sessionId in item.bestSessionIds) " · best" else "",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun Flags(item: ExerciseProgress) {
    flagText(item).forEach { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary) }
}

@Composable
private fun ProgressLayout(back: () -> Unit, loading: Boolean, title: @Composable RowScope.() -> Unit,
                           content: @Composable ColumnScope.() -> Unit) {
    Scaffold { padding ->
        val scroll = rememberScrollState()
        val body = Modifier.fillMaxSize().padding(padding).imePadding()
        Column((if (loading) body else body.verticalScroll(scroll)).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = back) { Text(stringResource(R.string.back)) }
                title()
            }
            content()
        }
    }
}
