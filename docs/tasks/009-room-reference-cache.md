# 009 — Room reference cache: exercises and programs

Status: done
Updated: 2026-11-01
Starting commit: 94118c1
Parent: [006](006-training-data-model.md) step 7 (first half) · Delegated: yes (see
[delegation](../delegation/README.md)). The second half — nested sessions and sync — is
lead work in task 010 and must not be started here.

## Outcome and acceptance
The phone can cache the signed-in user's exercises and programs (read-only on the server)
and hand the active program to the domain functions from task 007. Accepted when:
- The code below exists with these names and semantics; Room compiles the queries.
- JVM unit tests cover every numbered rule; `scripts/check --offline` passes.
- Room schema `android/data/schemas/com.repknot.data.WorkoutDatabase/4.json` is generated
  and committed.

## Scope
In:
- New `android/data/src/main/java/com/repknot/data/ReferenceStore.kt` (entities, DAO, mapping).
- New `android/data/src/main/java/com/repknot/data/ReferenceSync.kt` (server DTOs + fetch).
- `WorkoutDatabase.kt`: add the four entities, `version = 4`, accessor
  `abstract fun reference(): ReferenceDao`, and change the builder to
  `fallbackToDestructiveMigrationFrom(true, 1, 2, 3)` (building phase: local DB is
  disposable — see `docs/architecture.md` "Intentional deferrals"). Nothing else in that file.
- `android/data/build.gradle.kts`: add `testImplementation(libs.junit)` only.
- New tests under `android/data/src/test/java/com/repknot/data/`.

Out of scope: `SessionStore.kt`, `SessionSync.kt` (do not call the new fetch from sync —
the lead wires it in task 010), session/set entities, `android/app`, `domain`, Supabase,
new libraries. Do not run anything against the dev backend.

## Context
- Server tables and columns: `supabase/migrations/20261101030000_training_model.sql`
  (`exercises`, `programs`, `program_workouts`, `program_exercises`). Units are lowercase
  strings `kg|lb|plates|bodyweight`.
- Domain types to produce: `android/domain/src/main/kotlin/com/repknot/domain/Program.kt`
  (`Program`, `ProgramWorkout`, `Prescription`, `LoadUnit`).
- Existing patterns: `SessionSync.kt` (kotlinx `@Serializable` DTOs with `@SerialName`,
  `codec`, `cloud.requireClient().from(...)`), `SessionStore.kt` (Room entity/DAO style).

## Code (implement exactly)
Entities (Room, table names as given; ids are server uuids as `String`):
- `ExerciseRow` → `exercises`: `id` (PK), `slug`, `name`, `defaultUnit: String?`, `archived: Boolean`.
- `ProgramRow` → `programs`: `id` (PK), `key`, `name`, `version: Int`, `startsOn: String`,
  `rules: String?`, `isActive: Boolean`.
- `ProgramWorkoutRow` → `program_workouts`: `id` (PK), `programId`, `key`, `name`,
  `sequencePosition: Int`, `defaultWeekday: Int?`.
- `ProgramExerciseRow` → `program_exercises`: `id` (PK), `workoutId`, `position: Int`,
  `exerciseId`, `sets: Int`, `repMin/repMax/rirMin/rirMax/restSMin/restSMax: Int?`,
  `load: Double?`, `unit: String`, `notes: String?`.
No Room foreign keys or indices needed.

DTOs in `ReferenceSync.kt` (`@Serializable`, snake_case via `@SerialName`, unknown keys ignored):
`ServerExercise(id, slug, name, default_unit?, archived_at?)`,
`ServerProgram(id, key, name, version, starts_on, rules?, is_active, workouts: List<ServerProgramWorkout>)`
where `workouts` is `@SerialName("program_workouts")`,
`ServerProgramWorkout(id, key, name, sequence_position, default_weekday?, exercises: List<ServerPrescription>)`
where `exercises` is `@SerialName("program_exercises")`,
`ServerPrescription(id, position, exercise_id, sets, rep_min?, rep_max?, rir_min?, rir_max?, rest_s_min?, rest_s_max?, load?, unit, notes?)`.
Function: `suspend fun fetchReference(cloud: CloudAccount): ReferenceSnapshot` that selects
`exercises` with `*` and `programs` with `Columns.raw("*,program_workouts(*,program_exercises(*))")`
and returns `data class ReferenceSnapshot(val exercises: List<ServerExercise>, val programs: List<ServerProgram>)`.

