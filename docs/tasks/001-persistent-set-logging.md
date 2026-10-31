# 001 — Local set logging

Status: in progress
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
- [ ] Implement domain validation and repository contract.
- [ ] Implement Room storage, atomic pending state, and persistence tests.
- [ ] Implement minimal logging UI and verify on device across force-stop/relaunch.
- [ ] Run checks and update current architecture/status.

## Decisions and discoveries
The existing app/domain/data modules are sufficient. Use one synthetic exercise and
stable session/set IDs; persist pending state alongside the set within the same row.
No network queue/protocol framework is needed until task 003. Add test dependencies
with the behavior they test. Reference `../architecture.md` for shared invariants.

## Verification
Scaffold build/lint and device smoke tests passed previously. Feature verification
has not run yet. Run `scripts/check`, then connected Room tests and device restart checks.

## Handoff
Next: add minimal validated domain types, Room entity/DAO/database, and logging screen.
