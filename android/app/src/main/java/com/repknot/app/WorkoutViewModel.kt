package com.repknot.app

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.repknot.data.*
import com.repknot.domain.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID

sealed interface WorkoutUiState {
    data object Loading : WorkoutUiState
    data object NoProgram : WorkoutUiState
    data class Today(val programName: String, val suggested: ProgramWorkout, val workouts: List<ProgramWorkout>) : WorkoutUiState
    data class Session(val row: SessionRow, val exercises: List<ExerciseUi>, val choices: List<ExerciseRow>) : WorkoutUiState
}
data class ExerciseUi(val row: SessionExerciseRow, val sets: List<SetRow>, val previous: Prefill, val suggestion: SetSuggestion)
data class SetSuggestion(val load: Double?, val unit: LoadUnit, val reps: Double?)

fun todayState(program: Program?, programName: String?, history: List<PastSession>): WorkoutUiState =
    if (program == null) WorkoutUiState.NoProgram else WorkoutUiState.Today(
        programName ?: "Program", todaysWorkout(program, history), program.workouts.sortedBy { it.sequencePosition })

fun prescriptionFor(row: SessionExerciseRow, defaultUnit: LoadUnit): Prescription = Prescription(
    row.position, row.exerciseId, row.exerciseName, row.plannedSets ?: 0, row.plannedRepMin, row.plannedRepMax,
    row.plannedRirMin, row.plannedRirMax, row.plannedLoad, row.plannedUnit?.let(::parseUnit) ?: defaultUnit)

fun suggestionFor(prefill: Prefill, setIndex: Int, row: SessionExerciseRow, defaultUnit: LoadUnit): SetSuggestion {
    val suggestion = when (prefill) {
        is Prefill.Previous -> {
            val set = prefill.sets.getOrNull(setIndex) ?: prefill.sets.lastOrNull()
            if (set == null) SetSuggestion(row.plannedLoad, defaultUnit, row.plannedRepMax?.toDouble())
            else SetSuggestion(set.load, set.unit, set.reps)
        }
        is Prefill.StartLoad -> SetSuggestion(prefill.load, prefill.unit, row.plannedRepMax?.toDouble())
    }
    return if (suggestion.unit == LoadUnit.BODYWEIGHT) suggestion.copy(load = null) else suggestion
}

fun sessionState(snapshot: SessionSnapshot, history: List<PastSession>, exercises: List<ExerciseRow>): WorkoutUiState.Session {
    val byId = exercises.associateBy { it.id }
    val sets = snapshot.sets.groupBy { it.sessionExerciseId }
    val previousHistory = history.filterNot { it.id == snapshot.row.id }
    return WorkoutUiState.Session(snapshot.row, snapshot.exercises.sortedBy { it.position }.map { row ->
        val unit = row.plannedUnit?.let(::parseUnit) ?: byId[row.exerciseId]?.defaultUnit?.let(::parseUnit) ?: LoadUnit.KG
        val logged = sets[row.id].orEmpty().sortedBy { it.position }
        val previous = prefill(prescriptionFor(row, unit), previousHistory)
        ExerciseUi(row, logged, previous, suggestionFor(previous, logged.size, row, unit))
    }, exercises)
}

