package com.repknot.app

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.repknot.data.SessionDao
import com.repknot.data.SessionSnapshot
import com.repknot.data.SessionSync
import com.repknot.data.SetRow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

class SyncViewModel(private val dao: SessionDao, private val sync: SessionSync, private val sessionId: String) : ViewModel() {
    private val mutableError = MutableStateFlow<Int?>(null)
    val error = mutableError.asStateFlow()
    val exercises = dao.observeExercises(sessionId).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val sets = dao.observeSets(sessionId).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val session = dao.observe(sessionId).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    fun finish() = action { dao.finish(sessionId); sync.refreshAndPush() }
    fun saveNotes(sessionNotes: String) = action { dao.saveSessionNotes(sessionId, sessionNotes.trim().ifEmpty { null }) }
    fun keepMine() = action { sync.keepMine(sessionId) }
    fun takeTheirs() = action { sync.takeTheirs(sessionId) }
    fun share(deliver: (String) -> Unit) = action {
        val snapshot = dao.shareSnapshot(sessionId) ?: return@action
        deliver(formatSession(snapshot))
    }
    private fun action(block: suspend () -> Unit) { viewModelScope.launch {
        mutableError.value = null
        try { block() } catch (e: kotlinx.coroutines.CancellationException) { throw e }
        catch (e: Exception) {
            Log.w("RepknotSync", "Sync action failed; local data retained", e)
            mutableError.value = R.string.error_sync_action
        }
    } }
    class Factory(private val dao: SessionDao, private val sync: SessionSync, private val sessionId: String) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(SyncViewModel::class.java))
            @Suppress("UNCHECKED_CAST") return SyncViewModel(dao, sync, sessionId) as T
        }
    }
}

private fun number(value: Double): String = if (value % 1.0 == 0.0) value.toLong().toString() else value.toString()

/** One set as shared text: `25 kg × 10`, `9p × 12`, `bodyweight × 5.5`, `60 s`. */
internal fun formatSet(set: SetRow): String {
    val load = when (set.unit) {
        null -> null
        "bodyweight" -> "bodyweight"
        "plates" -> set.load?.let { "${number(it)}p" }
        else -> set.load?.let { "${number(it)} ${set.unit}" }
    }
    val amount = listOfNotNull(set.reps?.let(::number), set.durationS?.let { "$it s" }).joinToString(" · ")
    return listOfNotNull(load, amount.ifEmpty { null }).joinToString(" × ")
}

internal fun formatSession(snapshot: SessionSnapshot): String {
    val row = snapshot.row
    val date = LocalDate.parse(row.performedOn).format(DateTimeFormatter.ofPattern("EEE yyyy-MM-dd", Locale.ENGLISH))
    val setsByExercise = snapshot.sets.groupBy { it.sessionExerciseId }
    return buildString {
        append("Workout"); row.number?.let { append(" #$it") }; append(" · $date · ${row.title}\nref: ${row.id}")
        snapshot.exercises.sortedBy { it.position }.forEach { exercise ->
            append("\n${exercise.exerciseName}")
            exercise.notes?.let { append(" — $it") }
            setsByExercise[exercise.id].orEmpty().sortedBy { it.position }.forEachIndexed { index, set ->
                append("\n  ${index + 1}. ${formatSet(set)}")
                set.rir?.let { append(" · RIR $it") }; set.notes?.let { append(" — $it") }
            }
        }
        row.notes?.let { append("\nSession notes: $it") }
    }
}
