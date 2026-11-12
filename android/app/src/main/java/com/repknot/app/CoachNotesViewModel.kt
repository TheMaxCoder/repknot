package com.repknot.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.repknot.data.CoachNoteDao
import com.repknot.data.ReferenceDao
import com.repknot.data.parseCoachNoteItems
import com.repknot.domain.CoachNoteItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

data class CoachNoteUi(val id: String, val publishedAt: String, val basedOn: String?,
    val headline: String, val items: List<CoachNoteItem>)
data class CoachNotesUiState(val note: CoachNoteUi? = null, val exerciseNames: Map<String, String> = emptyMap(),
    val unseen: Boolean = false, val done: Set<Int> = emptySet()) {
    fun activeItemsFor(exerciseId: String): List<CoachNoteItem> =
        note?.items.orEmpty().filter { it.exerciseId == exerciseId && it.index !in done }
}

class CoachNotesViewModel(private val notes: CoachNoteDao, private val reference: ReferenceDao,
    private val seen: StateFlow<String?>, private val saveSeen: (String) -> Unit,
    private val done: StateFlow<Set<String>>, private val saveDone: (String, Int, Boolean) -> Unit) : ViewModel() {
    private val mutableState = MutableStateFlow(CoachNotesUiState())
    val state: StateFlow<CoachNotesUiState> = mutableState.asStateFlow()

    init {
        viewModelScope.launch {
            val cached = notes.observeLatest().map { row ->
                val note = row?.let { CoachNoteUi(it.id, it.publishedAt, it.basedOn, it.headline, parseCoachNoteItems(it.items)) }
                val names = if (row == null) emptyMap() else reference.getExercises().associate { it.id to it.name }
                note to names
            }
            combine(cached, seen, done) { (note, names), seenId, doneKeys ->
                val indexes = note?.let { n -> doneKeys.mapNotNull { key ->
                    key.takeIf { it.startsWith("${n.id}:") }?.removePrefix("${n.id}:")?.toIntOrNull()
                }.toSet() }.orEmpty()
                CoachNotesUiState(note, names, note != null && note.id != seenId, indexes)
            }.collect { mutableState.value = it }
        }
    }

    fun markSeen() { state.value.note?.id?.let(saveSeen) }
    fun toggleDone(index: Int) { state.value.let { current -> current.note?.id?.let { saveDone(it, index, index !in current.done) } } }

    class Factory(private val notes: CoachNoteDao, private val reference: ReferenceDao,
        private val seen: StateFlow<String?>, private val saveSeen: (String) -> Unit,
        private val done: StateFlow<Set<String>>, private val saveDone: (String, Int, Boolean) -> Unit) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(CoachNotesViewModel::class.java))
            @Suppress("UNCHECKED_CAST") return CoachNotesViewModel(notes, reference, seen, saveSeen, done, saveDone) as T
        }
    }
}
