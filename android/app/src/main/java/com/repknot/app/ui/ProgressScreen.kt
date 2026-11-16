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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.input.KeyboardType
import com.repknot.app.*
import com.repknot.app.R
import com.repknot.app.dipAlertText
import com.repknot.domain.DipAlertKind
import com.repknot.app.exposureLine
import com.repknot.app.flagText
import com.repknot.app.groupLabel
import com.repknot.app.reasonText
import com.repknot.domain.ExerciseProgress
import com.repknot.domain.BodyVerdict

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProgressScreen(state: ProgressUiState, openBody: () -> Unit,
                   addCheckIn: (String, String, String, String) -> Boolean, openExercise: (String) -> Unit) {
    var showDialog by rememberSaveable { mutableStateOf(false) }
    if (showDialog) CheckInDialog(state, addCheckIn) { showDialog = false }
    ProgressLayout(state.loading, title = {
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
                            BodySummaryCardLines(state)
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        FlowRow(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween, verticalArrangement = Arrangement.Center,
                            itemVerticalAlignment = Alignment.CenterVertically) {
                            state.checkIns.firstOrNull()?.let {
                                Text(lastCheckInLine(java.time.LocalDate.parse(it.checkedOn), state.today),
                                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            TextButton(onClick = { showDialog = true }) {
                                Text(stringResource(R.string.add_check_in))
                            }
                        }
                    }
                }
                if (state.items.isEmpty()) Text(stringResource(R.string.no_finished_workouts))
                state.dipAlert?.let { alert ->
                    val neutral = alert.kind == DipAlertKind.NOTICE
                    Card(colors = CardDefaults.cardColors(
                        containerColor = if (neutral) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.errorContainer,
                        contentColor = if (neutral) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onErrorContainer), modifier = Modifier.fillMaxWidth()) {
                        Text(dipAlertText(alert, state.names), Modifier.padding(16.dp))
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
fun BodyScreen(state: ProgressUiState, showDialog: Boolean, setShowDialog: (Boolean) -> Unit,
               addCheckIn: (String, String, String, String) -> Boolean, upload: (String) -> Unit, delete: (String) -> Unit) {
    if (showDialog) CheckInDialog(state, addCheckIn) { setShowDialog(false) }
    ProgressLayout(state.loading, title = {
        Text(stringResource(R.string.body), style = MaterialTheme.typography.titleLarge)
    }) {
        if (state.loading) CircularProgressIndicator() else {
            BodyLines(state)
            TextButton(onClick = { setShowDialog(true) }) { Text(stringResource(R.string.add_check_in)) }
            state.checkIns.forEach { row ->
                Text(checkInLine(row))
                // Phone-only check-ins reach the server only after Upload is tapped (task 028).
                if (row.pending && !row.uploadRequested) Row {
                    TextButton(onClick = { upload(row.id) }) { Text(stringResource(R.string.upload)) }
                    TextButton(onClick = { delete(row.id) }) { Text(stringResource(R.string.delete)) }
                }
            }
        }
    }
}

@Composable
private fun BodyLines(state: ProgressUiState) {
    val body = state.body
    if (body == null) {
        Text(stringResource(R.string.no_check_ins), style = MaterialTheme.typography.titleLarge)
        return
    }
    Text(waistLine(body), style = MaterialTheme.typography.titleLarge)
    rateLine(body).takeIf { it.isNotEmpty() }?.let { Text(it) }
    weightLine(body).takeIf { it.isNotEmpty() }?.let { Text(it) }
    BodyDetails(state)
}

@Composable
private fun BodySummaryCardLines(state: ProgressUiState) {
    val body = state.body
    if (body == null) {
        Text(stringResource(R.string.no_check_ins), style = MaterialTheme.typography.titleLarge)
        return
    }
    val waist: @Composable () -> Unit = {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("Waist", style = MaterialTheme.typography.labelLarge)
            body.waist?.let {
                Text(bodyMeasurement(it.latest, "cm"), style = MaterialTheme.typography.headlineSmall)
                waistCaption(body).takeUnless { caption -> caption == "Waist" }?.let { caption ->
                    Text(caption.removePrefix("Waist · "), style = MaterialTheme.typography.bodySmall)
                }
            } ?: Text("No waist measurement yet", style = MaterialTheme.typography.bodySmall)
        }
    }
    val weight: @Composable () -> Unit = {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("Weight", style = MaterialTheme.typography.labelLarge)
            body.weight?.let {
                Text(bodyMeasurement(it.latest, "kg"), style = MaterialTheme.typography.headlineSmall)
            } ?: Text("No weight measurement yet", style = MaterialTheme.typography.bodySmall)
        }
    }
    val waistRate: @Composable () -> Unit = {
        body.waist?.let {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("Last 4 weeks", style = MaterialTheme.typography.labelSmall)
                Text(summaryRate(it.perWeek, "cm"), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
    val weightRate: @Composable () -> Unit = {
        body.weight?.let {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("Last 4 weeks", style = MaterialTheme.typography.labelSmall)
                Text(summaryRate(it.perWeek, "kg"), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val fontScale = LocalDensity.current.fontScale
        if (maxWidth.value / fontScale >= 250f) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(Modifier.weight(1f)) { waist() }
                    Box(Modifier.weight(1f)) { weight() }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(Modifier.weight(1f)) { waistRate() }
                    Box(Modifier.weight(1f)) { weightRate() }
                }
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) { waist(); waistRate() }
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) { weight(); weightRate() }
            }
        }
    }
    goalEstimate(body).takeIf { it.isNotEmpty() }?.let {
        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    verdictLine(body, state.declines, state.recentDip).takeIf { it.isNotEmpty() }?.let {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Text(it, style = MaterialTheme.typography.bodyMedium, color = when (body.verdict) {
            BodyVerdict.TOO_FAST, BodyVerdict.STALLED -> MaterialTheme.colorScheme.error
            BodyVerdict.ON_TRACK, BodyVerdict.GOAL_REACHED -> MaterialTheme.colorScheme.primary
            else -> MaterialTheme.colorScheme.onSurface
        })
    }
}

@Composable
private fun BodyDetails(state: ProgressUiState) {
    val body = state.body ?: return
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
private fun Flags(item: ExerciseProgress) {
    flagText(item).forEach { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary) }
}

@Composable
private fun ProgressLayout(loading: Boolean, title: @Composable RowScope.() -> Unit,
                           content: @Composable ColumnScope.() -> Unit) {
    Scaffold { padding ->
        val scroll = rememberScrollState()
        val body = Modifier.fillMaxSize().padding(padding).imePadding()
        Column((if (loading) body else body.verticalScroll(scroll)).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                title()
            }
            content()
        }
    }
}
