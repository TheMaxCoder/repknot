# 027 — Workout timing and rest timer

Status: in progress
Starting commit: 1e7b90f
Date: 2026-11-02

## Outcome / acceptance
Owner wants a rest timer instead of checking a watch, and rough timing data for later
time optimization (total workout, per-exercise time, rest between sets). Owner often
batch-logs an exercise's sets after finishing them; that must not break anything.

1. Timestamps (no extra taps):
   - session `startedAt` set when Start is tapped; `finishedAt` set the first time Finish
     is tapped (Retry upload also calls finish and must not overwrite it).
   - set `loggedAt` set when a set is first saved; editing a set keeps it.
   - Older sessions/sets and imported history keep null. Values are instants (UTC millis
     on the phone, `timestamptz` on the server).
2. Timestamps survive upload, refresh, conflict adopt and connector (`save_session`)
   writes that omit them (omitted = keep stored value, like `light`).
3. Workout screen (session only): quiet elapsed time since start in the header; a rest
   timer that restarts on every Log (derived from the newest `loggedAt` in the session),
   showing elapsed / target where the target is 2:00 or 3:00 per exercise, toggled with
   one tap and remembered per exercise on the device. Vibrate once when the target is
   crossed while the app is open. No Rest button, no countdown alarm while locked.
4. Analysis UI is out of scope (later, once there is data). Batch-logged sets (logged a
   few seconds apart) are to be excluded from rest analysis when that is built.

## Scope
Step 1 (lead; sync/migration work stays with the lead per docs/delegation):
Supabase migration + pgTAP test, Room v6→v7 migration + test, mapping/push payload,
ViewModel timestamp writes.
Step 2 (worker): rest timer, elapsed time, rest target preference, vibration.
Out of scope: analysis/statistics UI, lock-screen alerts/notifications/foreground
service, changes to conflict/revision rules.

## Plan / progress
- [x] Step 1a: migration `20261102010000_workout_timing.sql` (columns, push_session,
      save_session, replace_session_exercises keeps `logged_at` by set id when omitted)
      + `supabase/tests/008_workout_timing_test.sql`; `scripts/db push` + `scripts/db test`.
- [x] Step 1b: Room v7 (`SessionRow.startedAt/finishedAt`, `SetRow.loggedAt`), MIGRATION_6_7,
      MigrationTest, mapping (ServerSession/ServerSet/Push*), ViewModel writes with an
      injectable clock; `scripts/check --offline`; device migration test.
- [x] Step 2: worker UI slice — timer, elapsed header, device-local target and vibration; offline checks passed.
- [ ] Lead review, install, phone check, architecture/status update.

## Step 2 spec (worker)
Step 1 is committed (5812eb0): `SessionRow.startedAt`, `SetRow.loggedAt` (epoch millis,
nullable) are already written by `WorkoutViewModel`. Do not change data/, supabase/, the
Room schema, sync, or the timestamp writes.

Files: `android/app/src/main/java/com/repknot/app/` — new `WorkoutTiming.kt` (pure
helpers), `WorkoutViewModel.kt`, `AppSettings.kt`, `MainActivity.kt` (factory wiring),
`ui/WorkoutScreen.kt`, `res/values/strings.xml`, `AndroidManifest.xml`; new JVM test
`android/app/src/test/java/com/repknot/app/WorkoutTimingTest.kt`.

Pure helpers in `WorkoutTiming.kt` (all JVM-tested):
1. `programRestS(workoutKey: String?, programId: String?, workouts: List<ProgramWorkoutRow>,
   prescriptions: List<ProgramExerciseRow>, exerciseId: String): Int?` — the workout row with
   `programId` and `key == workoutKey`, then its prescriptions (`workoutId == workout.id`)
   with `exerciseId`; lowest `position` wins; returns its `restSMax`. Null if anything is
   missing. Caller passes `row.substitutedFor ?: row.exerciseId` (a substitute rests like
   the prescribed exercise).
2. `restTargetS(exerciseId: String, programRestS: Int?, overrides: Map<String, Int>): Int` =
   `overrides[exerciseId] ?: programRestS ?: 120`.
3. `toggledRestTarget(current: Int): Int` = 180 if `current <= 120`, else 120.
4. `restState(nowMillis: Long, status: String, sets: List<SetRow>): RestState?` where
   `RestState(sessionExerciseId: String, elapsedS: Long)`. Null unless status is
   `in_progress`; takes the set with the greatest non-null `loggedAt` (tie: later in the list);
   null if none; `elapsedS = (now - loggedAt) / 1000` clamped at 0; null if
   `elapsedS > 900` (15 min: no longer a rest).
5. `restText(seconds: Long): String` = `m:ss` (`0:05`, `1:47`, `12:00`).
6. `elapsedWorkoutText(startedAt: Long?, nowMillis: Long, status: String): String?` — null
   unless in_progress and startedAt non-null; minutes = floor((now-start)/60000) clamped
   at 0; `"N min"` under 60, else `"H h M min"` (`"1 h 5 min"`, `"2 h 0 min"`). Plain
   Kotlin text is fine (no plurals resource).

