# Architecture

## Foundation

Native Kotlin + Compose, dark-only regardless of system appearance. App ID currently
`com.repknot.app`; min SDK 26, compile/target SDK 36. These are initial defaults.

Workout UI uses wallpaper-derived dynamic dark colors on Android 12+, with the fixed
dark palette as fallback. The owner approved this change in task 017. Logging uses the
session's prescribed load unit, falling back to the exercise's configured default for
ad-hoc exercises. Substitutions use the replacement exercise default and do not reuse
the original start load; editing a saved set preserves its recorded unit. Unit choice is not a
per-set logging control. Bodyweight inputs omit load; no conversions are inferred.

The active workout uses a flat focused logger with Plan/Last comparison, saved-set rows,
filled load/reps entry and RIR controls. The session header's ⋯ offers Edit workout, Replace
current, Session notes, Light day and Discard. The full-list editor moves, inserts, replaces and
removes rows immediately; unlogged removals offer undo, and logged removals need confirmation.
Moves retain row identity and associated sets. Structural DAO writes only apply in progress and
bump the session revision once per change. The editor is also available before Start on an
in-memory preview draft; closing preview discards it, while Start creates the session from it and
starts the clock. A draft is lost on process death. The rest strip remains visible during editing;
the normal footer holds the rest timer and Finish action. Session headers omit date and global
navigation; selection/preview retain their navigation.

The app shell (task 030, [ADR 004](decisions/004-app-navigation-and-history.md)) has
Home, History and Progress bottom destinations with independent saveable detail stacks.
Settings opens from the originating page; detail Back returns to that origin. Reselecting
a tab returns to its root. History has independent Workouts/Exercises list positions
and a saveable exercise search. The focused logger/finish summary hides the bar; Back
preserves the current session and unsaved input, with Resume/summary access from Home.
Workout preview retains bottom navigation and its separate Start action. The bar hides
while the keyboard is open, and outer insets are consumed before nested screen scaffolds.

Home (task 043) shows the suggested workout with its count, first exercise, and direct
Preview and Start actions. The Other workouts sheet offers those actions for each workout;
there is no persistent alternate selection. A current session replaces the suggestion with
its title, status and logger action. Coach notes sit directly below the card, followed by
conditional check-in and training attention rows. The check-in row opens Progress → Body
with Add check-in, and the training row opens Progress. Preview does not start a workout.
The Home header has a labeled Settings icon, no app title.

Program versions (task 038, [ADR 006](decisions/006-program-versions-and-coach-publishing.md)):
the coach workspace publishes new versions directly; the rotation counts sessions from every
cached version with the active program's key, matched by workout key.

Coach notes (task 034, [ADR 005](decisions/005-coach-notes.md)): coach tools publish
append-only briefs with `scripts/repknot publish-notes`; each refresh caches only the newest one
(Room `coach_notes`, replaced wholesale; a fetch failure never blocks uploads). Home shows it in a
collapsible card under the workout card, expanded by default: items for the suggested workout's
exercises or the current session's exercises first, then general, then other workouts. The opened
brief id is device-local (a New badge until opened).
Task 035: items can be marked done on Home (device-local, per brief; hidden elsewhere); the
logger shows an exercise's notes under its title, and Up next/preview show a one-line marker.

History is read-only and reactive to session/exercise/set changes, loading consistent
Room snapshots through a ViewModel. It includes finished, pushed and conflicted records,
including phone-only, light and empty completed sessions, while excluding active drafts.
The exercise directory groups recorded sets by exercise ID. Shared exercise history
preserves existing Progress conclusions and best markers, with compact per-session
performance rows including older unit variations and partial imports. Full notes and
per-set detail are available in the linked workout detail rather than repeated in the
exercise list (owner follow-up to task 030).
Imported (Airtable, `source = import`) workouts display without their session/exercise/set
notes and without the date prefix in titles; the records are unchanged. Screens have no
visible Back buttons: system Back pops each tab's stack (owner preference).
Session detail links and exercise links use stable IDs. Browsing never changes upload
intent or opens arbitrary historical records for editing.

Workout timing (task 027): sessions record `startedAt`/`finishedAt` and sets `loggedAt`
(phone millis, server `timestamptz`; writers that omit them keep stored values). The session
footer shows a rest timer derived from the newest `loggedAt` (every Log restarts it, hidden
after 15 min) with a 2:00/3:00 target from the program's `rest_s_max`, overridable per
exercise on the device; it vibrates once at the target while the app is in the foreground.
Log set closes the keyboard; while typing during a rest, a rest strip with progress sits above
the keyboard instead of the hidden footer (task 035).

Check-in reminder (task 039): an exact alarm posts a notification Mon/Wed/Fri at 08:00 local
time unless a check-in dated today exists (a check-in saved today clears it); the tap opens
Progress → Body with Add check-in. Rescheduled on firing, app start, boot, app update and
clock changes. Rest alerts clear only their own (tagged) notifications.

