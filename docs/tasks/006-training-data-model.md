# 006 — Training data model and program-driven workouts

Status: in progress
Updated: 2026-11-01
Starting commit: 78296df

## Outcome and acceptance
The PPL program (starts 2026-11-02) drives the daily workout, per
[ADR 002](../decisions/002-training-data-model.md):
- Dev schema has exercises, immutable program versions, session exercises with
  prescription snapshots, extended units/measures, import refs; pgTAP covers ownership,
  immutability, one active program, idempotent import refs.
- The PPL program is published to dev from `private-data/programs/ppl-2026-11.json`;
  exercises seeded from the normalized export.
- Android shows today's workout (next in sequence, manual override), logs multiple
  exercises/sets with previous-value prefill, and pushes nested sessions.
- Airtable history import works on dev with synthetic-shaped data first; real import goes
  to prod only after prod exists and access review (see architecture deferrals).

## Scope
In: schema + functions + tests, program publish script, Android logging screen rebuild,
importer. Out: check-in UI, web companion, ChatGPT connector identity (1a), analytics.

## Plan / progress
1. [x] Clean Airtable export → `private-data/airtable-health-2026-10-31/` (Health base only;
   other bases, empty base, redundant raw JSON removed). Normalizer `scripts/airtable-normalize`.
2. [x] Structure the PPL program → `private-data/programs/ppl-2026-11.json` (cross-checked
   against the markdown's weekly set table; all exercises map to export slugs).
3. [x] ADR 002.
4. [x] Migration: exercises, programs/workouts/exercises, `publish_program`, session_exercises,
   set unit/measure changes, `source_ref`/`raw`, checkins; update `session_snapshot`,
   `replace_session_sets`, `push_session`, `save_session` for nested payloads. Tests.
5. [x] `scripts/repknot` commands: `seed-exercises`, `publish-program <json>` — delegated as
   [008](008-cli-seed-and-publish.md) (done; real exercises + PPL v1 published to dev).
6. [x] Domain: today's-workout selection and prefill as pure Kotlin with unit tests —
   delegated as [007](007-domain-today-and-prefill.md) (done).
7. [x] Data/Room: cache programs/exercises ([009](009-room-reference-cache.md), delegated);
   session_exercises + nested push/pull ([010](010-nested-session-sync.md), lead).
8. [ ] App: workout screen (exercise list, set rows, previous values, add/remove/substitute).
9. [ ] Importer (dev, synthetic-shaped fixture first); derive the previous Upper/Lower
   program version from the Airtable Exercises table — its roles are free text, so the
   derived version needs user review before import.

## Decisions and discoveries
- Export: every exercise log maps to sets; ambiguous set rows are flagged `review` — carries without
  duration, missing loads/reps, partial reps, RIR count mismatches. Source CSVs keep originals.
- RIR ranges normalize to the lower bound; `rir_raw` keeps the text.
- Old Airtable "Low-Incline Bench Press" also holds early flat/incline bench logs; the
  DB chest press logs were on a separate exercise with unconfirmed bench angle.
- Units in history: kg, plates and bodyweight, plus a few unknown.

- Step 4 (2026-11-01, lead): dev session tables are dropped and recreated rather than
  migrated (synthetic data; owner said "use judgment, go" after the drop-vs-squash choice).
  Squash all migrations into one baseline before prod exists.
- Step 4 shape choices beyond ADR 002: `programs.key` names the program family (`ppl`) so
  versions are unique per key; `programs.details jsonb` keeps informational fields (goal,
  review date, source); session-exercise snapshot columns are prefixed `planned_`;
  `session_sets` keeps `session_id` (composite FK through `session_exercises`) for the
  revision trigger and queries. Payloads reference exercises by slug or uuid.
- No server `finished_at`: per ADR 001 every server session is finished. Step 7 should
  order sessions for `todaysWorkout`/`prefill` by `performed_on`, then `number`.
- Checkins table added; dated health-state/training-habit context records deferred to
  step 9 (importer), which is where they are first needed.
- Sets with a `raw` original may be incomplete (no unit/reps/load); all other sets need
  a unit, reps or duration, and a load unless bodyweight.

## Verification
- `scripts/airtable-normalize private-data/airtable-health-2026-10-31`: every source log
  produces ≥1 set (asserted); raw JSON matched CSV with 0 differences before deletion.
- `git check-ignore` confirms `private-data/` is ignored.

- Step 4 (2026-11-01): owner ran `scripts/db push` (training model, drops/recreates dev
  session tables). First `scripts/db test`: 44/45 — a foreign/unknown exercise slug was
  silently dropped because the planner skipped `resolve_exercise` inside a join. Fixed in
  `20261101040000_resolve_exercises_first.sql` (resolve up front + row-count guard), pushed;
  `scripts/db test` then passed 001 23/23, 002 17/17, 003 10/10, 004 7/7, 005 45/45.
  `scripts/repknot log-test` / `list` / `show` against dev: nested session #39 created and
  read back (1 exercise, 1 set). `deno check` on a .ts copy of the CLI passed.

## Handoff
Next action: step 8 — workout screen (today's workout, set rows with prefill, add/remove/
substitute, finish/share/conflict UI reusing `SyncViewModel`). Local ids must be uuids. `save_session` always records `source = 'chatgpt'` (cosmetic).
