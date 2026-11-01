package com.repknot.app

/** Keep a valid explicit selection; otherwise choose the first unfinished exercise. */
internal fun selectedExerciseId(selected: String?, exercises: List<ExerciseProgress>): String? =
    exercises.firstOrNull { it.id == selected }?.id
        ?: exercises.firstOrNull { it.target == null || it.target <= 0 || it.completed < it.target }?.id
        ?: exercises.lastOrNull()?.id

internal data class ExerciseProgress(val id: String, val target: Int?, val completed: Int)
