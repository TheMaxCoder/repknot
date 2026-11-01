# Project status

Updated: 2026-11-01

## Current state

- Android app/domain/data modules; dark-only Compose logging screen for one synthetic set.
- Last build verification (2026-11-01): `scripts/check --offline` passed; see task 004
  for database and device verification; task 005 adds the latest cleanup checks. Lint has only non-blocking warnings.
- Device smoke test (2026-10-31): installed the existing debug APK on the test phone
  (API 36). Cold launch and force-stop/relaunch succeeded. Screenshots confirmed
  readable content, unobstructed system-bar insets, and dark appearance with both
  light and dark system settings. Original system dark mode was restored. No
  Repknot entries appeared in the crash buffer. No app code changed during testing.
- Task 001 done (2026-10-31): one synthetic set can be logged locally with Room,
  survives force-stop, uses revision-based pending-sync state. Verified by unit tests,
  4 device Room tests, and a manual device run — details in the task.
- Task 003 done: refresh, offline retry, conflict handling, sharing, and authenticated
  `save_session` tested against the dev backend and Android device. See the task for
  evidence and the separately deferred ChatGPT integration.
- Shared startup, task-sizing, and handoff instructions now live in `AGENTS.md`.

## Current task

[006 — Training data model and program-driven workouts](tasks/006-training-data-model.md)
(in progress; design in [ADR 002](decisions/002-training-data-model.md)). Tasks 003–005 are done.
Steps 4–7 done (5 and 6 delegated: [008](tasks/008-cli-seed-and-publish.md),
[007](tasks/007-domain-today-and-prefill.md)); PPL v1 is published on dev. Implementation
work may be delegated per [docs/delegation](delegation/README.md).

## Next priorities

1. Task 006: workout screen (step 8) → import (step 9). Device tests for 010 pending.
2. Verify a narrowly scoped, authenticated ChatGPT integration before using it to write
   sessions; see [the draft instructions](chatgpt-session-instructions.md).
3. Check-ins UI, prod project + real import, then the web companion per the PRD.
   These are roadmap directions, not claims that their detailed scope is settled.

## Environment and handoff limitations

- Git was initialized on `main` in the normal filesystem view; initial scaffold
  checkpoint: `9c44212`. The sandbox still masks `.git`; Git operations may require
  the normal filesystem view. No hooks or remote were configured.
- `AGENTS.md` exists and `CLAUDE.md` imports it. Fresh-session loading has not been
  tested in Codex, Claude, or Antigravity. Antigravity is not on this session's PATH
  and `.agents` is read-only here. Configure its adapter in an environment that can
  write the tool's native rules directory if automatic loading is absent.
- One-time smoke check per tool: in a fresh session ask it to read repo guidance and
  identify the current task, verification command, and when to create a plan/ADR.
  For automatic-loading verification, ask without explicitly naming the files.
  Correct answers are a useful smoke check, not proof of future compliance.

Operational guidance is in `AGENTS.md`, README, and architecture notes. The two
setup proposals are historical rationale, not required startup reading.
