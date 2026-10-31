package com.repknot.app

import android.app.Application
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.util.Log
import com.repknot.data.CloudAccount
import com.repknot.data.CloudState
import com.repknot.data.RoomSetRepository
import com.repknot.data.SessionSync
import com.repknot.data.WorkoutDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class RepknotApplication : Application() {
    private val ids by lazy {
        val prefs = getSharedPreferences("workout_ids", MODE_PRIVATE)
        val previous = getDatabasePath("workouts.db").exists()
        val session = prefs.getString("session", null) ?: if (previous) "f7ff8342-691c-47ee-bd9b-c3c0892d9991" else java.util.UUID.randomUUID().toString()
        val set = prefs.getString("set", null) ?: if (previous) "9b776b65-2ab2-4c43-853d-e445428d60c8" else java.util.UUID.randomUUID().toString()
        check(prefs.edit().putString("session", session).putString("set", set).commit())
        session to set
    }
    val sessionId get() = ids.first
    val setId get() = ids.second
    val database by lazy { WorkoutDatabase.open(this) }
    val repository by lazy { RoomSetRepository(database.sets()) }
    val cloud by lazy { CloudAccount(BuildConfig.SUPABASE_URL, BuildConfig.SUPABASE_PUBLISHABLE_KEY) }
    val sync by lazy { SessionSync(cloud, database.sessions()) { userId ->
        val prefs = getSharedPreferences("sync_account", MODE_PRIVATE)
        val owner = prefs.getString("user_id", null)
        if (owner == null) prefs.edit().putString("user_id", userId).commit() else owner == userId
    } }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        ids // Decide whether this is an existing installation before Room creates its file.
        scope.launch { cloud.state.collect { if (it is CloudState.SignedIn) refresh() } }
        val connectivity = getSystemService(ConnectivityManager::class.java)
        val validation = NetworkValidation<Network>()
        connectivity.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
            override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                if (validation.changed(network, capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED))) requestRefresh()
            }
            override fun onLost(network: Network) { validation.lost(network) }
        })
    }
    fun requestRefresh() { scope.launch { refresh() } }
    private suspend fun refresh() {
        if (cloud.state.first() !is CloudState.SignedIn) return
        try { sync.refreshAndPush() } catch (e: kotlinx.coroutines.CancellationException) { throw e }
        catch (e: Exception) { Log.w("RepknotSync", "Refresh failed; retaining local data", e) }
    }
}
