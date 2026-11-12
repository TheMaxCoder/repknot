# ADR 005 — Coach notes

Status: accepted · 2026-11-12

## Context

The owner reviews recent training with an AI coach (this repo's lead session or the
separate coach workspace). The useful outcome of a review is a handful of reminders for
the next sessions ("go up a plate on X", "keep set 2 at RIR 2"), which today stay in a
chat. The owner wants them on the phone's Home, at a glance, without retyping them.

## Decision

- A new server table `coach_notes` holds **briefs**: a headline, an optional `based_on`
  date (latest workout considered), and 1–8 short items. Each item has a `kind`
  (`progress`, `hold`, `technique`, `recovery`, `focus`), a text (≤ 200 chars), and an
  optional `exercise_id`. Shape and limits are enforced by check constraints.
- Briefs are **append-only** (owner may select, insert, delete; no update). The newest
  brief by `published_at` is the current one; older ones remain as history for the coach.
- Writers are coach tools, through `scripts/repknot publish-notes <file>` signed in as the
  owner (RLS applies; no privileged key). The CLI validates the file and resolves exercise
  slugs to ids. The coach workspace reads its own past briefs from `coach-export`.
- The phone is **read-only**: each refresh caches only the newest brief in Room (replaced
  wholesale, like programs) and Home shows it in a collapsible card. Notes for the selected
  workout's exercises are shown first. Whether a brief was opened is device-local state.

## Alternatives

- Free-form markdown note: easiest to write, but no per-exercise relevance or consistent
  presentation; the phone would render arbitrary text.
- One row per item: lets items be edited or dismissed individually, but needs grouping by
  brief anyway and invites partial edits; a review is naturally replaced as a whole.
- Phone-side dismiss/complete per item, synced back: useful later, but it adds a second
  writer and sync state to what is now a read-only cache.

## Consequences

- No sync conflicts: only the CLI writes, and the phone never writes coach notes.
- New kinds need a migration (check constraint) and phone styling; the phone treats any
  unrecognized kind like `focus`, so an older app keeps working.
- Notes are only as current as the last review; the card shows the publish date.
