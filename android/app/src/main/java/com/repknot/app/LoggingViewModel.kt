package com.repknot.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.repknot.domain.LoadUnit
import com.repknot.domain.LoggedSet
import com.repknot.domain.SetRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

// Stable identifiers for the single synthetic workout. Real session creation comes later.
private const val DemoSession = "f7ff8342-691c-47ee-bd9b-c3c0892d9991"
private const val DemoSet = "9b776b65-2ab2-4c43-853d-e445428d60c8"

data class LoggingState(
    val loading: Boolean = true,
    val saved: LoggedSet? = null,
    val saving: Boolean = false,
    /** String resource ID of the current error message, if any. */
    val error: Int? = null,
)

class LoggingViewModel(private val repository: SetRepository) : ViewModel() {
    private val mutableState = MutableStateFlow(LoggingState())
    val state = mutableState.asStateFlow()

    init {
        viewModelScope.launch {
            try {
                repository.observeSet(DemoSet).collect {
                    mutableState.value = mutableState.value.copy(loading = false, saved = it)
                }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                mutableState.value = mutableState.value.copy(loading = false, error = R.string.error_read)
            }
        }
    }

    fun save(load: String, unit: LoadUnit, reps: String, rir: String) {
        if (state.value.loading || state.value.saving) return
        val values = parseSetInput(load, unit, reps, rir)
        if (values == null) {
            mutableState.value = mutableState.value.copy(error = R.string.error_invalid_input)
            return
        }
        mutableState.value = mutableState.value.copy(saving = true, error = null)
        viewModelScope.launch {
            try {
                repository.save(DemoSet, DemoSession, values)
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                mutableState.value = mutableState.value.copy(error = R.string.error_save)
            } finally {
                mutableState.value = mutableState.value.copy(saving = false)
            }
        }
    }

    class Factory(private val repository: SetRepository) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(LoggingViewModel::class.java))
            @Suppress("UNCHECKED_CAST")
            return LoggingViewModel(repository) as T
        }
    }
}
