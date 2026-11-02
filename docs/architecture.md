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
filled load/reps entry and RIR controls. Its footer contains Add exercise, Session notes,
a workout menu (Light day/Discard), and a quiet Finish action. Session headers omit date
and global navigation; selection/preview retain their navigation.

Workout timing (task 027): sessions record `startedAt`/`finishedAt` and sets `loggedAt`
(phone millis, server `timestamptz`; writers that omit them keep stored values). The session
footer shows a rest timer derived from the newest `loggedAt` (every Log restarts it, hidden
after 15 min) with a 2:00/3:00 target from the program's `rest_s_max`, overridable per
exercise on the device; it vibrates once at the target while the app is in the foreground.

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
  the phone and refreshed before sessions. Upload is a device setting (Settings → Upload
  workouts), off by default: refresh still downloads, finished sessions stay local
  until it is enabled.
- Check-ins: the phone caches the server's check-ins (replaced on refresh) and keeps
  phone-added ones `pending`; with upload on they are upserted by their phone id, then
  marked uploaded. Add-only on the phone. Sessions carry a `light` flag excluded from
  progress statuses.
- Progress (domain `Progress.kt`, `Body.kt`) is computed on the phone from the local cache,
  so it works offline and includes unuploaded workouts; see task 019 for the rules.
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
  v5→v6 (check-ins) and v6→v7 (timing) are explicit migrations tested by `MigrationTest`; every later schema
  change needs one. Only the synthetic v1–v3 databases may still be reset.
- Server: the project holds real training history ([ADR 003](decisions/003-single-canonical-project.md)).
  Migrations must preserve data; the 2026-11-01 drop-and-recreate was the last destructive one.