# 010 — Nested sessions on the phone and sync

Status: in progress
Updated: 2026-11-01
Starting commit: d506393
Parent: [006](006-training-data-model.md) step 7 (second half) · Lead task (sync/conflict
logic stays with the lead per [delegation](../delegation/README.md)).

## Outcome and acceptance
The phone stores sessions as session → exercises → sets, starts a session from a program
workout by snapshotting its prescription, and syncs nested snapshots with unchanged
ADR 001 semantics (pending/acknowledge/replay/conflict/keep mine/take theirs). Accepted when:
- `scripts/check --offline` passes; JVM tests cover the pure mapping (Room rows ↔ server
  DTOs ↔ push payload) and session start from a prescription.
- Device/instrumented tests (`SessionStoreTest`, `WorkoutDatabaseTest`,
  `SharedSessionIntegrationTest`) are rewritten for the nested shape; running them needs a
  connected device — record honestly if none is available.
- Refresh also replaces the reference cache from task 009.

## Scope
In: `SessionStore.kt`, `SessionSync.kt`, `WorkoutDatabase.kt` (session/set entities),
instrumented tests, the minimal `android/app` changes needed to compile (the one-set demo
screen goes away; step 8 builds the real workout screen).
Out: the workout screen itself (step 8), importer (step 9), server changes.

## Plan
1. Entities: `SessionRow` + `programId`, `programWorkoutKey`; new `SessionExerciseRow`
   (planned_* snapshot, `substitutedFor`, notes); `SetRow` gains `sessionExerciseId`,
   nullable `load`/`unit`, `reps: Double?`, `durationS: Int?`; drops `exerciseName`.
2. DTOs: one `ServerSession` shape for both the table select and RPC snapshots, using the
   PostgREST alias `exercises:session_exercises(*,sets:session_sets(*))`.
3. DAO: `adopt` / `replaceCache` / `snapshot` / conflict paths write and read nested rows;
   revision and status transitions unchanged. `startSession(program, workout)` snapshots
   prescriptions into session exercises.
4. Sync: `refreshAndPush` refreshes the reference cache first, then sessions; push sends
   `exercises` with `exercise` = exercise uuid.
5. Domain bridge: map finished cached sessions to `PastSession` ordered by
   `performed_on`, then `number` (no server `finished_at`; see task 006 decisions).
6. App: remove the single-set demo path (`SetRepository`, `LoggingViewModel`, `SetEditor`);
   keep finish/sync/account sections compiling.

## Handoff
Next action: plan step 1 (entities).