ViewModel / settings:
7. `ExerciseUi` gains `programRestS: Int?` filled in `sessionState` via helper 1.
   `sessionState` gets two extra params (`workouts: List<ProgramWorkoutRow>`,
   `prescriptions: List<ProgramExerciseRow>`); the ViewModel loads them with
   `reference.getWorkouts(programId)` (empty list when programId is null) and
   `reference.getPrescriptions()`. Update existing `WorkoutStateTest` calls to pass
   empty lists; do not change their assertions.
8. `AppSettings`: `restOverrides: StateFlow<Map<String, Int>>` persisted in the existing
   "settings" prefs under keys `rest_target_s.<exerciseId>`; `setRestOverride(exerciseId,
   seconds)` with `commit()` like the others. The ViewModel receives the flow and a setter
   (constructor params, wired in the Factory/MainActivity) and exposes
   `val restOverrides` plus `fun toggleRestTarget(exerciseId: String, current: Int)` which
   saves `toggledRestTarget(current)`. Overrides are keyed by the performed `exerciseId`
   (`row.exerciseId`).

UI (session screen, only while `row.status == "in_progress"`):
9. A once-per-second `now` tick (LaunchedEffect + delay(1000)) only while in progress.
10. Header row: elapsed text (helper 6) as quiet `labelMedium`/`onSurfaceVariant` beside
    the existing exercise position; omit when null.
11. Fixed footer (`SessionActions`), top line, only when `restState` is non-null: label
    `Rest 1:47 / 3:00` for the rested exercise (the set's `sessionExerciseId` → its
    `ExerciseUi`; target = helper 2 with that exercise's `row.exerciseId` and
    `programRestS`). Text uses `primary` color once elapsed ≥ target. On the same line, a
    small TextButton showing the target (`3:00`) with content description
    "Rest target for <exercise name>, tap to change" that calls `toggleRestTarget`. Keep the
    footer compact (one line, wraps at large font). No new Rest button or countdown.
12. Vibrate once (~400 ms one-shot, `VibrationEffect.createOneShot`, via `VibratorManager`
    on API 31+ else `Vibrator`) when a tick observes elapsed crossing from `< target` to
    `>= target` for the same newest set. If the first observed tick is already past the
    target (app reopened late), do not vibrate. A new log (different newest set) resets it.
    Add `android.permission.VIBRATE` to the manifest. No notification, wake lock or
    foreground service.

Out of scope (do not modify): `android/data/**`, `android/domain/**`, `supabase/**`,
finish/upload/conflict behavior, preview/progress/settings screens, timestamp writes.
Do not read `.env`, `private-data/`.

Worker verification: `scripts/check --offline` passes; `WorkoutTimingTest` covers helpers
1–6 including every null branch and the boundaries 120/121 (toggle), 900/901 s (rest
cutoff), 59/60 min (elapsed text). Don't add layout tests. Commit verified work.
Lead verification (device): timer appears after Log and restarts on next Log, batch of
logs leaves timer from the last one, target toggle persists across app restart,
vibration at target, elapsed header, nothing shown on preview/finished screens.

## Decisions / discoveries
- No Rest button: Log restarts the timer. Rests between sets inside a batch are not
  captured (owner accepted; uses watch for those).
- Rest timer state is derived from data (newest `loggedAt`), so it survives process
  death without extra persistence.
- Rest target default = the program prescription's `rest_s_max` (already cached on the
  phone as `ProgramExerciseRow.restSMax`). Dev data: PPL has 15 exercises at 90–120 s and
  14 at 120–180 s, so defaults are 2:00/3:00 as the owner counts them. Fallback 2:00 when
  there is no prescription (ad-hoc exercise, other programs have none). The per-exercise
  2:00/3:00 toggle is a device-local override (SharedPreferences), not synced.
- Room migrations are required from v4 (real workouts on the phone).
- `SessionDao.finishAt(id, now)` uses `COALESCE(finishedAt, now)` because Retry upload calls
  finish again; `finish(id)` remains for tests and uses the system clock.
- Timestamps go over the wire as ISO-8601 instants (`Instant.toString()`), parsed back with
  `OffsetDateTime.parse` (Postgres returns an offset, sometimes microseconds).
- Timer ticks and crossing detection run only while the activity is resumed; observation
  resets on resume so returning after the target does not cause a delayed vibration.

## Verification
- Step 1a: `scripts/db push` applied `20261102010000_workout_timing.sql` to dev
  (<dev-project-ref>, additive nullable columns only). `scripts/db test`: exit 0,
  127 ok including 10 new in 008.
- Step 1b: `scripts/check --offline` passed (build, lint, JVM tests incl. new
  `timingRoundTripsBetweenServerTimestampsAndPhoneMillis`). Device (test phone):
  `:data:connectedDebugAndroidTest --offline` with MigrationTest, SessionStoreTest,
  WorkoutDatabaseTest → 14/14 passed, including v6→v7 upgrade + finish-once.
  Not run: `scripts/test-sync-integration` (needs `REPKNOT_TEST2_*` credentials, not in
  `.env`); a real upload of a timed session is not yet verified end to end.
- Step 2: `scripts/check --offline` passed (debug build, lint, JVM tests including
  `WorkoutTimingTest`). First run failed on Kotlin nullability errors; fixed before rerun.
  Device checks and actual vibration remain pending lead verification.

## Handoff
Next: lead reviews the Step 2 diff, installs and runs the phone checks listed above,
then updates architecture/status. No unfinished worker changes. Review foreground-only
vibration/crossing behavior and compact footer at large font on device.
