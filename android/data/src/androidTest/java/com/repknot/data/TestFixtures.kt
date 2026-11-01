package com.repknot.data

/** A local draft session like the app creates before any set is logged. */
fun draft(id: String, date: String = "2026-10-31") =
    SessionRow(id, date, "Push", null, "in_progress", 0, 0, null, null, null, null)

/**
 * Logs (or overwrites) one set in a session's single exercise, creating the draft and the
 * exercise if needed — the nested replacement for the old one-set `SetDao.save`.
 */
suspend fun WorkoutDatabase.logSet(
    sessionId: String, setId: String, load: Double, exerciseId: String = "bench-id", unit: String = "kg",
) {
    val dao = sessions()
    dao.insertIfAbsent(draft(sessionId))
    val exercise = dao.exercises(sessionId).firstOrNull()
        ?: SessionExerciseRow(java.util.UUID.randomUUID().toString(), sessionId, 0, exerciseId, "Bench").also { dao.putExercises(listOf(it)) }
    dao.saveSet(SetRow(setId, sessionId, exercise.id, 0, load, unit, 7.0, rir = 3))
}
