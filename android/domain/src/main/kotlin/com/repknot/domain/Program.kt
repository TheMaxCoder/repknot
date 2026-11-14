package com.repknot.domain

enum class LoadUnit { KG, LB, PLATES, BODYWEIGHT }

data class Prescription(
    val position: Int, val exerciseId: String, val exerciseName: String, val sets: Int,
    val repMin: Int?, val repMax: Int?, val rirMin: Int?, val rirMax: Int?,
    val load: Double?, val unit: LoadUnit,
)
data class ProgramWorkout(val key: String, val name: String, val sequencePosition: Int,
    val exercises: List<Prescription>)
/** [versionIds]: every published version of this program (same key), including [id]; the
 *  rotation continues across versions, so republishing a program does not restart it. */
data class Program(val id: String, val workouts: List<ProgramWorkout>, val versionIds: Set<String> = setOf(id))

data class PastSet(val position: Int, val load: Double?, val unit: LoadUnit,
    val reps: Double?, val durationS: Int?, val rir: Int?)
data class PastExercise(val position: Int, val exerciseId: String, val sets: List<PastSet>,
    val exerciseName: String = "", val plannedSets: Int? = null)
data class PastSession(val id: String, val programId: String?, val programWorkoutKey: String?,
    val finishedAtMillis: Long?, val exercises: List<PastExercise>, val light: Boolean = false)

sealed interface Prefill {
    data class Previous(val sessionId: String, val sets: List<PastSet>) : Prefill
    data class StartLoad(val load: Double?, val unit: LoadUnit) : Prefill
}

fun todaysWorkout(program: Program, sessions: List<PastSession>): ProgramWorkout {
    val sequence = program.workouts.sortedBy { it.sequencePosition }
    require(sequence.isNotEmpty()) { "Program must contain a workout." }
    val keys = sequence.map { it.key }.toSet()
    val latest = sessions.withIndex()
        .filter { (_, session) ->
            session.finishedAtMillis != null && session.programId in program.versionIds &&
                session.programWorkoutKey in keys
        }
        .maxWithOrNull(compareBy({ it.value.finishedAtMillis }, { it.index }))
        ?.value ?: return sequence.first()
    val index = sequence.indexOfFirst { it.key == latest.programWorkoutKey }
    return sequence[(index + 1) % sequence.size]
}

fun prefill(prescription: Prescription, sessions: List<PastSession>): Prefill {
    val latest = sessions.withIndex()
        .filter { (_, session) ->
            session.finishedAtMillis != null && session.exercises.any { exercise ->
                exercise.exerciseId == prescription.exerciseId &&
                    exercise.sets.any { it.unit == prescription.unit }
            }
        }
        .maxWithOrNull(compareBy({ it.value.finishedAtMillis }, { it.index }))
        ?.value ?: return Prefill.StartLoad(prescription.load, prescription.unit)
    val sets = latest.exercises.asSequence()
        .filter { it.exerciseId == prescription.exerciseId }
        .sortedBy { it.position }
        .flatMap { exercise -> exercise.sets.filter { it.unit == prescription.unit }.sortedBy { it.position } }
        .toList()
    return Prefill.Previous(latest.id, sets)
}
