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
- Use one implementation task at a time per working tree (parallel work uses separate
  Git worktrees). No orchestration framework is required.

## Size the work yourself
- Small, clear, locally verifiable change: implement directly; no task file.
- Dependent steps, meaningful uncertainty, or likely handoff: create or update
  `docs/tasks/NNN-short-name.md` with an execution plan and progress.
- Consequential architecture choice that future work depends on: record a short
  ADR in `docs/decisions/NNN-short-name.md` when decided. This is independent of
  task size. Routine implementation details do not need ADRs.
- Upgrade a small task to a plan if investigation warrants it. State the choice
  briefly and proceed; classification and planning do not require approval.
- A task file contains: status, starting commit, outcome/acceptance, scope, plan/progress,
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

## Commits and review handoff
Work is reviewed from Git, so commit as you go; this is standing authorization to
commit (not push) on `main`.
- Commit each verified, coherent step (e.g. migration + its tests; one UI slice), not
  a whole task at once. Relevant checks must pass first. Never commit a broken build;
  if stopping mid-step, leave it uncommitted and list it in the task handoff.
- Stage files explicitly and check `git status`/the staged diff: no secrets, `.env`,
  `private-data/`, build output, or unrelated changes. Never rewrite existing commits.
- Message: imperative subject with task number; body says what and why, then:
  `Verified:` exact commands and results · `Not verified:` what is pending or skipped ·
  `Review:` anything needing a careful look (omit empty lines only if truly none).
- Always flag under `Review:` (and in the task notes): RLS/grants/security-definer or
  auth changes, migrations that drop or rewrite data, sync/conflict/revision logic,
  destructive device or database actions, weakened or skipped tests, new dependencies,
  deviations from an ADR or the task plan, and guesses about requirements.
- Record the task's starting commit in its file so the reviewer can diff the range.
  The final report lists commits, verification, open concerns, and the next action.
- Reviewers re-run checks rather than trusting reports, and verify claims against the diff.
- Never print credential values (from `.env` or elsewhere) in output, logs, or commits.

## Delegation
The Claude session leads (judgment, task files, review); well-specified implementation
goes to one worker model via `scripts/delegate <task-file>`. When to delegate, the review
loop, and the bounded rules for tuning `docs/delegation/worker-notes.md` live in
`docs/delegation/README.md`. If you are the worker, execute the task; don't re-delegate.

## Commands
- `scripts/check` — debug build, Android lint, and available unit tests.
- `cd android && ./gradlew :app:installDebug` — install on a connected device.
- `scripts/db push` / `scripts/db test` — apply migrations / run pgTAP tests on the linked
  dev project (refuses any other project). Tests must roll back; see existing test layout.
- `scripts/repknot …` — CLI as the signed-in user (`--as test2` for the second user).
- `scripts/airtable-normalize <export-dir>` — regenerate import-ready CSVs from a
  private Airtable export (`private-data/README.md` describes the layout).
- `scripts/delegate <task-file> [feedback]` — run a task through the worker model;
  logs go to the ignored `.delegate-logs/`.

## Boundaries
- Dark-only UI, including launch background and system bars. Use wallpaper-derived dynamic
  dark colors on Android 12+, with a fixed dark fallback on older devices. No theme toggle.
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
