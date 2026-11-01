package com.repknot.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.repknot.data.ReferenceDao
import com.repknot.data.SessionDao
import com.repknot.domain.ExerciseProgress
import com.repknot.domain.SessionDip
import com.repknot.domain.attentionOrder
import com.repknot.domain.progress
import com.repknot.domain.sessionDips
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ProgressUiState(
    val loading: Boolean = true,
    val items: List<ExerciseProgress> = emptyList(),
    val dips: List<SessionDip> = emptyList(),
    val names: Map<String, String> = emptyMap(),
)

class ProgressViewModel(
    private val sessions: SessionDao,
    private val reference: ReferenceDao,
    private val today: () -> LocalDate = LocalDate::now,
) : ViewModel() {
    private val mutableState = MutableStateFlow(ProgressUiState())
    val state: StateFlow<ProgressUiState> = mutableState.asStateFlow()

    init {
        viewModelScope.launch {
            sessions.observeAllSessions().collect {
                mutableState.value = withContext(Dispatchers.Default) {
                    val history = sessions.history()
                    val prescriptions = reference.activeProgram()?.workouts?.flatMap { it.exercises }.orEmpty()
                    val now = today()
                    val items = attentionOrder(progress(history, prescriptions, now))
                    ProgressUiState(
                        loading = false,
                        items = items,
                        dips = sessionDips(history).filter { it.date >= now.minusDays(28) && it.date <= now }
                            .sortedByDescending { it.date },
                        names = items.associate { it.exerciseId to it.name },
                    )
                }
            }
        }
    }

    class Factory(private val sessions: SessionDao, private val reference: ReferenceDao) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(ProgressViewModel::class.java))
            @Suppress("UNCHECKED_CAST") return ProgressViewModel(sessions, reference) as T
        }
    }
}
