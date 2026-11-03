package com.repknot.app

import com.repknot.data.SessionSnapshot
import com.repknot.data.SetRow
import com.repknot.domain.LoadUnit
import com.repknot.domain.PastSet
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

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

/** A recorded set is shown even when imports lack units or other values. */
fun historySetText(set: SetRow): String {
    fun number(value: Double): String = if (value % 1.0 == 0.0 && kotlin.math.abs(value) < 9e15)
        value.toLong().toString() else value.toString()
    val unit = set.unit?.takeIf { it.isNotBlank() }
    val recordedLoad = set.load
    val load = when {
        recordedLoad != null && unit == "plates" -> "${number(recordedLoad)}p"
        recordedLoad != null -> "${number(recordedLoad)} ${unit ?: "(unit unknown)"}"
        unit == "bodyweight" -> "bodyweight"
        unit != null -> "Load — $unit"
        else -> null
    }
    val amounts = listOfNotNull(set.reps?.let { "${number(it)} reps" }, set.durationS?.let { "$it s" })
    val performance = listOfNotNull(load, amounts.joinToString(" · ").ifEmpty { null }).joinToString(" × ")
    return listOfNotNull(performance.ifEmpty { "Missing values" }, set.rir?.let { "RIR $it" },
        set.notes?.takeIf { it.isNotBlank() }).joinToString(" · ")
}

/** Compact performance only; notes belong to the linked workout detail. */
fun exerciseHistoryLine(snapshot: SessionSnapshot, exerciseId: String): String {
    val sets = snapshot.exercises.filter { it.exerciseId == exerciseId }
        .sortedWith(compareBy({ it.position }, { it.id }))
        .flatMap { exercise -> snapshot.sets.filter { it.sessionExerciseId == exercise.id }
            .sortedWith(compareBy({ it.position }, { it.id })) }
    val units = sets.map { set -> LoadUnit.entries.firstOrNull { it.name.equals(set.unit, ignoreCase = true) } }
    val standard = sets.indices.all { i ->
        units[i] != null && (sets[i].load != null || units[i] == LoadUnit.BODYWEIGHT) &&
            (sets[i].reps != null) != (sets[i].durationS != null) &&
            !(units[i] == LoadUnit.BODYWEIGHT && sets[i].load != null)
    }
    val summary = if (standard) previousSummary(sets.mapIndexed { index, set ->
        PastSet(set.position, set.load, units[index]!!, set.reps, set.durationS, set.rir)
    }) else "${sets.size} ${if (sets.size == 1) "set" else "sets"} · " +
        sets.joinToString("; ") { historySetText(it.copy(notes = null)) }
    val date = runCatching { LocalDate.parse(snapshot.row.performedOn)
        .format(DateTimeFormatter.ofPattern("MMM d", Locale.ENGLISH)) }.getOrDefault(snapshot.row.performedOn)
    return "$date · $summary" + if (snapshot.row.light) " · light" else ""
}

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
