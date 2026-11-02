# 027 — Workout timing and rest timer

Status: in progress (implementation done; owner decision on screen-off pending)
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
- [x] Lead review, install, phone check, architecture/status update.
- [x] Owner decision: phone sits on a stand and owner walks during rests, music playing →
      silent phone notification at the rest target, mirrored to the paired smartwatch.
- [ ] Owner watch test: does the watch buzz for the silent notification?

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
- Lead review fixes: target shown once as the tappable button; ticking clock read only in
  small composables; `LocalLifecycleOwner`; vibration uses alarm usage (touch haptics can
  be disabled system-wide).
- Discovery: the owner's phone has a 60 s screen timeout. When the screen turns off the
  activity pauses, so a 2–3 min rest usually ends with the screen off and no vibration.
  Options for the owner: keep the screen on while a rest timer is running, or a background
  alert (exact alarm/notification — the planned follow-up).
- Decided (owner, 2026-11-02): watch alert. `RestAlert` schedules one exact alarm per rest
  (`setExactAndAllowWhileIdle`, same PendingIntent so each Log replaces it; cancelled when
  no rest is running: finished, discarded, past target, no sets). The receiver posts a
  notification on channel "Rest timer" (IMPORTANCE_HIGH, no sound, no vibration) so music
  is not interrupted and the watch companion app forwards it to the watch. In-app vibration and the
  VIBRATE permission were removed. Exact alarms via `USE_EXACT_ALARM` (granted without a
  prompt; Play restricts it to alarm apps, Repknot is sideloaded) and
  `SCHEDULE_EXACT_ALARM` maxSdk 32. POST_NOTIFICATIONS is requested when a session is in
  progress. Unknown: whether the watch forwards a notification that is silent on the phone;
  fallback is a channel with sound/vibration.
- Owner test 1 (2026-11-02): the alarm fired and the notification appeared on the phone
  on time, but the watch did not buzz (watch DND/sleep off). Cause assumed: a channel
  with no sound and no vibration is non-alerting and the watch does not forward/buzz for it.
  Fix: new channel `rest_alert` (old `rest_timer` deleted; channel settings are
  immutable) with vibration 300/150/300 ms and no sound. VIBRATE permission restored.
- Owner test 2: phone alerts fired on time on `rest_alert` with the vibration pattern
  (12:58:51, 13:01:07; dumpsys vibrator_manager), but the watch still did not buzz.
  Forwarding works (test 1 showed it in the watch's notification list). Open: watch
  side settings, or the watch not buzzing while the phone is in use; next owner check is a
  rest with the phone locked and whether other apps (e.g. WhatsApp) buzz the watch.
- Discovery during test 2: the test workout became "finished" without the owner meaning to
  (between ~13:07 and ~13:09, inferred from the missing alert for the Lateral raise set).
  Finish was a single unconfirmed tap at the bottom of a footer whose height changes
  (rest line, IME). Fix: Finish asks for confirmation ("N sets logged"); finished workouts
  that were never uploaded show "Resume workout" (`SessionDao.resume`: back to in_progress,
  finishedAt cleared; refused once uploaded). Verified on phone: resumed the stuck test
  workout, confirmation shown and cancelled, test workout discarded.

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
- Lead: re-ran `scripts/check --offline` (passed) after review fixes. Test phone:
  install over existing data, launch, no crash entries. Started a test Push 1: header
  "0 min"→"2 min", no timer before the first set; logged a test set → "Rest 0:05 / 3:00"
  (3:00 from the exercise's 120–180 s prescription); reinstall (process death) → timer resumed
  at 0:40; toggle 3:00→2:00; past target the text turns primary; `dumpsys
  vibrator_manager` showed exactly one 411 ms vibration from com.repknot.app at the 2:00
  crossing (screen held on with stayon for the test, original setting 7 restored);
  toggled back to 3:00 (DB press override now stored as 180 = program value); test
  workout discarded, Today screen shows no timer.
- Not verified on device: alarm-usage vibration (changed after the device run); large
  font footer; real upload of a timed session.

## Handoff
Implemented, installed on the phone, and checked. Commits: 3c0c00d (server), 5812eb0
(phone timing), 748443a (spec), 1c6a025 (worker UI), 0ea7a93 (lead fixes).
- Owner: still no watch buzz with the vibrating channel. v3 channel `rest_alert_sound` adds
  the default notification sound (USAGE_NOTIFICATION, ducks music briefly); v1/v2 channels
  are deleted on first post. Installed; phone was locked so the owner runs the test.
- Owner test 3: sound channel, still no watch buzz; WhatsApp messages do buzz the watch, so
  forwarding/vibration work for other apps. New hypothesis: every alert reused notification
  id 27, so the watch may treat later alerts as updates of an existing notification. Each alert
  now gets its own id (epoch seconds); clearing uses cancelAll (the app's only notifications).
- Owner report: during testing, after switching apps and back, "all logged sets were gone".
  Not caused by the lead (owner was not interacting with the lead then; no process death
  after 13:24:22, but the activity task changed, i.e. the screen was recreated). The
  session screen has no BackHandler, so Back finishes the activity, and the notification
  intent could start a new instance. A fresh screen auto-selected the first unfinished
  exercise, so after completing DB press it showed Lateral raise "0 / 4" — looks like loss
  while the sets are stored. Unconfirmed (phone locked; owner to check View all exercises).
  Fix: with no explicit selection, select the exercise of the newest logged set; the
  notification intent uses NEW_TASK|CLEAR_TOP|SINGLE_TOP to reuse the existing screen.
Next: owner confirms via View all exercises that the earlier sets still exist (if not, this
is real data loss and needs investigation), and tests a rest alert with the per-alert id.
If not: check watch companion app → Notifications → Repknot and the phone's autostart/battery
settings for the watch app; then try a channel with phone vibration. A lock-screen countdown
(chronometer notification) is a possible follow-up. Time analysis UI later, once data exists.
