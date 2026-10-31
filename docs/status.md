# Project status

Updated: 2026-10-31

## Current state

- Android skeleton exists: dark-only Compose launch screen; app/domain/data modules.
- Last build verification (2026-10-31): `scripts/check --offline` passed after a
  theme API compatibility fix. Lint has non-blocking version/backup warnings.
  Unit-test tasks reported NO-SOURCE; no behavior tests exist yet.
- Device smoke test (2026-10-31): installed the existing debug APK on the test phone
  (API 36). Cold launch and force-stop/relaunch succeeded. Screenshots confirmed
  readable content, unobstructed system-bar insets, and dark appearance with both
  light and dark system settings. Original system dark mode was restored. No
  Repknot entries appeared in the crash buffer. No app code changed during testing.
- Room, authentication, Supabase configuration, and workout logging are not implemented.
- Shared startup, task-sizing, and handoff instructions now live in `AGENTS.md`.

## Current task

[001 — Local set logging](tasks/001-persistent-set-logging.md)
is in progress. Backend/auth and sync are separate follow-up tasks. The task contains acceptance criteria, progress, and the next action.

## Next priorities

1. Skeleton device smoke test complete; functional logging tests await implementation.
2. Implement task 001 locally, then [002 backend/auth](tasks/002-dev-backend.md) and
   [003 sync](tasks/003-session-sync.md).
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
