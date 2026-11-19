package com.repknot.data

private fun List<SessionExerciseRow>.renumbered() = mapIndexed { index, row -> row.copy(position = index) }

fun moveTo(rows: List<SessionExerciseRow>, id: String, toIndex: Int): List<SessionExerciseRow> {
    val index = rows.indexOfFirst { it.id == id }
    if (index < 0) return rows.renumbered()
    val result = rows.toMutableList()
    val row = result.removeAt(index)
    result.add(toIndex.coerceIn(0, result.size), row)
    return result.renumbered()
}

fun insertAt(rows: List<SessionExerciseRow>, row: SessionExerciseRow, index: Int): List<SessionExerciseRow> =
    rows.toMutableList().apply { add(index.coerceIn(0, size), row) }.renumbered()

fun remove(rows: List<SessionExerciseRow>, id: String): List<SessionExerciseRow> =
    rows.filterNot { it.id == id }.renumbered()

fun replace(rows: List<SessionExerciseRow>, id: String, exerciseId: String, exerciseName: String): List<SessionExerciseRow> =
    rows.map { row ->
        if (row.id != id) row else {
            val original = row.substitutedFor ?: row.exerciseId
            row.copy(exerciseId = exerciseId, exerciseName = exerciseName,
                substitutedFor = if (exerciseId == original) null else original)
        }
    }.renumbered()
