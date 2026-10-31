package com.repknot.data

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
import kotlinx.serialization.Serializable

sealed interface CloudState {
    data object NotConfigured : CloudState
    data object Loading : CloudState
    data object SignedOut : CloudState
    data class SignedIn(val email: String, val userId: String) : CloudState
}

/**
 * Signed-in access to Supabase with the publishable key; row-level security limits the user
 * to their own rows. The stored auth session survives app restarts (supabase-kt default).
 * Session snapshots are synchronized separately by SessionSync.
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
            is SessionStatus.Authenticated -> CloudState.SignedIn(status.session.user?.email.orEmpty(), status.session.user?.id.orEmpty())
            is SessionStatus.Initializing -> CloudState.Loading
            else -> CloudState.SignedOut
        }
    } ?: flowOf(CloudState.NotConfigured)

    internal fun requireClient() = checkNotNull(client) { "Supabase is not configured" }

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


}

@Serializable
private data class IdRow(val id: String)
