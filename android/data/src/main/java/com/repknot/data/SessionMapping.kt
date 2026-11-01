package com.repknot.data

import com.repknot.domain.PastExercise
import com.repknot.domain.PastSession
import com.repknot.domain.PastSet
import com.repknot.domain.Program
import com.repknot.domain.ProgramWorkout
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.LocalDate
import java.time.ZoneOffset

internal val codec = Json { ignoreUnknownKeys = true; encodeDefaults = true }

/** A set as the server returns and accepts it. */
@Serializable
data class ServerSet(
    val id: String, val position: Int = 0, val load: Double? = null, val unit: String? = null,
    val reps: Double? = null, @SerialName("duration_s") val durationS: Int? = null,
    val rir: Int? = null, val notes: String? = null,
)

@Serializable
data class ServerSessionExercise(
    val id: String, val position: Int = 0,
    @SerialName("exercise_id") val exerciseId: String,
    @SerialName("exercise_name") val exerciseName: String,
    @SerialName("planned_sets") val plannedSets: Int? = null,
    @SerialName("planned_rep_min") val plannedRepMin: Int? = null,
    @SerialName("planned_rep_max") val plannedRepMax: Int? = null,
    @SerialName("planned_rir_min") val plannedRirMin: Int? = null,
    @SerialName("planned_rir_max") val plannedRirMax: Int? = null,
    @SerialName("planned_load") val plannedLoad: Double? = null,
    @SerialName("planned_unit") val plannedUnit: String? = null,
    @SerialName("substituted_for") val substitutedFor: String? = null,
    val notes: String? = null,
    val sets: List<ServerSet> = emptyList(),
)

/**
 * One shape for both the table select (aliased, see [SESSION_COLUMNS]) and the
 * `session_snapshot` returned by `push_session` / `save_session`.
 */
@Serializable
data class ServerSession(
    val id: String, val number: Long, @SerialName("performed_on") val performedOn: String,
    val title: String? = null, val notes: String? = null, val revision: Long,
    @SerialName("client_revision") val clientRevision: Long? = null,
    @SerialName("program_id") val programId: String? = null,
    @SerialName("program_workout_key") val programWorkoutKey: String? = null,
    val exercises: List<ServerSessionExercise> = emptyList(),
) {
    fun json(): String = codec.encodeToString(this)
}

const val SESSION_COLUMNS = "*,exercises:session_exercises(*,sets:session_sets(*))"

/** Push payload: exercises reference the exercise by uuid; positions follow list order. */
@Serializable
data class PushExercise(
    val id: String, val exercise: String,
    @SerialName("exercise_name") val exerciseName: String,
    @SerialName("planned_sets") val plannedSets: Int?,
    @SerialName("planned_rep_min") val plannedRepMin: Int?,
    @SerialName("planned_rep_max") val plannedRepMax: Int?,
    @SerialName("planned_rir_min") val plannedRirMin: Int?,
    @SerialName("planned_rir_max") val plannedRirMax: Int?,
    @SerialName("planned_load") val plannedLoad: Double?,
    @SerialName("planned_unit") val plannedUnit: String?,
    @SerialName("substituted_for") val substitutedFor: String?,
    val notes: String?,
    val sets: List<ServerSet>,
)

@Serializable
data class PushPayload(
    val id: String, @SerialName("performed_on") val performedOn: String,
    val title: String, val notes: String?,
    @SerialName("program_id") val programId: String?,
    @SerialName("program_workout_key") val programWorkoutKey: String?,
    val exercises: List<PushExercise>,
)

fun ServerSession.exerciseRows(): List<SessionExerciseRow> =
    exercises.sortedBy { it.position }.mapIndexed { index, e ->
        SessionExerciseRow(e.id, id, index, e.exerciseId, e.exerciseName, e.plannedSets, e.plannedRepMin,
            e.plannedRepMax, e.plannedRirMin, e.plannedRirMax, e.plannedLoad, e.plannedUnit, e.substitutedFor, e.notes)
    }

fun ServerSession.setRows(): List<SetRow> = exercises.flatMap { e ->
    e.sets.sortedBy { it.position }.mapIndexed { index, s ->
        SetRow(s.id, id, e.id, index, s.load, s.unit, s.reps, s.durationS, s.rir, s.notes)
    }
}

fun SessionSnapshot.pushPayload(): PushPayload {
    val setsByExercise = sets.groupBy { it.sessionExerciseId }
    return PushPayload(row.id, row.performedOn, row.title, row.notes, row.programId, row.programWorkoutKey,
        exercises.sortedBy { it.position }.map { e ->
            PushExercise(e.id, e.exerciseId, e.exerciseName, e.plannedSets, e.plannedRepMin, e.plannedRepMax,
                e.plannedRirMin, e.plannedRirMax, e.plannedLoad, e.plannedUnit, e.substitutedFor, e.notes,
                setsByExercise[e.id].orEmpty().sortedBy { it.position }.map { s ->
                    ServerSet(s.id, s.position, s.load, s.unit, s.reps, s.durationS, s.rir, s.notes)
                })
        })
}

/** A new in-progress session whose exercises snapshot the workout's prescription. */
fun startRows(
    sessionId: String, performedOn: LocalDate, program: Program, workout: ProgramWorkout, newId: () -> String,
): Pair<SessionRow, List<SessionExerciseRow>> {
    require(program.workouts.any { it.key == workout.key }) { "Workout ${workout.key} is not in program ${program.id}" }
    val row = SessionRow(sessionId, performedOn.toString(), workout.name, null, "in_progress", 0, 0, null, null, null, null,
        program.id, workout.key)
    val exercises = workout.exercises.sortedBy { it.position }.mapIndexed { index, p ->
        SessionExerciseRow(newId(), sessionId, index, p.exerciseId, p.exerciseName, p.sets, p.repMin, p.repMax,
            p.rirMin, p.rirMax, p.load, p.unit.name.lowercase())
    }
    return row to exercises
}

/**
 * Domain history. There is no finish time on the server (every server session is finished),
 * so sessions are ordered by date, then server number (unpushed last), and `finishedAtMillis`
 * is the date: same-day ties resolve by this list order, as the domain rules specify.
 * Sets without a unit (incomplete imports) cannot be compared and are left out.
 */
fun pastSessions(rows: List<SessionRow>, exercises: List<SessionExerciseRow>, sets: List<SetRow>): List<PastSession> {
    val exercisesBySession = exercises.groupBy { it.sessionId }
    val setsByExercise = sets.groupBy { it.sessionExerciseId }
    return rows.sortedWith(compareBy<SessionRow> { it.performedOn }.thenBy(nullsLast()) { it.number }).map { row ->
        val finishedAt = if (row.status == "in_progress") null
            else LocalDate.parse(row.performedOn).atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli()
        PastSession(row.id, row.programId, row.programWorkoutKey, finishedAt,
            exercisesBySession[row.id].orEmpty().map { e ->
                PastExercise(e.position, e.exerciseId, setsByExercise[e.id].orEmpty().mapNotNull { s ->
                    s.unit?.let { PastSet(s.position, s.load, parseUnit(it), s.reps, s.durationS, s.rir) }
                })
            })
    }
}
