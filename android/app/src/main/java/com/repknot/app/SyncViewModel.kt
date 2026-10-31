package com.repknot.app

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.repknot.data.SessionDao
import com.repknot.data.SessionSync
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

class SyncViewModel(private val dao: SessionDao, private val sync: SessionSync, private val sessionId: String, private val setId: String) : ViewModel() {
    private val mutableError = MutableStateFlow<Int?>(null)
    val error = mutableError.asStateFlow()
    val sets = dao.observeSets(sessionId).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val session = dao.observe(sessionId).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    fun finish() = action { dao.finish(sessionId); sync.refreshAndPush() }
    fun saveNotes(sessionNotes: String, setNotes: String) = action {
        dao.saveNotes(sessionId, setId, sessionNotes.trim().ifEmpty { null }, setNotes.trim().ifEmpty { null })
    }
    fun keepMine() = action { sync.keepMine(sessionId) }
    fun takeTheirs() = action { sync.takeTheirs(sessionId) }
    fun share(deliver: (String) -> Unit) = action {
        val (row, sets) = dao.shareSnapshot(sessionId) ?: return@action
        val text = formatSession(row, sets)
        deliver(text)
    }
    private fun action(block: suspend () -> Unit) { viewModelScope.launch {
        mutableError.value = null
        try { block() } catch (e: kotlinx.coroutines.CancellationException) { throw e }
        catch (e: Exception) {
            Log.w("RepknotSync", "Sync action failed; local data retained", e)
            mutableError.value = R.string.error_sync_action
        }
    } }
    class Factory(private val dao: SessionDao, private val sync: SessionSync, private val sessionId: String, private val setId: String) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(SyncViewModel::class.java))
            @Suppress("UNCHECKED_CAST") return SyncViewModel(dao, sync, sessionId, setId) as T
        }
    }
}

internal fun formatSession(row: com.repknot.data.SessionRow, sets: List<com.repknot.data.SetRow>): String {
    val date = LocalDate.parse(row.performedOn).format(DateTimeFormatter.ofPattern("EEE yyyy-MM-dd", Locale.ENGLISH))
    return buildString {
            append("Workout"); row.number?.let { append(" #$it") }; append(" · $date · ${row.title}\nref: ${row.id}")
            sets.groupBy { it.exerciseName }.forEach { (name, values) ->
                append("\n$name")
                values.forEachIndexed { index, set ->
                    append("\n  ${index + 1}. ${set.load} ${set.unit.lowercase()} × ${set.reps}")
                    set.rir?.let { append(" · RIR $it") }; set.notes?.let { append(" — $it") }
                }
            }
            row.notes?.let { append("\nSession notes: $it") }
        }
}
