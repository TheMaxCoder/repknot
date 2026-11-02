package com.repknot.app

/**
 * Keep a valid explicit selection; otherwise return to the exercise of the most recently logged
 * set (a recreated screen must not jump away from it, task 027); otherwise choose the first
 * unfinished exercise.
 */
internal fun selectedExerciseId(selected: String?, exercises: List<ExerciseCompletion>, lastLogged: String? = null): String? =
    exercises.firstOrNull { it.id == selected }?.id
        ?: exercises.firstOrNull { it.id == lastLogged }?.id
        ?: exercises.firstOrNull { it.target == null || it.target <= 0 || it.completed < it.target }?.id
        ?: exercises.lastOrNull()?.id

internal data class ExerciseCompletion(val id: String, val target: Int?, val completed: Int)
