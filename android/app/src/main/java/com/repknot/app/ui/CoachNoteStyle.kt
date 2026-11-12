package com.repknot.app.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.repknot.app.R

internal data class NoteStyle(val icon: Int, val label: Int, val container: Color, val tint: Color)

@Composable
internal fun coachNoteStyle(kind: String): NoteStyle {
    val colors = MaterialTheme.colorScheme
    return when (kind) {
        "progress" -> NoteStyle(R.drawable.ic_note_progress, R.string.coach_progress, colors.primaryContainer, colors.onPrimaryContainer)
        "hold" -> NoteStyle(R.drawable.ic_note_hold, R.string.coach_hold, colors.surfaceContainerHighest, colors.onSurfaceVariant)
        "technique" -> NoteStyle(R.drawable.ic_note_technique, R.string.coach_technique, colors.secondaryContainer, colors.onSecondaryContainer)
        "recovery" -> NoteStyle(R.drawable.ic_note_recovery, R.string.coach_recovery, colors.tertiaryContainer, colors.onTertiaryContainer)
        else -> NoteStyle(R.drawable.ic_note_focus, R.string.coach_focus, colors.surfaceContainerHighest, colors.primary)
    }
}
