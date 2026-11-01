# 008 — CLI: seed exercises and publish a program

Status: ready
Updated: 2026-11-01
Starting commit: (worker: record `git rev-parse --short HEAD` when you start)
Parent: [006](006-training-data-model.md) step 5 · Delegated: yes (see
[delegation](../delegation/README.md))

## Outcome and acceptance
Two new `scripts/repknot` commands that load exercises and publish a program version on
the dev backend as the signed-in user, per [ADR 002](../decisions/002-training-data-model.md).
Accepted when:
- Both commands behave exactly as specified below.
- Verified on dev **as the second test user only** (`--as test2`) with the synthetic
  fixtures described under Verification; outputs recorded in this file.
- `deno check` passes on a `.ts` copy of the script (see Verification).

## Scope
In: `scripts/repknot` only (plus this task file).
Out of scope: migrations, SQL functions, tests under `supabase/`, Android, any other script,
new files in the repo. Do **not** run either command without `--as test2`, and do not read,
copy, or pass anything from `private-data/` — the lead runs the real data afterwards.

## Context
- Schema: `supabase/migrations/20261101030000_training_model.sql` — table `exercises`
  (`slug`, `name`, `default_unit`, `notes`; unique `(user_id, slug)`) and function
  `publish_program(payload jsonb, activate boolean default true)`; read its validation and
  return value there. Clients cannot write program tables directly; only the function can.
- Input formats: exercises CSV header is
  `slug,name,airtable_name,airtable_ref,default_unit,active_in_airtable,airtable_role,airtable_notes`
  (fields may contain quoted commas). Program JSON is "format 1": see the `program_v1`
  literal in `supabase/tests/005_training_model_test.sql` for the shape.
- Follow the existing style of `scripts/repknot` (switch on command, `fail()`, usage line,
  header comment listing commands).

## Behavior (implement exactly)
`scripts/repknot [--as test2] seed-exercises <exercises.csv>`
1. Parse with `parse` from `jsr:@std/csv` (header row → objects). Fail with a clear message
   if any of `slug`, `name`, `default_unit` columns is missing.
2. Map each row to `{ slug, name, default_unit, notes }`: `default_unit` and `notes`
   (from `airtable_notes`) become `null` when empty. Other columns are ignored.
3. One upsert call: `.upsert(rows, { onConflict: "user_id,slug" })` — re-running updates
   names/units/notes and never duplicates. Do not touch `archived_at`.
4. Print `seeded N exercises (X new, Y updated)`, computing new vs updated by reading the
   user's existing slugs before the upsert.

`scripts/repknot [--as test2] publish-program <program.json> [--inactive]`
5. Read and `JSON.parse` the file; call `rpc("publish_program", { payload, activate })` with
   `activate = false` only when `--inactive` is given.
6. On success print `published <key> v<version> (<id>)`, then ` · active` or ` · inactive`,
   then ` · supersedes <uuid>` when present — one line.
7. Error handling: Postgres code `23505` → print `<key> v<version> is already published;
   bump "version" to publish a change` and exit 1. Any other error → existing `fail()`
   (prints the error, exit 1). The SQL message for unknown exercises/sequence must reach
   the user unchanged.
8. Update the header comment and the usage line for both commands.

## Plan / progress
1. [ ] Implement both commands.
2. [ ] Verify with synthetic fixtures as test2 (below); record outputs.
3. [ ] Commit (task 008 in subject).

## Verification (worker: run these, record actual output)
Create fixtures in a fresh `mktemp -d` directory outside the repo:
- `ex.csv`: header as above; rows `syn-press,"Press, synthetic",,,kg,true,,"cue, with comma"`,
  `syn-dip,Dip synthetic,,,bodyweight,true,,`, `syn-pin,Pin synthetic,,,plates,false,,`.
- `prog.json`: format 1, `"program": "syn"`, `"version": 1`, sequence `["a","b"]`, workout
  `a` with `syn-press` (kg, load 20) and `syn-pin` (plates, load 5), workout `b` with
  `syn-dip` (bodyweight, `reps: null`, `load: null`), plus `rules`, `default_days`.
Then, in order (all with `--as test2`):
1. `seed-exercises ex.csv` → `3 new, 0 updated`; again → `0 new, 3 updated`.
2. `publish-program prog.json` → active, no supersedes.
3. Same command again → the "already published" message, exit code 1.
4. `prog.json` with version 2 + `--inactive` → inactive, supersedes the v1 id.
5. A copy referencing slug `syn-missing` → error naming `syn-missing`, exit 1.
6. `deno check`: `d=$(mktemp -d) && cp scripts/repknot $d/repknot.ts && deno check $d/repknot.ts`.
Test-user data stays on dev (synthetic; there is no delete for programs).

## Decisions and discoveries
- Lead (2026-11-01): `active_in_airtable=false` exercises are seeded unarchived — history
  import (step 9) references them. Airtable role text is not stored.

## Handoff
(worker: what was done, outputs, anything unresolved or questioned)