`ReferenceDao` (in `ReferenceStore.kt`):
- `@Transaction suspend fun replaceAll(snapshot: ReferenceSnapshot)` — deletes all four
  tables' rows, then inserts the mapped rows.
- `suspend fun activeProgram(): Program?` (non-Room helper on the interface, like
  `SessionDao.snapshot`): reads the active `ProgramRow`, its workouts and prescriptions,
  and the exercises, then calls `toDomainProgram`.
- Plus whatever plain `@Query`/`@Insert` methods these need.

Pure mapping functions (top level in `ReferenceStore.kt`, unit-tested without Room):
- `fun ReferenceSnapshot.toRows(): ReferenceRows` with
  `data class ReferenceRows(val exercises: List<ExerciseRow>, val programs: List<ProgramRow>, val workouts: List<ProgramWorkoutRow>, val prescriptions: List<ProgramExerciseRow>)`.
- `fun toDomainProgram(program: ProgramRow, workouts: List<ProgramWorkoutRow>, prescriptions: List<ProgramExerciseRow>, exercises: List<ExerciseRow>): Program`
- `fun parseUnit(value: String): LoadUnit`

## Rules
1. `toRows` flattens nested programs: each workout gets `programId`, each prescription gets
   `workoutId` from its parent; all fields copied unchanged.
2. `archived = archived_at != null`.
3. `parseUnit` maps `kg|lb|plates|bodyweight` to `LoadUnit` (case-insensitive); anything
   else throws `IllegalArgumentException`.
4. `toDomainProgram` includes only workouts whose `programId == program.id`, and per workout
   only prescriptions whose `workoutId` matches, sorted by `position`. Workout order is left
   to the domain (`todaysWorkout` sorts by `sequencePosition`); keep `sequencePosition`.
5. `Prescription.exerciseName` comes from the matching `ExerciseRow.name`; a prescription
   whose exercise is missing throws `IllegalStateException` naming the exercise id.
6. `Program.id` = `ProgramRow.id`; `ProgramWorkout.key/name/sequencePosition` copied;
   prescription numbers copied (`load` stays nullable).
7. `activeProgram()` returns null when no row has `isActive = true`.
8. DTOs decode a JSON sample shaped like the PostgREST response (nested `program_workouts`
   → `program_exercises`, extra unknown fields present, nullable fields absent or null).

Rule 7 and `replaceAll` are Room-only: verified by compilation now and by the lead on a
device later; no JVM test is needed for them.

## Plan / progress
1. [x] `testImplementation(libs.junit)`; entities + primitive DAO queries + database version/fallback; build. Transaction and domain helpers follow DTOs/mapping.
2. [x] DTOs + `fetchReference`; mapping functions and DAO helpers.
3. [x] JVM tests for rules 1–6 and 8 (`ReferenceMappingTest.kt`).
4. [x] `scripts/check --offline`; confirmed `schemas/.../4.json` exists and is committed.

## Verification
- `cd android && ./gradlew :data:assembleDebug --offline` — passed twice; Room generated schema 4 and compiled DAO helpers.
- `cd android && ./gradlew :data:testDebugUnitTest --offline` — passed (4 tests).
- `scripts/check --offline && test -f android/data/schemas/com.repknot.data.WorkoutDatabase/4.json && echo 'Schema 4 exists'` — passed; schema exists.

## Handoff
DTOs, fetch, transactional replacement and domain mapping are implemented and unit-tested.
Lead: wire the fetch in task 010 and verify `replaceAll` and `activeProgram` on a device.
No dev backend was contacted. Review: v4 fallback destructively resets synthetic v1–v3
schemas; JUnit test dependency added as specified. `docs/architecture.md` still describes
the v3 fallback; update it when integrating task 010 (outside this task's stated scope).
