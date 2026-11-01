# 013 — Airtable history importer (synthetic-verified on dev)

Status: ready (starts after 012 is delegated; independent files)
Updated: 2026-11-01
Starting commit: (worker: record `git rev-parse --short HEAD` when you start)
Parent: [006](006-training-data-model.md) step 9 · Delegated: yes (see
[delegation](../delegation/README.md))

## Outcome and acceptance
The normalized Airtable export can be imported idempotently into Supabase as the signed-in
user, keeping source ids and ambiguous originals (PRD §3 item 8, ADR 002 "Import"). This
milestone verifies it on **dev with synthetic data as test2 only**; the real history goes
to prod later (ADR 002) and is not part of this task. Accepted when:
- The normalizer changes, synthetic fixture and importer behave as specified below.
- The verification sequence below passes as test2 on dev; outputs recorded here.
- `deno check` passes on `.ts` copies of the new script; the normalizer still runs.

## Scope
In: `scripts/airtable-normalize` (additive column changes only), new fixture directory
`scripts/fixtures/airtable-synthetic/source/` (synthetic CSVs, committed), new
`scripts/airtable-import`, `private-data/README.md` is **out** (lead updates it), this file.
Out of scope: anything under `android/`, `supabase/`, `private-data/` (do not read, copy, or
write it), `scripts/repknot`, new libraries beyond `npm:@supabase/supabase-js@2` and
`jsr:@std/csv@1` (both already used). Never run the importer without `--as test2`.

## Context to read
- Normalizer: `scripts/airtable-normalize` — its docstring, the source formats it parses
  (Airtable JSON link/select cells, `__airtable_record_id`, free-text reps/RIR/loads), and
  the normalized CSV columns it writes. Source CSV headers:
  `Check-ins.csv`: `__airtable_record_id,__airtable_created_time,Check-in,Date,Weight kg,Waist cm,Chest cm,Arms cm,Notes,Arms Flexed cm,Height cm,Legs cm`;
  `Exercise Logs.csv`: `__airtable_record_id,__airtable_created_time,Log,Load,Load Unit,Reps,RIR,Notes,Session,Exercise`;
  `Exercises.csv`: `__airtable_record_id,__airtable_created_time,Exercise,Active,Workout,Order,Target Sets,Rep Min,Rep Max,Current Load,Load Unit,Progression Rule,Notes,Exercise Logs`;
  `Health State.csv`: `__airtable_record_id,__airtable_created_time,Name,Type,Value,Status,Effective From,Notes / Rationale`;
  `Sessions.csv`: `__airtable_record_id,__airtable_created_time,Session,Date,Workout,Readiness,Soreness / Stiffness,Session Notes,Exercise Logs,Training Habits Log`;
  `Training Habits Log.csv`: `__airtable_record_id,__airtable_created_time,Habit Event,Date,Session,Behavior,Direction,Impact,Evidence,Next-session rule`.
- Server tables: `supabase/migrations/20261101030000_training_model.sql` (`exercises`,
  `workout_sessions`, `session_exercises`, `session_sets` incl. `session_sets_complete`,
  `checkins`) and `20261101050000_context_entries.sql`. All have `unique (user_id, source_ref)`.
- CLI style and auth: `scripts/repknot` (env loading, `--as test2`, `fail()`).

## Normalizer changes (additive; existing columns and assertions unchanged)
1. `sessions.csv`: add `readiness` (select name) after `soreness`.
2. `checkins.csv`, `health_state.csv`, `training_habits.csv`: add a first column `ref`
   = the Airtable record id.
3. `training_habits.csv`: add `session_ref` (first linked session id, or empty) after `session`.

## Synthetic fixture
4. `scripts/fixtures/airtable-synthetic/source/` holds the six CSVs above with invented,
   clearly synthetic content (names like "Synthetic press"; no real values), in the exact
   Airtable cell formats the normalizer parses. Minimum coverage: 2 exercises plus 1
   bodyweight exercise; 2 sessions; 4 logs including a half rep, a timed set, a RIR range
   ("1-2"), a log the normalizer flags with a `review` reason, and a log with no unit;
   2 check-ins; 1 health-state row; 1 training-habit row linked to a session.
   Running `scripts/airtable-normalize <copy>` must succeed on a temp copy.

## Importer: `scripts/airtable-import [--as test2] <normalized-dir>`
Deno script like `scripts/repknot` (signed-in user, publishable key, RLS applies). In order;
every insert is an upsert with `onConflict: "user_id,source_ref"` and `ignoreDuplicates: true`,
batched (≤ 500 rows per call):
5. Exercises from `exercises.csv` exactly as `repknot seed-exercises` does (upsert by slug,
   updating name/unit/notes), then read `slug → id`.
6. Sessions: `performed_on = date`, `title`, `notes`, `source = 'import'`,
   `source_ref = session_ref`, `raw = {workout, soreness, readiness}` (only non-empty keys;
   `null` if none). Then read `source_ref → id` for the user's sessions.
7. Session exercises: one per `log_ref`, in the order logs first appear in `sets.csv`;
   `position` = 0.. within its session (continuing after existing rows if the session already
   has exercises), `exercise_id` by slug, `exercise_name` = that exercise's name,
   `source_ref = log_ref`. Then read `source_ref → id`.
8. Sets: `position = set - 1`, `load`, `unit`, `reps`, `duration_s`, `rir`, `notes`,
   `source_ref = "<log_ref>#<set>"`. Empty CSV cells → `null`. `raw` = object of the
   non-empty `rir_raw` and `review` fields, else `null`. A bodyweight row with a load moves
   it into `raw.load` (load becomes `null`). If a row would still violate
   `session_sets_complete` (no unit, or no reps and no duration, or missing load for
   kg/lb/plates) and `raw` is null, set `raw = {"review": "incomplete source row"}`.
9. Check-ins: `checked_on = date`, the numeric columns, `notes`, `source = 'import'`,
   `source_ref = ref`.
10. Context: `health_state` → `kind 'health_state'`, `recorded_on = effective_from`,
    `title = name`, `details = {type, status, value, rationale}`; `training_habits` →
    `kind 'training_habit'`, `recorded_on = date`, `title = event`, `details = {session,
    session_ref, behavior, direction, impact, evidence, next_session_rule}`. Both
    `source = 'import'`, `source_ref = ref`; empty detail fields omitted.
11. Print one line per table: `<table>: N in file, X new, Y already imported`
    (count existing refs before inserting). Exit 1 on any error with the message.

## Plan / progress
1. [ ] Normalizer changes (1–3); fixture (4); normalizer runs on a temp copy.
2. [ ] Importer (5–11).
3. [ ] Verification (below); commit(s) with task 013 in the subject.

## Verification (worker: run, record actual output)
1. `d=$(mktemp -d) && cp -r scripts/fixtures/airtable-synthetic $d/ && scripts/airtable-normalize $d/airtable-synthetic`
   → succeeds; show the normalized headers of the five changed/used files.
2. `scripts/airtable-import --as test2 $d/airtable-synthetic/normalized` → all `X new`.
3. Same command again → every table `0 new`.
4. `scripts/repknot --as test2 list 5` and `show` one imported session → nested exercises
   and sets with the expected positions; the flagged and unit-less sets carry `raw`.
5. `deno check` on `.ts` copies of `scripts/airtable-import`.
Synthetic test2 rows stay on dev.

## Decisions and discoveries
- Lead (2026-11-01): importer writes through RLS as the user (no service key); bodyweight
  loads go to `raw` rather than guessing "added weight" semantics.

## Handoff
(worker: what was done, outputs, anything unresolved or questioned)