@OptIn(ExperimentalCoroutinesApi::class)
class WorkoutViewModel(
    private val sessions: SessionDao, private val reference: ReferenceDao, private val sync: SessionSync,
    private val current: CurrentSession, private val today: () -> LocalDate = LocalDate::now,
) : ViewModel() {
    private val currentId = MutableStateFlow(current.get())
    private val mutableState = MutableStateFlow<WorkoutUiState>(WorkoutUiState.Loading)
    val state: StateFlow<WorkoutUiState> = mutableState.asStateFlow()
    private val mutableError = MutableStateFlow<Int?>(null)
    val error: StateFlow<Int?> = mutableError.asStateFlow()
    private val actionMutex = Mutex()

    init {
        viewModelScope.launch {
            currentId.flatMapLatest { id ->
                val snapshot = if (id == null) flowOf(null) else sessions.observeSession(id)
                combine(snapshot, reference.observeActiveProgramRow()) { session, program -> Triple(id, session, program) }
            }.collect { (id, snapshot, programRow) ->
                if (id != null && snapshot == null) {
                    current.set(null)
                    currentId.value = null
                }
                val history = sessions.history()
                mutableState.value = if (snapshot == null) todayState(reference.activeProgram(), programRow?.name, history)
                    else sessionState(snapshot, history, reference.activeExercises())
            }
        }
    }

    fun start(workoutKey: String) = action {
        if (current.get() != null) return@action
        val program = reference.activeProgram() ?: return@action
        val workout = program.workouts.firstOrNull { it.key == workoutKey } ?: return@action
        val id = uuid()
        val (row, exercises) = startRows(id, today(), program, workout, ::uuid)
        if (sessions.start(row, exercises)) { current.set(id); currentId.value = id }
    }
    fun logSet(exerciseId: String, setId: String?, load: String, unit: LoadUnit, reps: String, rir: String) {
        val input = parseSetInput(load, unit, reps, rir)
        if (input == null) { mutableError.value = R.string.error_invalid_input; return }
        action {
            val id = current.get() ?: return@action
            val exercise = sessions.getExercise(exerciseId)?.takeIf { it.sessionId == id } ?: return@action
            val existing = setId?.let { sessions.getSet(it)?.takeIf { set -> set.sessionExerciseId == exercise.id && set.sessionId == id } ?: return@action }
            val position = existing?.position ?: sessions.sets(id).count { it.sessionExerciseId == exerciseId }
            sessions.saveSet(existing?.copy(load = input.load, unit = input.unit.name.lowercase(), reps = input.reps, rir = input.rir)
                ?: SetRow(uuid(), id, exerciseId, position, input.load, input.unit.name.lowercase(), input.reps, rir = input.rir))
        }
    }
    fun deleteSet(setId: String) = action {
        if (sessions.getSet(setId)?.sessionId == current.get()) sessions.deleteSet(setId)
    }
    fun addExercise(exerciseId: String) = action {
        val id = current.get() ?: return@action
        val exercise = reference.activeExercises().firstOrNull { it.id == exerciseId } ?: return@action
        sessions.addExercise(SessionExerciseRow(uuid(), id, 0, exercise.id, exercise.name))
    }
    fun removeExercise(id: String) = exerciseAction(id) { sessions.removeExercise(id) }
    fun moveExercise(id: String, delta: Int) = exerciseAction(id) { sessions.moveExercise(id, delta) }
    fun substitute(id: String, exerciseId: String) = exerciseAction(id) {
        val exercise = reference.activeExercises().firstOrNull { it.id == exerciseId } ?: return@exerciseAction
        sessions.substituteExercise(id, exercise.id, exercise.name)
    }
    fun saveExerciseNotes(id: String, notes: String) = exerciseAction(id) {
        sessions.saveExerciseNotes(id, notes.trim().ifEmpty { null })
    }
    fun saveSessionNotes(notes: String) = action {
        current.get()?.let { sessions.saveSessionNotes(it, notes.trim().ifEmpty { null }) }
    }
    fun finish() = action { current.get()?.let { sessions.finish(it); sync.refreshAndPush() } }
    fun keepMine() = action { current.get()?.let { sync.keepMine(it) } }
    fun takeTheirs() = action { current.get()?.let { sync.takeTheirs(it) } }
    fun share(deliver: (String) -> Unit) = action {
        current.get()?.let { sessions.shareSnapshot(it) }?.let { deliver(formatSession(it)) }
    }
    fun done() = action {
        val id = current.get() ?: return@action
        if (sessions.get(id)?.status in listOf("pushed", "finished")) { current.set(null); currentId.value = null }
    }
    private fun exerciseAction(id: String, block: suspend () -> Unit) = action {
        if (sessions.getExercise(id)?.sessionId == current.get()) block()
    }
    private fun action(block: suspend () -> Unit) {
        viewModelScope.launch {
            actionMutex.withLock {
                mutableError.value = null
                try { block() } catch (e: CancellationException) { throw e }
                catch (e: Exception) { Log.w("RepknotWorkout", "Action failed; local data retained", e); mutableError.value = R.string.error_sync_action }
            }
        }
    }
    class Factory(private val sessions: SessionDao, private val reference: ReferenceDao, private val sync: SessionSync,
                  private val current: CurrentSession) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(WorkoutViewModel::class.java))
            @Suppress("UNCHECKED_CAST") return WorkoutViewModel(sessions, reference, sync, current) as T
        }
    }
}

private fun uuid(): String = UUID.randomUUID().toString()

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
