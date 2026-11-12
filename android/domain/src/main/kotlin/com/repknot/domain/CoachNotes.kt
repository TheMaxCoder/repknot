package com.repknot.domain

data class CoachNoteItem(val kind: String, val text: String, val exerciseId: String?)
data class CoachNoteGroups(val forWorkout: List<CoachNoteItem>, val general: List<CoachNoteItem>, val other: List<CoachNoteItem>)

fun groupCoachNotes(items: List<CoachNoteItem>, workoutExerciseIds: Set<String>): CoachNoteGroups = CoachNoteGroups(
    items.filter { it.exerciseId != null && it.exerciseId in workoutExerciseIds },
    items.filter { it.exerciseId == null },
    items.filter { it.exerciseId != null && it.exerciseId !in workoutExerciseIds },
)
