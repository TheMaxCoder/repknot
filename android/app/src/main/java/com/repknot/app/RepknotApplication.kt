package com.repknot.app

import android.app.Application
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.util.Log
import com.repknot.data.CloudAccount
import com.repknot.data.CloudState
import com.repknot.data.SessionSync
import com.repknot.data.WorkoutDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class RepknotApplication : Application() {
    val current: CurrentSession by lazy { PrefsCurrentSession(this) }
    val settings by lazy { AppSettings(this) }
    val database by lazy { WorkoutDatabase.open(this) }
    val cloud by lazy { CloudAccount(BuildConfig.SUPABASE_URL, BuildConfig.SUPABASE_PUBLISHABLE_KEY) }
    val sync by lazy { SessionSync(cloud, database.sessions(), database.reference(), database.checkIns()) { userId ->
        val prefs = getSharedPreferences("sync_account", MODE_PRIVATE)
        val owner = prefs.getString("user_id", null)
        if (owner == null) prefs.edit().putString("user_id", userId).commit() else owner == userId
    } }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
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
