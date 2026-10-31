# 002 — Training data model: exercises, versioned programs, session snapshots

Status: accepted  
Date: 2026-11-01

## Context

The first logging slice stores flat synthetic sets (`exercise_name`, kg/lb, integer reps).
Real use needs (PRD §V0 1–9): today's workout from a program rotation, prescriptions with
rep ranges, previous-performance prefill, substitutions and per-exercise notes, and an
Airtable import that keeps ambiguous values. The real history (an Airtable
export) and the PPL program starting 2026-11-02 show:

- Loads come in **kg**, **machine stack pins** ("9p" — not convertible to kg), and
  **bodyweight** (no load). A few sets are **timed** (carries) or have **half reps** (5.5
  pull-ups). Some old values are unknowable ("2 carries", load not recorded).
- RIR is reported as ranges ("1-2", "1-0") or not at all.
- The program is a fixed **sequence** of six workouts with default weekdays; a missed day
  shifts the week ("never double up"), so "today" is the next workout, not the weekday's.
- The same exercise appears in several workouts with different positions and set counts.

## Decision

### Exercises
- Per-user `exercises`: `slug` (unique per user, stable import key), `name`, `default_unit`,
  `notes`, `archived_at`. A variation that should not share history is a separate exercise
  (e.g. low-incline DB press vs DB chest press).
- Previous performance and progress compare the **same exercise and unit** only.

### Loads and set measures
- `unit ∈ kg | lb | plates | bodyweight`. `plates` = machine stack pin number, shown as
  `9p`, never converted. `load` is null only for `bodyweight` (added weight later = kg + note).
- `reps numeric(4,1)` in 0.5 steps (partial reps), `duration_s` for timed sets, `rir`
  integer (a reported range stores its lower bound; the raw text stays in `raw`).
- A set needs reps or a duration, except imported sets that keep their `raw` source value.

### Programs are immutable versions
- `programs`: one row per **version** (`name`, `version`, `starts_on`, `rules` text,
  `supersedes`, `is_active` — at most one active per user).
- `program_workouts`: `key` (`push-1`), `name`, `sequence_position`, optional `default_weekday`.
- `program_exercises`: workout, position, exercise, `sets`, `rep_min/rep_max` (nullable —
  "stop at ~2 RIR"), `rir_min/rir_max`, `rest_s_min/rest_s_max`, start `load` + `unit`, `notes`.
- Clients can only read program tables. Programs are created through one function
  (`publish_program`) that inserts a complete new version and optionally activates it.
  Changing a program means publishing a new version; existing versions are never edited,
  so sessions always point at the prescription they were done against.

### Sessions snapshot the prescription
- `workout_sessions` gains nullable `program_id` + `program_workout_key` (null for ad-hoc
  and imported sessions).
- New `session_exercises` (session, `position`, `exercise_id`, `exercise_name` snapshot,
  planned `sets/rep_min/rep_max/rir_min/rir_max/load/unit` snapshot, `substituted_for`,
  `notes`). Starting a session copies today's prescription; later program versions do not
  change it. Adding, removing, reordering or substituting edits only the session.
- `session_sets` belong to a `session_exercise` (replaces `exercise_name` on the set).
- ADR 001 is unchanged: the session (with its exercises and sets) is still the sync unit,
  and `push_session` / `save_session` exchange whole nested snapshots under one revision.

### Today's workout and previous values (computed on the phone)
- Today = the workout after the latest session of the active program in `sequence`
  (first workout if none). Weekdays are informational; any workout can be chosen manually.
- Prefill per exercise = sets from the most recent finished session containing that
  exercise with the same unit, labelled as previous values; otherwise the program's start
  load. Never stored as completed sets.

### Import
- Imported rows use `source = 'import'` and carry `source_ref` (Airtable record ID),
  unique per user where present, so re-running an import is idempotent and never creates
  duplicates. Ambiguous originals are kept in `raw jsonb` with the review reason.
- Import input is the normalized export from `scripts/airtable-normalize` (kept in
  git-ignored `private-data/`). Real data is imported to prod only, after dev verification.
- Check-ins get their own table (`checkins`: date, weight, waist, other girths, notes).
  Health-state and training-habit entries are stored as dated text context records.

## Alternatives considered

- **Flat sets with `exercise_id`** (no `session_exercises`): smaller, but nowhere to keep
  the planned prescription, substitutions, or per-exercise notes.
- **Prescription snapshot as one `jsonb` on the session**: simpler writes, but harder to
  query planned-vs-actual and to validate.
- **Editable programs with history tables**: more flexible, but needs audit logic;
  immutable versions get the same guarantee with plain inserts.
- **Weekday-based "today"**: contradicts the "missed day shifts the week" rule.
- **Converting plates to kg**: stack weights are machine-specific and unknown.

## Consequences

- The session schema, sync payloads, Room tables, and share text change shape. During the
  building phase dev data is synthetic and Room may reset (see architecture notes).
- ChatGPT payloads become nested (exercises → sets) and reference exercises by slug.
- Program changes are explicit, reviewable new versions; nothing edits history silently.
- Units without conversion mean cross-machine comparisons are not attempted.