Workout preview compares the prescription and previous performance in compact columns,
with a fixed Start action above the navigation inset. Target suggestions are no longer
displayed in preview or session UI; previous values and per-set comparisons remain.

Dependencies: `app → domain`, `app → data → domain`. Domain is pure Kotlin/JVM.
App owns Compose, ViewModels, navigation, and wiring. Data will own Room, Supabase,
and sync. Repositories separate UI from storage; no direct DAO/network calls in UI.
Domain holds pure rules (`todaysWorkout`, `prefill`, set value validation). Data owns Room
(`WorkoutDatabase`, exported schema in `android/data/schemas/`): sessions → session
exercises → sets, plus a read-only cache of exercises and programs replaced wholesale on
each refresh. Manual DI via
`RepknotApplication`. `CloudAccount` (data) talks to Supabase with the publishable key
and the signed-in user's session; RLS restricts every table to its owner. Schema lives in
`supabase/migrations`, tests in `supabase/tests`.

## Rules for the first logging slice

- Previous values are suggestions, never completed sets.
- Session prescription snapshots remain independent of later program edits.
- Session substitutions do not mutate the program.
- Room is the local read source; Supabase is the canonical shared store. Sessions are
  refreshed transactionally after a complete fetch; local drafts and pending edits are
  retained. Server and phone share the ADR 002 shape (sessions → session exercises →
  sets, exercises referenced by uuid on push). Programs and exercises are read-only on
  the phone and refreshed before sessions. Upload is explicit per workout (task 028):
  finishing keeps it local; only an Upload tap marks it for push/retry. While it runs, the
  footer shows `SessionSync.step` (account, reference, history, check-ins, workouts) with a
  animated wavy step bar (tasks 041 and 043). The rest timer uses the same custom indicator,
which respects system animation duration settings. History includes
  those local completed records. Refresh still downloads independently.
- Check-ins: the phone caches the server's check-ins (replaced on refresh) and keeps
  phone-added ones `pending`; saving one requests a sync, which upserts every pending row by
  its phone id and then marks it uploaded (failed pushes retry on later syncs). A check-in
  can be deleted until it reaches the server.
  Sessions carry a `light` flag excluded from
  progress statuses.
- Progress (domain `Progress.kt`, `Body.kt`) is computed on the phone from the local cache,
  so it works offline and includes unuploaded workouts; see task 019 for the rules. Broad dips
  (task 037) resolve when their exercises recover; Progress shows at most one dip card: neutral
  for a lone recent dip (≤ 10 days), red for a persisting dip, 2 dips in the last 8 sessions, or a
  dip while losing > 1 %/week; nothing older than 21 days. History tags every dip session.
  Body trends (2026-11-18): the shown rate and goal estimate use up to 12 weeks of check-ins,
  averaged per calendar week, with a Theil–Sen slope (needs 3 different weeks over 2+ weeks), so
  a busy or bloated week cannot swing it. The 4-week least-squares rate is kept for the
  "last 4 weeks" line and for the too-fast checks (verdict and dip alert), which should react quickly.
- Persist edits and pending sync together, retain stable IDs, and handle retries without duplicates.
- Upload acknowledgements must not discard newer local edits, and no writer (Android,
  web, ChatGPT, CLI) may silently overwrite another's change. Revision rules are in
  ADR 001. Session revisions govern sync; individual sets do not have sync revisions.
- Imports retain source IDs and ambiguous raw values.

Training data model: [ADR 002](decisions/002-training-data-model.md) — exercises,
immutable program versions, session prescription snapshots (being implemented in task 006).

Sync model: [ADR 001](decisions/001-sync-model.md) — server data cached and refreshed on
open, in-progress sessions phone-only, finished sessions pushed whole with retry and
revision-based conflict detection. No general sync framework.

## Intentional deferrals

Local verification only. No CI or custom command guards. Supabase schema, sign-in,
and access policies come with the first logging slice. Use synthetic dev data;
review access control and recovery before importing real health records. Record any
actual temporary policy relaxation here when introduced.

- Push receipts are writable only through the owner-scoped `push_session` function;
  clients can read their own receipts but cannot write them. The function runs with
  definer privileges and explicitly scopes parent access to `auth.uid()`.
  Re-evaluate connector privileges before using real health data.
- Supabase CLI is logged in (`supabase login`) with an account-wide token while dev is the
  only project. Before creating a prod project: log out and use `--db-url`, or keep prod in
  a separate account.
- Room migrations: from v4 the phone holds real workouts. v4→v5 (session `light`),
  v5→v6 (check-ins), v6→v7 (timing), v7→v8 (upload intent), v8→v9 (session source) and v9→v10 (coach notes) are explicit migrations tested by `MigrationTest`; every later schema
  change needs one. Only the synthetic v1–v3 databases may still be reset.
- Server: the project holds real training history ([ADR 003](decisions/003-single-canonical-project.md)).
  Migrations must preserve data; the 2026-11-01 drop-and-recreate was the last destructive one.
