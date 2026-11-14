# ADR 006 — Program versions keep the rotation; the coach publishes them

Status: accepted · 2026-11-14 · task [038](../tasks/038-coach-publishes-program.md)

## Context

Programs are immutable versions (ADR 002): `publish_program` inserts a new `programs` row with
a new id and makes it active. The phone picked the next workout only from sessions whose
`program_id` was the active version's id, so every republish restarted the rotation. The owner
wants the coach workspace (`../health`) to put its suggestions straight into the app, which
makes program republishes routine rather than rare.

## Decision

- The rotation is per program **key**, not per version: `todaysWorkout` counts finished
  sessions from every cached version with the active program's key (`Program.versionIds`),
  matched by workout key. Workout keys must stay stable across versions.
- The coach workspace publishes both coach briefs (ADR 005) and new program versions
  without a per-change approval, through wrappers around `scripts/repknot` signed in as the
  owner (RLS applies; no new privileges). It reports each change to the owner afterwards.
  Goal/phase changes, new programs, added or removed workouts, and lighter weeks still need
  the owner's OK. The rules live in `../health/AGENTS.md`.
- The publishable program JSON lives in the coach workspace (`../health/programs/`), next to
  the program document, instead of this repo's `private-data/programs/`.

## Alternatives

- Keep owner approval for every program change: safer against a bad coach edit, but the owner
  explicitly chose direct publishing; a bad version is fixed by publishing the next one, and
  logged sessions are never touched (session prescriptions are snapshots).
- Mutable programs (update the active version in place): no rotation problem, but loses the
  version history ADR 002 relies on and needs new write paths and sync handling.
- Show the last-used load in the Plan row instead of republishing load changes: would remove
  most load-only republishes; a possible later app change, not decided here.

## Consequences

- A republish keeps today's suggestion. A temporary alternate workout selection on Home still
  resets, because the program identity changed.
- Renaming a workout key in a new version breaks the rotation link for that workout.
- Phones running a build from before task 038 still restart the rotation after a republish.
