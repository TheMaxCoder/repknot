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
4. [ ] Migration: exercises, programs/workouts/exercises, `publish_program`, session_exercises,
   set unit/measure changes, `source_ref`/`raw`, checkins; update `session_snapshot`,
   `replace_session_sets`, `push_session`, `save_session` for nested payloads. Tests.
5. [ ] `scripts/repknot` (or sibling) commands: `seed-exercises`, `publish-program <json>`.
6. [ ] Domain: today's-workout selection and prefill as pure Kotlin with unit tests.
7. [ ] Data/Room: cache programs/exercises; session_exercises; nested push/pull.
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

## Verification
- `scripts/airtable-normalize private-data/airtable-health-2026-10-31`: every source log
  produces ≥1 set (asserted); raw JSON matched CSV with 0 differences before deletion.
- `git check-ignore` confirms `private-data/` is ignored.

## Handoff
Next action: step 4 (migration + pgTAP on dev). The app cannot log real workouts by
2026-11-02; log the first PPL week as before (Airtable/ChatGPT) — a later export can be
normalized with the same script and imported.
