package com.repknot.app

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.repknot.data.CloudAccount
import com.repknot.data.CloudState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CloudUiState(
    val cloud: CloudState = CloudState.Loading,
    val busy: Boolean = false,
    val sessionCount: Int? = null,
    /** String resource ID of the current error message, if any. */
    val error: Int? = null,
)

private const val TAG = "RepknotCloud"

/** Sign-in and the count of sessions visible to the current account. */
class CloudViewModel(private val cloud: CloudAccount) : ViewModel() {
    private val mutableState = MutableStateFlow(CloudUiState())
    val state = mutableState.asStateFlow()

    init {
        viewModelScope.launch {
            cloud.state.collect { cloudState ->
                mutableState.update { it.copy(cloud = cloudState) }
                if (cloudState is CloudState.SignedIn) refreshCount()
                else mutableState.update { it.copy(sessionCount = null) }
            }
        }
    }

    fun signIn(email: String, password: String) = run(R.string.error_sign_in) {
        cloud.signIn(email.trim(), password)
    }

    fun signOut() = run(R.string.error_cloud) { cloud.signOut() }

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

    class Factory(private val cloud: CloudAccount) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(CloudViewModel::class.java))
            @Suppress("UNCHECKED_CAST")
            return CloudViewModel(cloud) as T
        }
    }
}
