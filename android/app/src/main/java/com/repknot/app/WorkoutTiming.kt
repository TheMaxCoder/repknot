package com.repknot.app

import com.repknot.data.ProgramExerciseRow
import com.repknot.data.ProgramWorkoutRow
import com.repknot.data.SetRow

fun programRestS(workoutKey: String?, programId: String?, workouts: List<ProgramWorkoutRow>,
                 prescriptions: List<ProgramExerciseRow>, exerciseId: String): Int? {
    if (workoutKey == null || programId == null) return null
    val workout = workouts.firstOrNull { it.programId == programId && it.key == workoutKey } ?: return null
    return prescriptions.filter { it.workoutId == workout.id && it.exerciseId == exerciseId }
        .minByOrNull { it.position }?.restSMax
}

fun restTargetS(exerciseId: String, programRestS: Int?, overrides: Map<String, Int>): Int =
    overrides[exerciseId] ?: programRestS ?: 120

fun toggledRestTarget(current: Int): Int = if (current <= 120) 180 else 120

data class RestState(val sessionExerciseId: String, val elapsedS: Long)

internal fun newestTimedSet(sets: List<SetRow>): SetRow? = sets.fold(null as SetRow?) { newest, set ->
    val loggedAt = set.loggedAt
    if (loggedAt != null && (newest == null || loggedAt >= newest.loggedAt!!)) set else newest
}

fun restState(nowMillis: Long, status: String, sets: List<SetRow>): RestState? {
    if (status != "in_progress") return null
    val newest = newestTimedSet(sets) ?: return null
    val elapsed = ((nowMillis - newest.loggedAt!!) / 1000).coerceAtLeast(0)
    return if (elapsed > 900) null else RestState(newest.sessionExerciseId, elapsed)
}

fun restText(seconds: Long): String = "${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}"

fun elapsedWorkoutText(startedAt: Long?, nowMillis: Long, status: String): String? {
    if (status != "in_progress" || startedAt == null) return null
    val minutes = ((nowMillis - startedAt) / 60000).coerceAtLeast(0)
    return if (minutes < 60) "$minutes min" else "${minutes / 60} h ${minutes % 60} min"
}

/** When the current rest reaches its target; null when no rest is running in an in-progress session. */
data class RestAlertPlan(val atMillis: Long, val exerciseName: String, val targetS: Int)

fun restAlertPlan(session: WorkoutUiState.Session, overrides: Map<String, Int>): RestAlertPlan? {
    if (session.row.status != "in_progress") return null
    val newest = newestTimedSet(session.exercises.flatMap { it.sets }) ?: return null
    val exercise = session.exercises.firstOrNull { it.row.id == newest.sessionExerciseId } ?: return null
    val target = restTargetS(exercise.row.exerciseId, exercise.programRestS, overrides)
    return RestAlertPlan(newest.loggedAt!! + target * 1000L, exercise.row.exerciseName, target)
}
