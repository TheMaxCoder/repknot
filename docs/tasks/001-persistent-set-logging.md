# 001 — Local set logging

Status: done
Updated: 2026-10-31

## Outcome and acceptance
Open a synthetic workout, explicitly log load/unit/reps/optional RIR, and recover
those exact values after process termination. Suggestions must never become completed
sets without an explicit save. Save the actual edit and pending-sync state atomically.
Keep the screen dark and usable. Invalid input must not replace valid stored data.

## Scope
Minimal domain types, repository, Room persistence, and logging screen. One synthetic
session/exercise is enough. No backend, auth, sync transport, or program management.
Tasks 002 and 003 complete the end-to-end engineering slice; this is not PRD V0 completion.

## Plan and progress
- [x] Verify scaffold baseline/device; initialize Git checkpoint.
- [x] Implement domain validation and repository contract.
- [x] Implement Room storage, atomic pending state, and persistence tests.
- [x] Implement minimal logging UI and verify on device across force-stop/relaunch.
- [x] Run checks and update current architecture/status.

## Decisions and discoveries
- One synthetic session/set with hard-coded stable UUIDs in `LoggingViewModel`.
  Real session creation comes with program/rotation work.
- Pending state is revision-based, not a boolean: each save increments `localRevision`;
  an acknowledgement records `syncedRevision = max(syncedRevision, uploaded revision)`;
  pending ⇔ `localRevision > syncedRevision`. A late ack for an older upload therefore
  cannot clear a newer edit. `SetDao.markSynced` exists for task 003; nothing calls it yet.
- Save is a Room `@Transaction` (read, then insert or increment-update). SQLite UPSERT
  with `DO UPDATE` is unavailable on the framework SQLite of API 26, so it is not used.
- Room schema v1 was redefined in place; exported schema lives in `android/data/schemas/`.
  No Room migrations during the building phase (see architecture "Intentional deferrals").
- Text input is parsed by `parseSetInput` (app), which accepts `,` or `.` as decimal
  separator. Domain `SetValues` enforces value rules.
- Room tests are instrumented (`androidTest`) and need a device: first run needs
  network to fetch the UTP test runner (`--offline` fails until cached).

## Verification (2026-10-31)
- `scripts/check --offline`: build + lint + unit tests pass — `SetValuesTest` (2),
  `SetInputTest` (2). Lint: only pre-existing version/backup warnings.
- `./gradlew :data:connectedDebugAndroidTest` on the test phone (API 36): 4/4 pass —
  suggestions not logged and updates stay pending; revision increments per save;
  late/stale acknowledgement does not clear a newer edit; values survive DB reopen.
- Manual on device: entered `32,5` kg × 7, RIR 3 → saved as 32.5 kg, pending sync;
  `am force-stop` + relaunch → identical values restored; reps `0` → validation error,
  stored set unchanged; screen stays dark with system night mode off (restored to on).
  No Repknot entries in the crash buffer. Fields start empty; the previous-performance
  hint is text only.

## Handoff
Done. Next: [002 — dev backend](002-dev-backend.md).
