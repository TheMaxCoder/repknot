package com.repknot.app

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.repknot.data.CloudAccount
import com.repknot.data.CloudState
import com.repknot.domain.SetRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CloudUiState(
    val cloud: CloudState = CloudState.Loading,
    val busy: Boolean = false,
    val sessionCount: Int? = null,
    val lastCreatedNumber: Long? = null,
    /** String resource ID of the current error message, if any. */
    val error: Int? = null,
)

private const val TAG = "RepknotCloud"

/** Task 002 connectivity check: sign in, count sessions, write one synthetic session. */
class CloudViewModel(private val cloud: CloudAccount, private val sets: SetRepository) : ViewModel() {
    private val mutableState = MutableStateFlow(CloudUiState())
    val state = mutableState.asStateFlow()

    init {
        viewModelScope.launch {
            cloud.state.collect { cloudState ->
                mutableState.update { it.copy(cloud = cloudState) }
                if (cloudState is CloudState.SignedIn) refreshCount()
                else mutableState.update { it.copy(sessionCount = null, lastCreatedNumber = null) }
            }
        }
    }

    fun signIn(email: String, password: String) = run(R.string.error_sign_in) {
        cloud.signIn(email.trim(), password)
    }

    fun signOut() = run(R.string.error_cloud) { cloud.signOut() }

    fun uploadTestSession() = run(R.string.error_cloud) {
        val saved = sets.observeSet(DemoSet).first()
        if (saved == null) {
            mutableState.update { it.copy(error = R.string.error_no_local_set) }
            return@run
        }
        val number = cloud.writeTestSession(saved.values)
        mutableState.update { it.copy(lastCreatedNumber = number) }
        refreshCount()
    }

    private suspend fun refreshCount() {
        try {
            val count = cloud.sessionCount()
            mutableState.update { it.copy(sessionCount = count) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Session count failed", e)
            mutableState.update { it.copy(error = R.string.error_cloud) }
        }
    }

    private fun run(errorMessage: Int, block: suspend () -> Unit) {
        if (state.value.busy) return
        mutableState.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Cloud action failed", e)
                mutableState.update { it.copy(error = errorMessage) }
            } finally {
                mutableState.update { it.copy(busy = false) }
            }
        }
    }

    class Factory(private val cloud: CloudAccount, private val sets: SetRepository) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(CloudViewModel::class.java))
            @Suppress("UNCHECKED_CAST")
            return CloudViewModel(cloud, sets) as T
        }
    }
}
