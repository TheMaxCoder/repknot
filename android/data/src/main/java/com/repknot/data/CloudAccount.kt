package com.repknot.data

import com.repknot.domain.SetValues
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.LocalDate

sealed interface CloudState {
    data object NotConfigured : CloudState
    data object Loading : CloudState
    data object SignedOut : CloudState
    data class SignedIn(val email: String) : CloudState
}

/**
 * Signed-in access to Supabase with the publishable key; row-level security limits the user
 * to their own rows. The stored auth session survives app restarts (supabase-kt default).
 * Task 002 connectivity only — session sync comes with task 003 (ADR 001).
 */
class CloudAccount(url: String, publishableKey: String) {
    private val client: SupabaseClient? =
        if (url.isBlank() || publishableKey.isBlank()) null
        else createSupabaseClient(url, publishableKey) {
            install(Auth)
            install(Postgrest)
        }

    val state: Flow<CloudState> = client?.auth?.sessionStatus?.map { status ->
        when (status) {
            is SessionStatus.Authenticated -> CloudState.SignedIn(status.session.user?.email.orEmpty())
            is SessionStatus.Initializing -> CloudState.Loading
            else -> CloudState.SignedOut
        }
    } ?: flowOf(CloudState.NotConfigured)

    private fun requireClient() = checkNotNull(client) { "Supabase is not configured" }

    suspend fun signIn(email: String, password: String) {
        requireClient().auth.signInWith(Email) {
            this.email = email
            this.password = password
        }
    }

    suspend fun signOut() = requireClient().auth.signOut()

    /** Number of sessions visible to the signed-in user. */
    suspend fun sessionCount(): Int =
        requireClient().from("workout_sessions").select(Columns.list("id")).decodeList<IdRow>().size

    /** Writes a synthetic session holding [values] and returns its server-assigned number. */
    suspend fun writeTestSession(values: SetValues): Long {
        val db = requireClient()
        val session = db.from("workout_sessions").insert(
            NewSession(LocalDate.now().toString(), "Android connectivity check (synthetic)", "android"),
        ) { select() }.decodeSingle<SessionRow>()
        db.from("session_sets").insert(
            NewSet(session.id, 0, "Dumbbell bench press", values.load, values.unit.name.lowercase(), values.reps, values.rir),
        )
        return session.number
    }
}

@Serializable
private data class IdRow(val id: String)

@Serializable
private data class NewSession(
    @SerialName("performed_on") val performedOn: String,
    val title: String,
    val source: String,
)

@Serializable
private data class SessionRow(val id: String, val number: Long)

@Serializable
private data class NewSet(
    @SerialName("session_id") val sessionId: String,
    val position: Int,
    @SerialName("exercise_name") val exerciseName: String,
    val load: Double,
    val unit: String,
    val reps: Int,
    val rir: Int?,
)
