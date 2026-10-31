# Repknot

Native Kotlin/Compose workout logger. Read `Repknot-PRD.md` for product scope,
`docs/architecture.md` for boundaries, and `README.md` for setup.

## Start and resume
- Read `docs/status.md` on entry. Follow the user's current request first.
- For “continue,” resume its linked current task; otherwise take the next ready
  item within the agreed scope. Backlog ideas are not blanket authorization.
- Read the relevant task, architecture notes, and linked decisions, then inspect
  actual files and Git status/diff when available. Notes can be stale; reconcile
  them with evidence. Do not overwrite unrelated or unfinished work.
- A new request may interrupt a task: preserve its handoff instead of erasing it.
- Use one implementation task at a time. No orchestration framework is required.

## Size the work yourself
- Small, clear, locally verifiable change: implement directly; no task file.
- Dependent steps, meaningful uncertainty, or likely handoff: create or update
  `docs/tasks/NNN-short-name.md` with an execution plan and progress.
- Consequential architecture choice that future work depends on: record a short
  ADR in `docs/decisions/NNN-short-name.md` when decided. This is independent of
  task size. Routine implementation details do not need ADRs.
- Upgrade a small task to a plan if investigation warrants it. State the choice
  briefly and proceed; classification and planning do not require approval.
- A task file contains: status, outcome/acceptance, scope, plan/progress,
  decisions/discoveries, verification, and handoff. Keep enough context to resume
  without chat history; link stable repo docs instead of copying them.
- An ADR contains: status/date, context, decision, alternatives, consequences.
  Record accepted choices, not an approval ceremony. If superseded, link the new
  decision rather than silently rewriting the original rationale.

## Checkpoint and finish
- Update task notes after meaningful milestones, discoveries, and before stopping
  or handing off. Record the exact next action, blockers, unfinished changes, and
  commands/results actually verified. Do not wait until context is exhausted.
- Use task statuses: ready, in progress, blocked, done. Keep the blocker and useful
  independent next action explicit; do not mark unfinished work done.
- `docs/status.md` is a short routing page: current state, task link, and next
  priorities. Detailed progress lives only in the task. No separate session diary.
- Completion requires the task's acceptance criteria and relevant checks. Record
  pending device/manual verification honestly. Update architecture docs if behavior
  changes. Keep completed task records; do not read every old task on startup.
- For one-shot changes, update durable docs only when project state or decisions
  change; the final report and Git diff are enough for trivial edits.

## Commands
- `scripts/check` — debug build, Android lint, and available unit tests.
- `cd android && ./gradlew :app:installDebug` — install on a connected device.
- `scripts/db push` / `scripts/db test` — apply migrations / run pgTAP tests on the linked
  dev project (refuses any other project). Tests must roll back; see existing test layout.
- `scripts/repknot …` — CLI as the signed-in user (`--as test2` for the second user).

## Boundaries
- Dark-only UI, including launch background and system bars. No theme toggle or dynamic colors.
- `android/app`: UI and wiring. `domain`: pure Kotlin. `data`: persistence and sync.
- Keep the foundation small; add libraries, abstractions, and tooling when used.
- Preserve unrelated changes. Never claim checks passed unless they ran.

## Process and autonomy
State a short plan for substantial work, then proceed within the authorized task.
Routine code, dependency, test, and dev Supabase changes do not need repeated approval.
Confirm the database target is dev before operating; ask about destructive actions on
retained data or unauthorized production/real-data operations. Keep credentials and
personal health exports out of Git and privileged keys out of clients.

Early security is intentionally lightweight for synthetic development. Record temporary
relaxations and revisit them before real health data. Run relevant checks, inspect the
diff, and report limitations. Tests and check scripts may evolve; don't weaken them to
hide failures. No mandatory CI, command-blocking hooks, or approval for every plan.
