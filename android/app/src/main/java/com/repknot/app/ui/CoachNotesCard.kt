package com.repknot.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.repknot.app.CoachNotesUiState
import com.repknot.app.R
import com.repknot.app.coachDate
import com.repknot.app.coachNoteDate
import com.repknot.domain.CoachNoteItem
import com.repknot.domain.groupCoachNotes
import java.time.LocalDate
import java.time.ZoneId

@Composable
fun CoachNotesCard(state: CoachNotesUiState, workoutName: String, exerciseIds: Set<String>, markSeen: () -> Unit, toggleDone: (Int) -> Unit) {
    val note = state.note ?: return
    var expanded by rememberSaveable(note.id) { mutableStateOf(true) }
    LaunchedEffect(note.id, expanded) { if (expanded) markSeen() }
    val groups = groupCoachNotes(note.items, exerciseIds, state.done)
    val today = LocalDate.now()
    val date = coachNoteDate(note.publishedAt, ZoneId.systemDefault(), today)
    val description = stringResource(if (expanded) R.string.coach_expanded else R.string.coach_collapsed)
    val rotation by animateFloatAsState(if (expanded) 180f else 0f, label = "coach chevron")
    Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).semantics { stateDescription = description }
                .clickable(role = Role.Button) { expanded = !expanded; if (expanded) markSeen() },
                horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.size(36.dp)) {
                    Box(contentAlignment = Alignment.Center) { Icon(painterResource(R.drawable.ic_coach_notes), null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(24.dp)) }
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(if (date == null) stringResource(R.string.coach_notes) else stringResource(R.string.coach_notes_dated, date),
                            style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f, fill = false))
                        if (state.unseen) Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.primaryContainer) {
                            Text(stringResource(R.string.coach_new), Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                color = MaterialTheme.colorScheme.onPrimaryContainer, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                    Text(note.headline, style = MaterialTheme.typography.titleMedium)
                }
                Icon(painterResource(R.drawable.ic_expand_more), null, modifier = Modifier.rotate(rotation))
            }
            if (!expanded) {
                val activeCount = note.items.size - groups.done.size
                val count = if (activeCount == 0 && groups.done.isNotEmpty()) pluralStringResource(R.plurals.coach_all_done, groups.done.size, groups.done.size)
                    else pluralStringResource(R.plurals.coach_note_count, activeCount, activeCount)
                val relevant = if (groups.forWorkout.isNotEmpty()) " · " + stringResource(R.string.coach_for_count, groups.forWorkout.size, workoutName) else ""
                val doneText = if (activeCount > 0 && groups.done.isNotEmpty()) " · " + stringResource(R.string.coach_done_count, groups.done.size) else ""
                Text(count + relevant + doneText, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            AnimatedVisibility(expanded, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    val sections = listOf(R.string.coach_for_workout to groups.forWorkout, R.string.coach_general to groups.general, R.string.coach_other to groups.other, R.string.coach_done to groups.done).filter { it.second.isNotEmpty() }
                    sections.forEachIndexed { index, (title, items) ->
                        if (index > 0) HorizontalDivider()
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            if (sections.size >= 2) Text(if (title == R.string.coach_for_workout) stringResource(title, workoutName) else stringResource(title),
                                style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            items.forEach { CoachNoteItemRow(it, state.exerciseNames, it.index in state.done) { toggleDone(it.index) } }
                        }
                    }
                    val based = note.basedOn?.let { runCatching { coachDate(LocalDate.parse(it), today) }.getOrNull() }
                    if (based != null) Text(stringResource(R.string.coach_based_on, based), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun CoachNoteItemRow(item: CoachNoteItem, names: Map<String, String>, done: Boolean, toggleDone: () -> Unit) {
    val (icon, label, container, tint) = coachNoteStyle(item.kind)
    Row(Modifier.alpha(if (done) 0.5f else 1f), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
        Surface(shape = CircleShape, color = container, modifier = Modifier.size(32.dp)) {
            Box(contentAlignment = Alignment.Center) { Icon(painterResource(icon), stringResource(label), tint = tint, modifier = Modifier.size(20.dp)) }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            item.exerciseId?.let { names[it] }?.let { Text(it, style = MaterialTheme.typography.labelLarge) }
            Text(item.text, style = MaterialTheme.typography.bodyMedium)
        }
        IconButton(onClick = toggleDone, modifier = Modifier.size(48.dp)) {
            Icon(painterResource(R.drawable.ic_note_done), stringResource(if (done) R.string.coach_mark_not_done else R.string.coach_mark_done))
        }
    }
}
