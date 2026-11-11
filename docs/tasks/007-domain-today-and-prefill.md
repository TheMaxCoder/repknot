# 007 — Domain: today's workout and previous-value prefill

Status: done
Updated: 2026-11-01
Starting commit: ae0f86a
Parent: [006](006-training-data-model.md) step 6 · Delegated: yes (first trial, see
[delegation](../delegation/README.md))

## Outcome and acceptance
Pure Kotlin in `android/domain` that answers, per [ADR 002](../decisions/002-training-data-model.md)
("Today's workout and previous values"): which program workout is next, and what to
prefill for each prescribed exercise. Accepted when:
- The API below exists with exactly these names and semantics.
- Unit tests cover every numbered rule below (one or more tests per rule).
- `scripts/check --offline` passes (fall back to `scripts/check` if offline fails due to
  missing dependencies, and say so).

## Scope
In:
- `android/domain/src/main/kotlin/com/repknot/domain/LoggedSet.kt`: add `PLATES` and
  `BODYWEIGHT` to `LoadUnit` (append after `LB`; keep `KG`, `LB` first).
- `android/app/src/main/java/com/repknot/app/MainActivity.kt`: the existing unit picker
  (`LoadUnit.entries.forEach`) must keep offering only KG and LB — change it to
  `listOf(LoadUnit.KG, LoadUnit.LB).forEach`. No other app change.
- New `android/domain/src/main/kotlin/com/repknot/domain/Program.kt` and
  `android/domain/src/test/kotlin/com/repknot/domain/ProgramTest.kt`.

Out of scope: Room/data module, Supabase/migrations, sync, any other UI, parsing the
program JSON, `SetValues` changes, new dependencies. Do not touch `private-data/`.

## API (implement exactly)
```kotlin
package com.repknot.domain

data class Prescription(
    val position: Int, val exerciseId: String, val exerciseName: String, val sets: Int,
    val repMin: Int?, val repMax: Int?, val rirMin: Int?, val rirMax: Int?,
    val load: Double?, val unit: LoadUnit,
)
data class ProgramWorkout(val key: String, val name: String, val sequencePosition: Int,
    val exercises: List<Prescription>)
data class Program(val id: String, val workouts: List<ProgramWorkout>)

data class PastSet(val position: Int, val load: Double?, val unit: LoadUnit,
    val reps: Double?, val durationS: Int?, val rir: Int?)
data class PastExercise(val position: Int, val exerciseId: String, val sets: List<PastSet>)
data class PastSession(val id: String, val programId: String?, val programWorkoutKey: String?,
    val finishedAtMillis: Long?, val exercises: List<PastExercise>)

sealed interface Prefill {
    data class Previous(val sessionId: String, val sets: List<PastSet>) : Prefill
    data class StartLoad(val load: Double?, val unit: LoadUnit) : Prefill
}

fun todaysWorkout(program: Program, sessions: List<PastSession>): ProgramWorkout
fun prefill(prescription: Prescription, sessions: List<PastSession>): Prefill
```

## Rules
`todaysWorkout`:
1. Sequence = `program.workouts` sorted by `sequencePosition` (input order is irrelevant).
   An empty program throws `IllegalArgumentException`.
2. Only finished sessions count (`finishedAtMillis != null`) whose `programId == program.id`
   and whose `programWorkoutKey` matches a workout in this program. Sessions of other
   programs, ad-hoc/imported sessions (null program), and unknown keys are ignored.
3. No counting session → first workout in sequence.
4. Otherwise the workout after the latest counting session (max `finishedAtMillis`;
   on a tie, the one appearing later in `sessions`), wrapping from last to first.
   Weekdays play no part.

`prefill`:
5. Candidate sessions: finished only, any program or none (history is per exercise).
6. Take the latest candidate (same ordering/tie rule as 4) that has at least one set with
   `exerciseId == prescription.exerciseId` and `unit == prescription.unit`.
   A session with that exercise only in another unit is skipped, falling back to older ones.
7. `Previous.sets` = those matching sets from that session, ordered by exercise
   `position` then set `position` (an exercise can appear twice in one session).
   Sets of other units in the same session are excluded.
8. No match → `StartLoad(prescription.load, prescription.unit)` (load may be null).
9. Functions are pure: no clock, no I/O; inputs are not mutated.

## Plan / progress
1. [x] Add units + picker change; build passes.
2. [x] `Program.kt` + `ProgramTest.kt` covering rules 1–9.
3. [x] `scripts/check --offline`; commit (task 007 in subject).

## Decisions and discoveries
- Lead decisions (2026-11-01): unfinished sessions don't advance "today" (resuming an
  in-progress session is the app's job); prefill history crosses programs; tie-break by
  input order keeps the functions deterministic without ids carrying meaning.

## Verification
- `cd android && ./gradlew --offline :app:assembleDebug` — passed.
- `cd android && ./gradlew --offline :domain:test` — passed; test compiler reports redundant spread warnings.
- `scripts/check --offline` — passed (debug build, lint, available unit tests). Device checks not run.

## Handoff
Implemented the domain API and rule tests; units added and existing picker restricted to KG/LB. No unresolved questions. Next: return to parent task 006 to integrate the domain logic; device UI verification was not in scope.

## Lead review (2026-11-01)
Accepted without retry. Re-ran `scripts/check --offline` (passed) and
`./gradlew --offline :domain:test --rerun` (ProgramTest 7/7, SetValuesTest 2/2). Diff read
against rules 1–9: all implemented and tested; scope respected. Cosmetic only: redundant
spread (`*arrayOf`) warnings in `ProgramTest.kt`. No miss logged.
