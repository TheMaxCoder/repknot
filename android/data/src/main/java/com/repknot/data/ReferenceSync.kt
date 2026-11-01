package com.repknot.data

import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

internal val referenceCodec = Json { ignoreUnknownKeys = true }

@Serializable
data class ServerExercise(
    val id: String, val slug: String, val name: String,
    @SerialName("default_unit") val defaultUnit: String? = null,
    @SerialName("archived_at") val archivedAt: String? = null,
)

@Serializable
data class ServerProgram(
    val id: String, val key: String, val name: String, val version: Int,
    @SerialName("starts_on") val startsOn: String, val rules: String? = null,
    @SerialName("is_active") val isActive: Boolean,
    @SerialName("program_workouts") val workouts: List<ServerProgramWorkout>,
)

@Serializable
data class ServerProgramWorkout(
    val id: String, val key: String, val name: String,
    @SerialName("sequence_position") val sequencePosition: Int,
    @SerialName("default_weekday") val defaultWeekday: Int? = null,
    @SerialName("program_exercises") val exercises: List<ServerPrescription>,
)

@Serializable
data class ServerPrescription(
    val id: String, val position: Int, @SerialName("exercise_id") val exerciseId: String,
    val sets: Int, @SerialName("rep_min") val repMin: Int? = null,
    @SerialName("rep_max") val repMax: Int? = null,
    @SerialName("rir_min") val rirMin: Int? = null,
    @SerialName("rir_max") val rirMax: Int? = null,
    @SerialName("rest_s_min") val restSMin: Int? = null,
    @SerialName("rest_s_max") val restSMax: Int? = null,
    val load: Double? = null, val unit: String, val notes: String? = null,
)

data class ReferenceSnapshot(val exercises: List<ServerExercise>, val programs: List<ServerProgram>)

suspend fun fetchReference(cloud: CloudAccount): ReferenceSnapshot {
    val client = cloud.requireClient()
    val exercises = client.from("exercises").select().data
    val programs = client.from("programs")
        .select(Columns.raw("*,program_workouts(*,program_exercises(*))")).data
    return ReferenceSnapshot(referenceCodec.decodeFromString(exercises), referenceCodec.decodeFromString(programs))
}
