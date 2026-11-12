package com.repknot.domain

data class CoachNoteItem(val kind: String, val text: String, val exerciseId: String?, val index: Int)
data class CoachNoteGroups(val forWorkout: List<CoachNoteItem>, val general: List<CoachNoteItem>, val other: List<CoachNoteItem>, val done: List<CoachNoteItem>)

fun groupCoachNotes(items: List<CoachNoteItem>, workoutExerciseIds: Set<String>, done: Set<Int> = emptySet()): CoachNoteGroups {
    val active = items.filter { it.index !in done }
    return CoachNoteGroups(
        active.filter { it.exerciseId != null && it.exerciseId in workoutExerciseIds },
        active.filter { it.exerciseId == null },
        active.filter { it.exerciseId != null && it.exerciseId !in workoutExerciseIds },
        items.filter { it.index in done },
    )
}
