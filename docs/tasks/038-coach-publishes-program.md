# 038 — Coach publishes program changes directly

Status: done (device test and phone install pending) · starting commit `032e789` · decision: [ADR 006](../decisions/006-program-versions-and-coach-publishing.md)

## Outcome / acceptance
- The coach workspace (`../health`) publishes program changes and coach briefs to the app
  itself, then tells the owner what changed. The owner does not have to approve each one or
  switch to this repo.
- Publishing a new version of the active program does not reset the workout rotation. The
  next workout after a republish is the same one as before it.
- Existing behavior is unchanged when there is only one version.

## Scope
1. Domain/data: `todaysWorkout` counts finished sessions from every cached version of the
   active program's key (`Program.versionIds`). No Room schema change: the phone already
   caches all program versions.
2. Coach workspace (separate local repo, not part of this Git history): the program JSON
   source moves to `../health/programs/`, a `./publish-program` wrapper is added, and
   `AGENTS.md` lets the coach publish briefs and program versions without waiting for an OK.
3. Docs: ADR 006, architecture note, `private-data/README.md` pointer (ignored file).

Out of scope: showing last-used load in the Plan row (suggested to the owner, not requested).

## Plan / progress
- [x] 1. Domain + mapping change with JVM tests; `scripts/check`; commit.
- [x] 2. Coach workspace wiring; dry-run check that the wrapper finds the CLI.
- [x] 3. ADR + architecture + status; commit.

## Decisions / discoveries
- `publish_program` inserts a new `programs` row per version (new id). `todaysWorkout`
  matched `session.programId == program.id`, so any republish restarted the rotation.
- `program_workouts` rows of old versions stay cached, so the rest-timer lookup by a
  session's own `programId` keeps working for sessions started before a republish.

## Verification
- Step 1: `scripts/check` passed (debug build, lint, app/data/domain unit tests; new
  `ProgramTest.rotationContinuesAcrossVersionsOfTheSameProgram` and mapping test).
  `./gradlew :data:compileDebugAndroidTestKotlin` passed. The new device test
  `WorkoutDatabaseTest.activeProgramListsEveryVersionOfItsKey` was **not run**: no
  device or emulator attached, and no AVD configured.

- Step 2: before moving it, `private-data/programs/ppl-2026-11.json` was compared field by
  field with the live program from `../health/refresh`: same sequence, exercises, sets, ranges,
  rest, loads, units and notes (live is v1). `../health/publish-program` without arguments
  prints its usage. Nothing was published.

## Handoff
- Changes in `../health` (separate local repo, left **uncommitted** for the owner, mixed with
  their earlier uncommitted edits): `AGENTS.md` (direct publishing, "Program changes"
  section), new `publish-program`, `programs/ppl-2026-11.json` (moved from `private-data/`).
- Owner: install the new build (`cd android && ./gradlew :app:installDebug`) before the
  coach's first program republish; older builds restart the rotation after a republish.
- Not run: `WorkoutDatabaseTest.activeProgramListsEveryVersionOfItsKey` (needs a device).
