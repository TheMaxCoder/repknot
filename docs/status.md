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

[001 — First persistent workout logging slice](tasks/001-persistent-set-logging.md)
is ready, not started. Its local persistence work can begin before Supabase details
are resolved. The task contains acceptance criteria, progress, and the next action.

## Next priorities

1. Skeleton device smoke test complete; functional logging tests await implementation.
2. Implement task 001, resolving hosted dev access and sign-in when needed.
3. After the slice, expand session editing, previous-performance suggestions, program
   rotation, check-ins, import/reconciliation, then the web companion per the PRD.
   These are roadmap directions, not claims that their detailed scope is settled.

## Environment and handoff limitations

- This managed session exposes `.git` as an empty read-only directory; `git status`
  previously failed with “not a git repository.” Files exist, but commits/history
  have not been verified here. Check Git in the normal workspace before relying on
  history; do not replace the managed `.git` directory.
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
