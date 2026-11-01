# 011 — Workout logic: session editing, set input, workout ViewModel

Status: in progress
Updated: 2026-11-01
Starting commit: `4123c56`
Parent: [006](006-training-data-model.md) step 8 (logic half; the Compose screen is task 012)
· Delegated: yes (see [delegation](../delegation/README.md))

## Outcome and acceptance
Everything the workout screen needs, minus Compose: Room operations to edit a session's
exercises, parsing of set input in the new measures, and a `WorkoutViewModel` exposing one
UI state plus actions. Product rules: `Repknot-PRD.md` §3 items 1–6, `docs/architecture.md`
"Rules for the first logging slice". Accepted when:
- The API below exists with these names and semantics.
- JVM tests cover rules 1–4 and 7–9 (pure functions; no new test libraries). Rules 5, 6
  and 10–14 live in the ViewModel and are verified on device by the lead (clarified
  2026-11-01 after the worker's question; this line previously said 1–14).
- `scripts/check --offline` passes and `cd android && ./gradlew --offline :data:compileDebugAndroidTestKotlin` compiles.

## Scope
In: `android/data/.../SessionStore.kt` (new DAO methods only), `ReferenceStore.kt` (one new
query), `android/app/.../SetInput.kt` (replace), new `WorkoutViewModel.kt` and
`CurrentSession.kt` in the app, `RepknotApplication.kt` (wiring), app/data JVM tests, and
deleting now-unused code listed under Cleanup.
Out of scope: any Compose/UI code, `MainActivity.kt`, strings, `SessionSync.kt` logic,
migrations/Supabase, new libraries. Do not run anything against the dev backend.

## Context to read
`SessionStore.kt`, `SessionMapping.kt` (`startRows`, `pastSessions`, `SessionSnapshot`),
`ReferenceStore.kt`, `domain/Program.kt` (`todaysWorkout`, `prefill`, `Prefill`),
`SyncViewModel.kt` (`formatSession`, action/error pattern, Factory pattern),
`CloudViewModel.kt`, `RepknotApplication.kt`. Local ids must be uuids
(`java.util.UUID.randomUUID().toString()`): the server casts them.

## API (implement exactly)
Data — `SessionDao` additions (each `@Transaction` that edits also calls `bump(sessionId)`):
- `addExercise(row: SessionExerciseRow)` — inserts at `position = max + 1` (0 if none),
  ignoring the row's given position.
- `removeExercise(id: String)` — deletes the exercise and its sets; remaining positions
  renumbered 0..n-1 in order.
- `moveExercise(id: String, delta: Int)` — swaps with the neighbour at `position + delta`
  (`delta` is -1 or +1); no-op at the ends.
- `substituteExercise(id: String, exerciseId: String, exerciseName: String)` — sets the new
  exercise; `substitutedFor` = the previous `exerciseId` only if `substitutedFor` was null
  (it always names the originally prescribed exercise); substituting back to that original
  exercise clears `substitutedFor`. Planned values are kept. Logged sets are kept.
- `saveExerciseNotes(id: String, notes: String?)`.
- `observeSession(id: String): Flow<SessionSnapshot?>` — emits on any change to the
  session, its exercises, or its sets (Room `@Query` flows on the three tables combined;
  `kotlinx.coroutines.flow.combine` is fine).
`ReferenceDao` addition: `@Query("SELECT * FROM programs WHERE isActive = 1 LIMIT 1") fun observeActiveProgramRow(): Flow<ProgramRow?>`
(used only as a reload trigger after refresh), and `suspend fun activeExercises(): List<ExerciseRow>` (non-archived, by name).

App — `SetInput.kt` (replace the old function):
```kotlin
data class SetInput(val load: Double?, val unit: LoadUnit, val reps: Double, val rir: Int?)
fun parseSetInput(load: String, unit: LoadUnit, reps: String, rir: String): SetInput?
```
App — `CurrentSession.kt`:
```kotlin
interface CurrentSession { fun get(): String?; fun set(id: String?) }
class PrefsCurrentSession(context: Context) : CurrentSession  // SharedPreferences "workout_ids", key "current_session"
```
App — `WorkoutViewModel.kt`:
```kotlin
sealed interface WorkoutUiState {
    data object Loading : WorkoutUiState
    data object NoProgram : WorkoutUiState                       // nothing cached yet (signed out / never synced)
    data class Today(val programName: String, val suggested: ProgramWorkout,
                     val workouts: List<ProgramWorkout>) : WorkoutUiState   // workouts in sequence order
    data class Session(val row: SessionRow, val exercises: List<ExerciseUi>,
                       val choices: List<ExerciseRow>) : WorkoutUiState
}
data class ExerciseUi(val row: SessionExerciseRow, val sets: List<SetRow>, val previous: Prefill,
                      val suggestion: SetSuggestion)
data class SetSuggestion(val load: Double?, val unit: LoadUnit, val reps: Double?)

class WorkoutViewModel(sessions: SessionDao, reference: ReferenceDao, sync: SessionSync,
                       current: CurrentSession, today: () -> LocalDate = LocalDate::now) : ViewModel() {
    val state: StateFlow<WorkoutUiState>
    val error: StateFlow<Int?>          // string resource id, same pattern as SyncViewModel
    fun start(workoutKey: String)
    fun logSet(exerciseId: String, setId: String?, load: String, unit: LoadUnit, reps: String, rir: String)
    fun deleteSet(setId: String)
    fun addExercise(exerciseId: String)
    fun removeExercise(id: String); fun moveExercise(id: String, delta: Int)
    fun substitute(id: String, exerciseId: String)
    fun saveExerciseNotes(id: String, notes: String); fun saveSessionNotes(notes: String)
    fun finish(); fun keepMine(); fun takeTheirs()
    fun share(deliver: (String) -> Unit)
    fun done()
    class Factory(...)
}
```
Pure helpers (top level in `WorkoutViewModel.kt`, these carry the logic and the tests):
```kotlin
fun todayState(program: Program?, programName: String?, history: List<PastSession>): WorkoutUiState
fun prescriptionFor(row: SessionExerciseRow, defaultUnit: LoadUnit): Prescription
fun suggestionFor(prefill: Prefill, setIndex: Int, row: SessionExerciseRow, defaultUnit: LoadUnit): SetSuggestion
fun sessionState(snapshot: SessionSnapshot, history: List<PastSession>, exercises: List<ExerciseRow>): WorkoutUiState.Session
```

## Rules
Set input (`parseSetInput`):
1. Trim; accept `,` as decimal separator. Reps: > 0 in 0.5 steps (`8`, `8.5`; not `8.3`, `0`).
2. Load: required, finite, ≥ 0 for kg/lb/plates; must be blank for bodyweight (→ `null`).
3. RIR: blank → null, else whole number ≥ 0. Any invalid field → `null`.

State:
4. `todayState`: null program → `NoProgram`; otherwise `Today` with `suggested =
   todaysWorkout(program, history)` and `workouts` sorted by `sequencePosition`.
5. The current session is `current.get()`; if it is null or that session no longer exists
   locally, show `Today` (and clear the stale id). Otherwise show `Session`.
6. `start(key)`: uses the cached active program and `startRows(uuid, today(), program, workout) { uuid }`,
   then `sessions.start(...)`, then `current.set(id)`. Ignored if a current session exists.
7. `prescriptionFor` builds a domain `Prescription` from the row's planned snapshot;
   missing planned unit → `defaultUnit`; missing planned sets → 0.
   `sessionState` computes `previous = prefill(prescriptionFor(row, unit), history)` where
   `unit` = planned unit, else the exercise's `defaultUnit`, else KG; history excludes the
   session itself.
8. `suggestionFor(prefill, i, row, unit)` (for the *next* set, `i` = number of logged sets):
   `Previous` → the previous set at index `i`, else its last set; `StartLoad` → planned load
   and planned reps max (null if no range). Bodyweight suggestions have `load = null`.
   Suggestions are never written as sets (architecture rule).
9. `choices` = `activeExercises()`.
Actions:
10. `logSet` parses (invalid → `error = R.string.error_invalid_input`, nothing saved);
    `setId == null` → new uuid at `position = logged count`; else overwrites that set
    keeping its position. Saved via `sessions.saveSet`.
11. `addExercise` creates a row from the chosen `ExerciseRow` (uuid id, no planned values).
    `substitute` uses `substituteExercise` with the chosen exercise's name.
12. `finish()` = `sessions.finish(id)` then `sync.refreshAndPush()`; `keepMine`/`takeTheirs`
    delegate to `SessionSync`; `share` = `shareSnapshot` + `formatSession` (move
    `formatSession`/`formatSet` from `SyncViewModel.kt` into `WorkoutViewModel.kt` unchanged).
13. `done()` clears the current session only when its status is `pushed`, `finished`
    (upload pending, retried by background sync) — not `in_progress` or a conflict status.
14. Sync/network failures set `error = R.string.error_sync_action`; local data is kept.
    The state reloads when the session snapshot changes or `observeActiveProgramRow` emits.

## Cleanup
Delete (now unused): `SyncViewModel.kt` (after moving the format functions),
`domain/LoggedSet.kt` contents except `LoadUnit` (move `LoadUnit` to `domain/Program.kt`),
`SetValuesTest.kt`. Update `SessionShareTest`/`SetInputTest` for the moved/replaced code.
In `RepknotApplication`: drop `sessionId`/`ids`; expose `current: CurrentSession`. Keep the
interim `MainActivity` compiling (it does not use the new ViewModel yet).

## Plan / progress
1. [x] DAO additions + reference queries; compile (incl. androidTest).
2. [ ] `SetInput`, `CurrentSession`, pure helpers + `WorkoutViewModel`; cleanup; wiring.
3. [ ] JVM tests: `SetInputTest` (1–3), `WorkoutStateTest` (4, 7, 8 and `sessionState`
   ordering/choices), `SessionShareTest` still passing. Rules 5, 6, 10–14 and the DAO
   methods are verified on device by the lead; note anything you could not test.
4. [ ] `scripts/check --offline`; androidTest compile; commit(s) with task 011 in subject.

## Verification
- `cd android && ./gradlew --offline :data:compileDebugKotlin :data:compileDebugAndroidTestKotlin` — passed.
- `scripts/check --offline` — passed on DAO-only checkpoint (existing tests; remaining implementation not present).
- `cd android && ./gradlew --offline :data:compileDebugAndroidTestKotlin` — passed on DAO-only checkpoint.
- DAO behavior still requires lead's device verification; review revision bump and reorder logic.

## Handoff
DAO additions committed as `c7b87e4`; steps 2–4 remain unfinished. No uncommitted implementation changes. Review revision bumps and reorder logic; device verification remains pending.

**Question / blocker:** Acceptance requires JVM tests for rules 1–14 (pure functions; no new test libraries), but plan step 3 explicitly assigns rules 5, 6, and 10–14 to the lead's device verification. Which verification requirement governs? In particular, should those ViewModel/action rules be extracted into pure helpers and tested on the JVM, or verified only on device as planned? After clarification, implement step 2 and tests under the agreed interpretation, then rerun both acceptance commands.
