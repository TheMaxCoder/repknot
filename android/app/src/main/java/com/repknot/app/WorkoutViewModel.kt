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
import java.time.Instant
import java.time.ZoneOffset
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID

sealed interface WorkoutUiState {
    data object Loading : WorkoutUiState
    data object NoProgram : WorkoutUiState
    data class Today(val programName: String, val suggested: ProgramWorkout, val workouts: List<ProgramWorkout>) : WorkoutUiState
    data class Session(val row: SessionRow, val exercises: List<ExerciseUi>, val choices: List<ExerciseRow>) : WorkoutUiState
    /** A workout looked at before starting it; nothing is stored until `start`. */
    data class Preview(val programName: String, val workout: ProgramWorkout, val suggested: Boolean,
                       val exercises: List<PreviewExerciseUi>) : WorkoutUiState
}
data class ExerciseUi(val row: SessionExerciseRow, val sets: List<SetRow>, val previous: Prefill, val suggestion: SetSuggestion, val previousOn: LocalDate?, val target: TodayTarget?, val deltas: List<SetDelta>, val programRestS: Int?)
data class SetSuggestion(val load: Double?, val unit: LoadUnit, val reps: Double?)
data class PreviewExerciseUi(val prescription: Prescription, val previous: Prefill, val previousOn: LocalDate?, val target: TodayTarget?)

private fun previousDate(previous: Prefill, history: List<PastSession>): LocalDate? =
    (previous as? Prefill.Previous)?.let { p -> history.firstOrNull { it.id == p.sessionId }?.finishedAtMillis }
        ?.let { Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate() }

/** Preview of one program workout with previous values; null if the key is not in the program. */
fun previewState(program: Program, programName: String?, workoutKey: String, history: List<PastSession>): WorkoutUiState.Preview? {
    val workout = program.workouts.firstOrNull { it.key == workoutKey } ?: return null
    return WorkoutUiState.Preview(programName ?: "Program", workout, todaysWorkout(program, history).key == workoutKey,
        workout.exercises.sortedBy { it.position }.map {
            val previous = prefill(it, history)
            PreviewExerciseUi(it, previous, previousDate(previous, history), todayTarget(it, history))
        })
}

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

fun sessionState(snapshot: SessionSnapshot, history: List<PastSession>, exercises: List<ExerciseRow>,
                 workouts: List<ProgramWorkoutRow>, prescriptions: List<ProgramExerciseRow>): WorkoutUiState.Session {
    val byId = exercises.associateBy { it.id }
    val sets = snapshot.sets.groupBy { it.sessionExerciseId }
    val previousHistory = history.filterNot { it.id == snapshot.row.id }
    return WorkoutUiState.Session(snapshot.row, snapshot.exercises.sortedBy { it.position }.map { row ->
        val configuredUnit = byId[row.exerciseId]?.defaultUnit?.let(::parseUnit)
        val unit = if (row.substitutedFor != null) configuredUnit ?: LoadUnit.KG
            else row.plannedUnit?.let(::parseUnit) ?: configuredUnit ?: LoadUnit.KG
        // A substitution keeps the original prescription snapshot, but its start load
        // is not a suggestion for a different exercise or unit.
        val inputRow = if (row.substitutedFor != null) row.copy(plannedUnit = unit.name.lowercase(), plannedLoad = null) else row
        val logged = sets[row.id].orEmpty().sortedBy { it.position }
        val previous = prefill(prescriptionFor(inputRow, unit), previousHistory)
        val target = if (row.substitutedFor != null) null else todayTarget(prescriptionFor(inputRow, unit), previousHistory)
        val deltas = logged.mapIndexed { index, set ->
            set.unit?.let { setDelta(PastSet(set.position, set.load, parseUnit(it), set.reps, set.durationS, set.rir), index, row.exerciseId, previousHistory) }
                ?: SetDelta.NONE
        }
        ExerciseUi(row, logged, previous, suggestionFor(previous, logged.size, inputRow, unit), previousDate(previous, previousHistory), target, deltas,
            programRestS(snapshot.row.programWorkoutKey, snapshot.row.programId, workouts, prescriptions, row.substitutedFor ?: row.exerciseId))
    }, exercises)
}

