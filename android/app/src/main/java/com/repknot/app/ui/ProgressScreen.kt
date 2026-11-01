package com.repknot.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.repknot.app.ProgressUiState
import com.repknot.app.R
import com.repknot.app.dipText
import com.repknot.app.exposureLine
import com.repknot.app.flagText
import com.repknot.app.groupLabel
import com.repknot.app.reasonText
import com.repknot.domain.ExerciseProgress

@Composable
fun ProgressScreen(state: ProgressUiState, back: () -> Unit, openExercise: (String) -> Unit) {
    ProgressLayout(back, state.loading, title = {
        Text(stringResource(R.string.progress), style = MaterialTheme.typography.titleLarge)
    }) {
        when {
            state.loading -> Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            state.items.isEmpty() -> Text(stringResource(R.string.no_finished_workouts))
            else -> {
                state.dips.forEach { dip ->
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer), modifier = Modifier.fillMaxWidth()) {
                        Text(dipText(dip, state.names), Modifier.padding(16.dp))
                    }
                }
                var previousGroup: String? = null
                state.items.forEach { item ->
                    val group = groupLabel(item)
                    if (group != previousGroup) {
                        Text(group, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                        previousGroup = group
                    }
                    Card(Modifier.fillMaxWidth().clickable(role = Role.Button) { openExercise(item.exerciseId) }) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(item.name, style = MaterialTheme.typography.titleMedium)
                            Text(reasonText(item.reason), style = MaterialTheme.typography.bodyMedium,
                                color = when (group) {
                                    "Needs a look" -> MaterialTheme.colorScheme.error
                                    "Improving" -> MaterialTheme.colorScheme.primary
                                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                                })
                            Flags(item)
                            item.exposures.takeLast(3).asReversed().forEach { exposure ->
                                Text(exposureLine(exposure), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
    }
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
