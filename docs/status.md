# Project status

Updated: 2026-10-31

## Current state

- Android app/domain/data modules; dark-only Compose logging screen for one synthetic set.
- Last build verification (2026-10-31): `scripts/check --offline` passed with 4 unit
  tests. Lint has only non-blocking version/backup warnings.
- Device smoke test (2026-10-31): installed the existing debug APK on the test phone
  (API 36). Cold launch and force-stop/relaunch succeeded. Screenshots confirmed
  readable content, unobstructed system-bar insets, and dark appearance with both
  light and dark system settings. Original system dark mode was restored. No
  Repknot entries appeared in the crash buffer. No app code changed during testing.
- Task 001 done (2026-10-31): one synthetic set can be logged locally with Room,
  survives force-stop, uses revision-based pending-sync state. Verified by unit tests,
  4 device Room tests, and a manual device run — details in the task.
- Sync (task 003) is not implemented.
- Shared startup, task-sizing, and handoff instructions now live in `AGENTS.md`.

## Current task

[003 — Pull on open, push finished sessions](tasks/003-session-sync.md) is ready, not
started. Task 002 is done: dev schema + RLS (22/22 tests), CLI, and Android sign-in/upload
verified end to end.

## Next priorities

1. Task 003: pull on open + push finished sessions, per
   [ADR 001](decisions/001-sync-model.md) (accounts for ChatGPT/web as other writers).
2. ChatGPT write access (PRD §4): verify integration options before committing; writes
   follow ADR 001 revision rules.
3. After the slice, expand session editing, previous-performance suggestions, program
   rotation, check-ins, import/reconciliation, then the web companion per the PRD.
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
