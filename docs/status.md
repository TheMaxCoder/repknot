# Project status

Updated: 2026-11-02

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

[027 — Workout timing and rest timer](tasks/027-workout-timing.md): timestamps (server + phone v7), rest timer with program-based 2:00/3:00 targets and vibration are implemented and phone-checked. Rest alert: phone notification (vibration, no sound) works; the paired smartwatch does not buzz for it yet — investigation paused, next experiments in the task.

[026 — Flat focused workout logger](tasks/026-flat-workout-logger.md) is done: flat session layout, compact Plan/Last, simplified footer and Light day/Discard menu. Installed and phone-tested (log/edit/delete, RIR, keyboard, notes and selection).

[025 — Compact workout preview](tasks/025-workout-preview.md) is done: compact Plan/Last comparison, fixed Start, full scrollable exercise list, no target suggestion. Installed and device-checked, including local start/discard and back navigation.

[024 — Progress screen redesign](tasks/024-progress-redesign.md) is implemented, checked, installed, and visually inspected at the phone’s current size; owner feedback and interaction/edge-case checks remain pending.

[017 — Workout redesign](tasks/017-workout-redesign.md) is implemented and device-smoke-tested: compact
exercise card, Up next, separated actions, dynamic dark colors, and configured load units.

Milestone [006 — Training data model and program-driven workouts](tasks/006-training-data-model.md)
is done (design: [ADR 002](decisions/002-training-data-model.md); steps delegated per
[docs/delegation](delegation/README.md) as tasks 007–009 and 011–013, lead work in 010).
The phone app previews and starts today's PPL workout, logs and edits sets with last
values from the full imported history ([ADR 003](decisions/003-single-canonical-project.md)),
and keeps workouts local until upload is enabled in Settings.

Milestone [019 — Progress and regression](tasks/019-progress-milestone.md) is done: workout
card ▲/=/▼, Progress screen (statuses, broad dips, exercise history), body card
(waist goal, trends, cut check, check-in entry), light-day flag (tasks 020–023).

## Next priorities

1. Real logging on the phone from 2026-11-02 (upload off until the owner enables it in
   Settings); fix what hurts in the gym.
2. ChatGPT integration, web companion per the PRD; progress follow-ups in task 019.

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