@OptIn(ExperimentalCoroutinesApi::class)
class WorkoutViewModel(
    private val sessions: SessionDao, private val reference: ReferenceDao, private val sync: SessionSync,
    private val current: CurrentSession,
    val restOverrides: StateFlow<Map<String, Int>>, private val setRestOverride: (String, Int) -> Unit,
    private val today: () -> LocalDate = LocalDate::now,
    private val now: () -> Long = System::currentTimeMillis,
) : ViewModel() {
    private val currentId = MutableStateFlow(current.get())
    private val previewKey = MutableStateFlow<String?>(null)
    private val mutableState = MutableStateFlow<WorkoutUiState>(WorkoutUiState.Loading)
    val state: StateFlow<WorkoutUiState> = mutableState.asStateFlow()
    private val mutableError = MutableStateFlow<Int?>(null)
    val error: StateFlow<Int?> = mutableError.asStateFlow()
    private val actionMutex = Mutex()

    init {
        viewModelScope.launch {
            currentId.flatMapLatest { id ->
                val snapshot = if (id == null) flowOf(null) else sessions.observeSession(id)
                // Session history (e.g. a refresh bringing in another client's session) also
                // changes today's suggestion, so any session change reloads the state.
                combine(snapshot, reference.observeActiveProgramRow(), sessions.observeAllSessions(), previewKey) { session, program, _, key ->
                    Pair(Triple(id, session, program), key)
                }
            }.collect { (loaded, key) ->
                val (id, snapshot, programRow) = loaded
                if (id != null && snapshot == null) {
                    current.set(null)
                    currentId.value = null
                }
                val history = sessions.history()
                val program = if (snapshot == null) reference.activeProgram() else null
                mutableState.value = when {
                    snapshot != null -> sessionState(snapshot, history, reference.activeExercises(),
                        snapshot.row.programId?.let { reference.getWorkouts(it) }.orEmpty(), reference.getPrescriptions())
                    program != null && key != null -> previewState(program, programRow?.name, key, history)
                        ?: todayState(program, programRow?.name, history)
                    else -> todayState(program, programRow?.name, history)
                }
            }
        }
    }

    fun toggleRestTarget(exerciseId: String, current: Int) { setRestOverride(exerciseId, toggledRestTarget(current)) }
    fun preview(workoutKey: String) { previewKey.value = workoutKey }
    fun closePreview() { previewKey.value = null }
    /** Abandons the current workout if it never left the phone (see SessionDao.discard); back to today's workout. */
    fun discard() = action {
        val id = current.get() ?: return@action
        if (sessions.discard(id)) { current.set(null); currentId.value = null }
    }
    fun start(workoutKey: String) = action {
        if (current.get() != null) return@action
        val program = reference.activeProgram() ?: return@action
        val workout = program.workouts.firstOrNull { it.key == workoutKey } ?: return@action
        val id = uuid()
        val (row, exercises) = startRows(id, today(), program, workout, ::uuid)
        if (sessions.start(row.copy(startedAt = now()), exercises)) { current.set(id); currentId.value = id; previewKey.value = null }
    }
    fun logSet(exerciseId: String, setId: String?, load: String, unit: LoadUnit, reps: String, rir: String) {
        val input = parseSetInput(load, unit, reps, rir)
        if (input == null) { mutableError.value = R.string.error_invalid_input; return }
        action {
            val id = current.get() ?: return@action
            val exercise = sessions.getExercise(exerciseId)?.takeIf { it.sessionId == id } ?: return@action
            val existing = setId?.let { sessions.getSet(it)?.takeIf { set -> set.sessionExerciseId == exercise.id && set.sessionId == id } ?: return@action }
            // max + 1, not count: after deleting a middle set, count could repeat a position.
            val position = existing?.position
                ?: ((sessions.sets(id).filter { it.sessionExerciseId == exerciseId }.maxOfOrNull { it.position } ?: -1) + 1)
            sessions.saveSet(existing?.copy(load = input.load, reps = input.reps, rir = input.rir)
                ?: SetRow(uuid(), id, exerciseId, position, input.load, input.unit.name.lowercase(), input.reps, rir = input.rir,
                    loggedAt = now()))
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
    /** Marks the current workout as a light / modified day; progress statuses ignore it. */
    fun setLight(light: Boolean) = action { current.get()?.let { sessions.setLight(it, light) } }
    fun saveSessionNotes(notes: String) = action {
        current.get()?.let { sessions.saveSessionNotes(it, notes.trim().ifEmpty { null }) }
    }
    /** Marks the workout finished. It stays on the phone until Upload is tapped (task 028). */
    fun finish() = action { current.get()?.let { sessions.finishAt(it, now()) } }
    /** Marks the finished workout for upload and pushes it; later syncs retry if this fails. */
    fun upload() = action { current.get()?.let { if (sessions.requestUpload(it) > 0) sync.refreshAndPush() } }
    /** Back to in progress if the workout was never uploaded. */
    fun resume() = action { current.get()?.let { sessions.resume(it) } }
    fun keepMine() = action { current.get()?.let { sync.keepMine(it) } }
    fun takeTheirs() = action { current.get()?.let { sync.takeTheirs(it) } }
    fun share(deliver: (String) -> Unit) = action {
        current.get()?.let { sessions.shareSnapshot(it) }?.let { deliver(formatSession(it)) }
    }
    fun done() = action {
        val id = current.get() ?: return@action
        val row = sessions.get(id) ?: return@action
        // A finished workout must be uploaded or discarded first, so test runs never linger unseen.
        if (row.status == "pushed" || (row.status == "finished" && row.uploadRequested)) { current.set(null); currentId.value = null }
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
                  private val current: CurrentSession,
                  private val restOverrides: StateFlow<Map<String, Int>>, private val setRestOverride: (String, Int) -> Unit) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(WorkoutViewModel::class.java))
            @Suppress("UNCHECKED_CAST") return WorkoutViewModel(sessions, reference, sync, current, restOverrides, setRestOverride) as T
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
