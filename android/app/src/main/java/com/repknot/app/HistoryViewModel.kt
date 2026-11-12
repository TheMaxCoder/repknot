package com.repknot.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.repknot.data.SessionDao
import com.repknot.data.SessionSnapshot
import com.repknot.domain.PastSession
import com.repknot.domain.sessionDips
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

data class HistoryUiState(val loading: Boolean = true, val error: Boolean = false, val sessions: List<SessionSnapshot> = emptyList(),
    val dipSessionIds: Set<String> = emptySet())

internal fun historyDipSessionIds(history: List<PastSession>): Set<String> = sessionDips(history).map { it.sessionId }.toSet()

class HistoryViewModel(private val sessions: SessionDao) : ViewModel() {
    private val mutableState = MutableStateFlow(HistoryUiState())
    val state = mutableState.asStateFlow()
    init {
        viewModelScope.launch {
            try {
                combine(sessions.observeAllSessions(), sessions.observeAllExercises(), sessions.observeAllSets()) { _, _, _ -> Unit }
                    .collect {
                        try { mutableState.value = HistoryUiState(loading = false, sessions = completedHistory(sessions.allSnapshots()),
                            dipSessionIds = historyDipSessionIds(sessions.history())) }
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
