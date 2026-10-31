# Architecture

## Foundation

Native Kotlin + Compose, dark-only regardless of system appearance. App ID currently
`com.repknot.app`; min SDK 26, compile/target SDK 36. These are initial defaults.

Dependencies: `app → domain`, `app → data → domain`. Domain is pure Kotlin/JVM.
App owns Compose, ViewModels, navigation, and wiring. Data will own Room, Supabase,
and sync. Repositories separate UI from storage; no direct DAO/network calls in UI.
Domain holds `SetValues`/`LoggedSet` and the `SetRepository` contract; data implements it
with Room (`WorkoutDatabase`, exported schema in `android/data/schemas/`). Manual DI via
`RepknotApplication`. No backend connection, credentials, or Supabase dependency exists yet.

## Rules for the first logging slice

- Previous values are suggestions, never completed sets.
- Session prescription snapshots remain independent of later program edits.
- Session substitutions do not mutate the program.
- Room is the local read source; Supabase will be the canonical shared store.
- Persist edits and pending sync together, retain stable IDs, and handle retries without duplicates.
- Upload acknowledgements must not discard newer local edits. Implemented locally as
  revisions: each save increments `localRevision`; an ack sets `syncedRevision` to
  max(current, uploaded revision); pending ⇔ `localRevision > syncedRevision`.
- Imports retain source IDs and ambiguous raw values.

Start with single-device editing of active sessions. Evaluate a transactional session
snapshot upload during implementation; avoid general multi-client sync machinery now.

## Intentional deferrals

Local verification only. No CI or custom command guards. Supabase schema, sign-in,
and access policies come with the first logging slice. Use synthetic dev data;
review access control and recovery before importing real health records. Record any
actual temporary policy relaxation here when introduced. None exist yet.
