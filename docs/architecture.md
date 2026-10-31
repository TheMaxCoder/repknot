# Architecture

## Foundation

Native Kotlin + Compose, dark-only regardless of system appearance. App ID currently
`com.repknot.app`; min SDK 26, compile/target SDK 36. These are initial defaults.

Dependencies: `app → domain`, `app → data → domain`. Domain is pure Kotlin/JVM.
App owns Compose, ViewModels, navigation, and wiring. Data will own Room, Supabase,
and sync. Repositories separate UI from storage; no direct DAO/network calls in UI.
Domain holds `SetValues`/`LoggedSet` and the `SetRepository` contract; data implements it
with Room (`WorkoutDatabase`, exported schema in `android/data/schemas/`). Manual DI via
`RepknotApplication`. `CloudAccount` (data) talks to Supabase with the publishable key
and the signed-in user's session; RLS restricts every table to its owner. Schema lives in
`supabase/migrations`, tests in `supabase/tests`.

## Rules for the first logging slice

- Previous values are suggestions, never completed sets.
- Session prescription snapshots remain independent of later program edits.
- Session substitutions do not mutate the program.
- Room is the local read source; Supabase is the canonical shared store. Sessions are
  refreshed transactionally after a complete fetch; local drafts and pending edits are
  retained. The current synthetic workout is the only prescription; program and
  previous-performance tables are not present yet.
- Persist edits and pending sync together, retain stable IDs, and handle retries without duplicates.
- Upload acknowledgements must not discard newer local edits, and no writer (Android,
  web, ChatGPT, CLI) may silently overwrite another's change. Revision rules are in
  ADR 001. Session revisions govern sync; individual sets do not have sync revisions.
- Imports retain source IDs and ambiguous raw values.

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
- Building phase: Room schema changes ship without migrations. The v3 database can
  destructively reset the prior synthetic v1/v2 schemas only; the phone's reset was
  explicitly approved. Future schema changes must not silently discard local data;
  add real migrations once Android starts logging real workouts.
