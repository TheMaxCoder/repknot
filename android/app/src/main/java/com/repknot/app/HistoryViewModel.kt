package com.repknot.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.repknot.data.SessionDao
import com.repknot.data.SessionSnapshot
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

data class HistoryUiState(val loading: Boolean = true, val error: Boolean = false, val sessions: List<SessionSnapshot> = emptyList())

class HistoryViewModel(private val sessions: SessionDao) : ViewModel() {
    private val mutableState = MutableStateFlow(HistoryUiState())
    val state = mutableState.asStateFlow()
    init {
        viewModelScope.launch {
            try {
                combine(sessions.observeAllSessions(), sessions.observeAllExercises(), sessions.observeAllSets()) { _, _, _ -> Unit }
                    .collect {
                        try { mutableState.value = HistoryUiState(loading = false, sessions = completedHistory(sessions.allSnapshots())) }
                        catch (e: CancellationException) { throw e }
                        catch (e: Exception) { mutableState.value = HistoryUiState(loading = false, error = true) }
                    }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { mutableState.value = HistoryUiState(loading = false, error = true) }
        }
    }
    class Factory(private val sessions: SessionDao) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(HistoryViewModel::class.java))
            @Suppress("UNCHECKED_CAST") return HistoryViewModel(sessions) as T
        }
    }
}
