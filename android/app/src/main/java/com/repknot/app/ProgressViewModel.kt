package com.repknot.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.repknot.data.CheckInDao
import com.repknot.data.CheckInRow
import com.repknot.data.ReferenceDao
import com.repknot.data.SessionDao
import com.repknot.domain.*
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ProgressUiState(
    val loading: Boolean = true,
    val items: List<ExerciseProgress> = emptyList(),
    val dipAlert: DipAlert? = null,
    val names: Map<String, String> = emptyMap(),
    val body: BodySummary? = null,
    val checkIns: List<CheckInRow> = emptyList(),
    val declines: Int = 0,
    val recentDip: Boolean = false,
    val today: LocalDate = LocalDate.now(),
)

class ProgressViewModel(
    private val sessions: SessionDao,
    private val reference: ReferenceDao,
    private val checkIns: CheckInDao,
    private val waistGoal: StateFlow<Double>,
    /** Asks the app to sync (pushes check-ins marked for upload). */
    private val requestSync: () -> Unit,
    private val today: () -> LocalDate = LocalDate::now,
) : ViewModel() {
    private val mutableState = MutableStateFlow(ProgressUiState())
    val state: StateFlow<ProgressUiState> = mutableState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(sessions.observeAllSessions(), checkIns.observeAll(), waistGoal) { _, rows, goal -> rows to goal }
                .collect { (rows, goal) ->
                    mutableState.value = withContext(Dispatchers.Default) {
                        val history = sessions.history()
                        val prescriptions = reference.activeProgram()?.workouts?.flatMap { it.exercises }.orEmpty()
                        val now = today()
                        val items = attentionOrder(progress(history, prescriptions, now))
                        val declines = items.count { it.status == ProgressStatus.POSSIBLE_DECLINE && it.daysSinceLast <= 14 }
                        val checks = rows.map { CheckIn(LocalDate.parse(it.checkedOn), it.weightKg, it.waistCm) }
                        val weightRate = if (rows.isEmpty()) null else bodySummary(checks, now, goal, declines, false).weightPctPerWeek
                        val alert = dipAlert(history, now, weightRate)
                        val recentDip = alert != null
                        ProgressUiState(
                            loading = false, items = items,
                            dipAlert = alert,
                            names = items.associate { it.exerciseId to it.name },
                            body = if (rows.isEmpty()) null else bodySummary(checks, now, goal, declines, recentDip),
                            checkIns = rows.sortedByDescending { it.checkedOn }, declines = declines,
                            recentDip = recentDip, today = now,
                        )
                    }
                }
        }
    }

    fun addCheckIn(date: String, weight: String, waist: String, notes: String): Boolean {
        val row = parseCheckIn(date, weight, waist, notes) ?: return false
        viewModelScope.launch { checkIns.add(row) }
        return true
    }
    /** The explicit Upload tap for a phone-only check-in (task 028). */
    fun uploadCheckIn(id: String) { viewModelScope.launch { if (checkIns.requestUpload(id) > 0) requestSync() } }
    fun deleteCheckIn(id: String) { viewModelScope.launch { checkIns.deleteLocal(id) } }

    class Factory(
        private val sessions: SessionDao, private val reference: ReferenceDao,
        private val checkIns: CheckInDao, private val waistGoal: StateFlow<Double>,
        private val requestSync: () -> Unit,
    ) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(ProgressViewModel::class.java))
            @Suppress("UNCHECKED_CAST") return ProgressViewModel(sessions, reference, checkIns, waistGoal, requestSync) as T
        }
    }
}

internal fun parseCheckIn(date: String, weight: String, waist: String, notes: String): CheckInRow? {
    val parsedDate = runCatching { LocalDate.parse(date) }.getOrNull() ?: return null
    fun parse(value: String): Double? = value.trim().replace(',', '.').toDoubleOrNull()?.takeIf { it > 0 && it < 1000 }
    val w = parse(weight)
    val c = parse(waist)
    if ((weight.isNotBlank() && w == null) || (waist.isNotBlank() && c == null) || (w == null && c == null)) return null
    return CheckInRow(UUID.randomUUID().toString(), parsedDate.toString(), w, c, notes = notes.trim().ifEmpty { null })
}
