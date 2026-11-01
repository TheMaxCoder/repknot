# Delegation

The lead (Claude) decides, plans, writes task files, and reviews. A single worker
model does well-specified implementation through `scripts/delegate`. One model is
used deliberately so its behavior can be learned and the instructions tuned.

Worker: `pi` with `openai-codex/gpt-6.1-terra`, thinking `high`. pi's small default
prompt means `AGENTS.md`, the task file, and `worker-notes.md` dominate what the
worker sees, so instruction changes have attributable effects.

## When to delegate
Delegate when writing the spec and reviewing the diff costs less than doing it:
- the spec fits in about a page and acceptance is mechanically checkable
  (`scripts/check`, `scripts/db test`, a CLI result);
- the work follows an existing pattern: UI slices, tests for existing behavior,
  refactors, importers, boilerplate;
- it touches many files where the lead only needs the result.

Keep it with the lead:
- small edits (under roughly 5–10 tool calls);
- unclear requirements, ADR-level or product decisions;
- sync/conflict/revision logic, RLS/grants/auth, migrations that rewrite data;
- debugging where each step depends on the last observation;
- any category listed under "Kept with the lead" below.

## Loop
1. Lead writes or updates `docs/tasks/NNN-*.md` for the delegated step: scope with an
   explicit "Out of scope", file pointers, acceptance, exact verification commands.
   Pin versions of any new dependency, and keep acceptance and plan consistent: each
   rule is assigned to exactly one verifier (worker JVM test or lead device check).
   "Out of scope" means do not modify; list paths that must not even be read separately.
2. `scripts/delegate docs/tasks/NNN-*.md` (run one task per worktree if parallel).
   Progress streams one line per tool call/message to `.delegate-logs/latest.progress`;
   inside herdr it shows in a "worker" tab automatically, elsewhere run
   `scripts/delegate-watch`. A run with no new progress lines for a long time is stuck.
3. Lead re-runs the checks and reads the diff; never accept on the report alone.
4. On a miss: one retry with specific feedback
   (`scripts/delegate <task> "Review feedback: ..."`). If the retry also misses, the
   lead finishes the work and records the miss below.

## Triage of misses (bounded self-improvement)
Classify every miss before changing instructions:
1. Spec gap (lead's task was ambiguous or incomplete) → improve how the lead writes
   tasks (this file's Loop section); not a worker rule.
2. One-off worker error → fix or retry; note it in the task file only.
3. Same failure twice → add or sharpen a rule in `worker-notes.md` (cap 12).
4. Recurs after a rule exists → stop rewording; add the category to "Kept with the
   lead". Instructions do not fix capability gaps.

Commit instruction changes separately (`Tune worker notes: ...`) so their history
is reviewable.

## Miss log
One line each: date · task · category (1–4) · what happened · action.

- 2026-11-01 · 008 · 1 · new dependency unpinned; spec named no version · lead pinned; Loop rule added.
- 2026-11-01 · 011 · 1 · acceptance said JVM tests for rules 1–14, plan gave 5/6/10–14 to
  device; worker stopped and asked (correct behavior) · spec fixed, resumed with feedback; Loop rule added.
- 2026-11-01 · 011 · 3 · second stop in the same task, over a trivial nullable default
  (`todayState` program name) · worker-notes rule 1 (decide small gaps, record, continue).
- 2026-11-01 · 011 · 1 · spec rules 10/14: set position by count; no reload on history
  change · lead fixed in review.
- 2026-11-01 · 012 · 1 · spec asked to reset fields "after a successful log" without a signal;
  worker stopped (as told to for ViewModel gaps) · answered: derive from set count in state.
- 2026-11-01 · 013 · 1 · out-of-scope line said "do not read" for supabase/ while Context
  required reading two migrations · wording fixed; Loop rule on read vs modify.
- 2026-11-01 · 016 · 1 · RIR stepper range 0–10 vs existing larger values; worker stopped
  (correct per rule 1: affects saved data) · answered: upper bound max(10, current).

## Kept with the lead
(none beyond the defaults above)
- 2026-11-01 · 020 · 1 · lineage unit undefined when the latest session lists the exercise
  with no sets; worker stopped and asked (affects statuses, correct stop) · spec fixed, resumed.
- 2026-11-01 · 020 · 3 · second stop in the task, over a same-date sort tie that rule 1
  already covers · rule 1 sharpened with this example; spec clarified; resumed.
