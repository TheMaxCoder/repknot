package com.repknot.app

import com.repknot.data.SessionSnapshot

private val completedStatuses = setOf("finished", "pushed", "conflict", "conflict_deleted")

val historyOrder: Comparator<SessionSnapshot> = compareByDescending<SessionSnapshot> { it.row.performedOn }
    .thenByDescending { it.row.finishedAt ?: Long.MIN_VALUE }
    .thenByDescending { it.row.number ?: Long.MAX_VALUE }
    .thenBy { it.row.id }

fun completedHistory(snapshots: List<SessionSnapshot>): List<SessionSnapshot> = snapshots
    .filter { it.row.status in completedStatuses }
    .sortedWith(historyOrder)
    .map { snapshot -> snapshot.copy(
        exercises = snapshot.exercises.sortedWith(compareBy({ it.position }, { it.id })),
        sets = snapshot.sets.sortedWith(compareBy({ it.position }, { it.id })),
    ) }

fun historyStatus(snapshot: SessionSnapshot): String = when (snapshot.row.status) {
    "pushed" -> "Uploaded"
    "finished" -> if (snapshot.row.uploadRequested) "Waiting to upload" else "On this phone"
    "conflict" -> "Sync conflict"
    "conflict_deleted" -> "Deleted on server · local copy retained"
    else -> ""
}

data class HistoryExercise(val id: String, val name: String, val performedOn: String)

fun historyExercises(snapshots: List<SessionSnapshot>, query: String = ""): List<HistoryExercise> {
    val directory = linkedMapOf<String, HistoryExercise>()
    completedHistory(snapshots).forEach { snapshot ->
        val recorded = snapshot.sets.mapTo(hashSetOf()) { it.sessionExerciseId }
        snapshot.exercises.filter { it.id in recorded }.forEach { exercise ->
            directory.putIfAbsent(exercise.exerciseId, HistoryExercise(exercise.exerciseId, exercise.exerciseName, snapshot.row.performedOn))
        }
    }
    val term = query.trim()
    return directory.values.filter { it.name.contains(term, ignoreCase = true) }
        .sortedWith(compareByDescending<HistoryExercise> { it.performedOn }.thenBy { it.name }.thenBy { it.id })
}
