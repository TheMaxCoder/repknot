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
- [ ] Step 2: worker UI slice (spec added below when step 1 lands).
- [ ] Lead review, install, phone check, architecture/status update.

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

## Handoff
Next: write the step 2 worker spec and delegate.
