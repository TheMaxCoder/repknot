# 001 — First persistent workout logging slice

Status: ready  
Updated: 2026-10-31

## Outcome and acceptance

Prove that a recorded set survives app termination and unreliable connectivity,
then reaches the canonical Supabase store without duplication. This is engineering
Milestone 0, not completion of the PRD's broader first milestone.

- A synthetic seeded workout can be opened and one actual set recorded.
- Load, unit, reps, and optional RIR survive process termination and reopening.
- Previous/suggested values are never automatically marked completed.
- An authenticated user can synchronize the session to the hosted dev project.
- Losing the acknowledgement after a successful save and retrying creates no duplicate.
- Editing locally during an upload preserves the newer edit for a subsequent save.
- Android and a CLI using a normal authenticated identity can read back the exact
  saved data. The CLI can also perform an authorized write and read it back.
- The screen remains usable and dark under both light and dark system settings.

## Scope

Implement the minimum model, repository, Room persistence, logging UI, dev schema,
sign-in, and upload behavior needed for those examples. Keep data synthetic.
Use `docs/architecture.md` and `Repknot-PRD.md` for constraints.

Defer real imports, full program management/rotation, charts, food logging, web UI,
general concurrent editing, and production deployment. Do not scaffold those systems.

## Plan and progress

- [ ] Inspect the skeleton and establish relevant build/device baseline.
- [ ] Add minimal domain types and repository contract for a session and set.
- [ ] Implement Room storage and persist edits/pending state transactionally.
- [ ] Connect a minimal Compose logging screen and verify restart persistence.
- [ ] Identify the authorized hosted dev target and resolve sign-in method.
- [ ] Add minimum schema, ownership policies, synthetic seed, and authenticated access.
- [ ] Implement transactional upload with stable IDs, retry identity, revision checks,
      and handling for edits made during upload. Record the chosen protocol in an ADR.
- [ ] Verify loss/retry behavior and authenticated Android/CLI read/write.
- [ ] Run relevant checks and device acceptance; update architecture and status.

These steps are a starting plan. Adjust them as evidence warrants without seeking
approval for normal implementation details. Do not build a general sync framework.

## Decisions and discoveries

- Existing modules: `android/app`, `android/domain`, `android/data`.
- App ID: `com.repknot.app`; minimum API 26; compile/target API 36.
- Room and Supabase libraries have not been selected or added yet.
- Candidate protocol: upload a whole session through one database transaction.
  A stable mutation ID distinguishes retry from a new edit; an expected revision
  detects stale writes. Reusing an ID requires the same payload. Acknowledgement
  must not clear newer local pending changes. Confirm details during implementation.
- Supabase project identity, access configuration, and sign-in preference are unknown.
  Inspect available dev setup first; ask for only what remains missing. Never put
  credentials in this task file. Local persistence does not depend on these answers.

## Verification

Task implementation has not started; no task acceptance criteria have been verified.
Scaffold verification is recorded in `docs/status.md`.

- Run `scripts/check` from the repo root for build, lint, and available unit tests.
- Add meaningful tests with the risky behavior: Room persistence/transactions,
  lost-response retry, stale revision handling, and edits during upload.
- Run device tests/manual checks on an actual connected device or emulator. Record
  commands/environment and outcomes here; a build alone does not prove persistence.
- Add an executable authenticated CLI check when the dev backend exists. Confirm
  target identity before writes and keep synthetic test records identifiable.

## Handoff

Next action: inspect the existing Gradle configuration and implement the smallest
local session/set model and Room-backed logging flow. Check device availability;
if none is connected, continue code and available tests while recording that gap.

No feature implementation changes or database operations have been made for this
task. Supabase/sign-in are later unresolved inputs, not blockers to starting local work.
