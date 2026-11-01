# 019 — Progress and regression (milestone)

Status: in progress
Updated: 2026-11-01
Starting commit: dbd6b62
Lead task; steps are delegated as separate task files.

## Outcome and acceptance
Owner request (2026-11-01): "not just views — meaningful data that is actually practical."
Every number must change a decision in the gym or in weekly planning. Plan agreed between
the lead and a second opinion (pi `gpt-6-astra`), owner's answers recorded below. Accepted
when steps 1–5 are done with their own acceptance, `scripts/check` passes, and the lead has
checked the screens on the phone against the real history.

## Owner context (2026-11-01)
- Currently cutting toward a waist goal. Holding strength is success.
- Follows **double progression**: all prescribed sets at the top of the rep range at the
  target RIR → next available load.
- "No recent improvement" after 3 comparable exposures or 14 days is fine.
- Dumbbell loads are **per dumbbell**, always.
- Low-incline press history mixes early flat-bench sets; owner: incline is 20–30°, nearly
  flat — treat as one lineage, no data fix.

## Plan / progress
1. [x] Status engine in `domain` — [020](020-progress-engine.md) (delegated).
2. [x] Workout card: today's target, ▲/=/▼ per set vs the matching set last time, planned vs
   done sets (delegated after 1).
3. [x] Progress screen: exercises sorted by attention, status + reason + flags, broad-dip
   sessions grouped; tap → exercise history (delegated after 1).
4. [x] Light / modified day flag on a session, excluded from statuses. Needs a non-destructive
   server migration, `push_session` support, and the **first real Room migration** (v4→v5:
   from 2026-11-02 the phone holds unuploaded real workouts, so no destructive fallback).
   Lead does migration + sync; UI toggle may be delegated. Server side done (742ed5c:
   `light` column, push/save keep it when omitted; pgTAP 007). Phone side 1a7b656: Room v5
   with an explicit migration (MigrationTest), sync payload, "Light day" switch. The installed
   app upgraded v4→v5 on the owner's phone without data loss or crash (2026-11-01).
5. [ ] Body card: check-ins to the phone (sync + entry form), waist now / cm to goal /
   14-day rate and projected date, weight via 14-day medians (≥3 check-ins per window);
   prompt to review the cut when weight loss > ~1 %/week *and* broad strength declines.

## Decisions
- Status per exercise: improving / maintaining / possible decline / not enough data, plus
  independent flags (ready to move up but not moved — covers equipment limits like the
  pec deck at max stack; not trained recently). Maintaining is not a warning.
- Comparisons only within one exercise and unit, at the same top load, matched set by set
  on reps + RIR. A load increase counts as improvement under double progression; no
  decline verdict across different loads. No e1RM for plates (pin counts are not
  proportional resistance).
- Equipment limits are inferred from behavior (ready to move up repeatedly without moving)
  instead of an equipment-ladder setup; next load = next higher load seen in that
  exercise's history, else "pick the next load".
- Session-wide dips are grouped with "check sleep, food, recovery" wording, never a cause;
  individual statuses stay visible.
- Computation is pure Kotlin in `domain` on the phone's local history (offline, includes
  unuploaded sessions). ChatGPT later reads raw data.
- Deferred: weekly hard sets per muscle (needs muscle tags), adherence beyond planned vs
  done, e1RM as a secondary kg-only number.

## Discoveries
- The phone drops `raw`/`source_ref` when caching sessions (`SessionMapping`). Harmless while
  past sessions cannot be edited on the phone; guard it before adding past-session editing
  (pushing an imported incomplete set without `raw` would fail `session_sets_complete`).

## Handoff
Steps 1–4 done and device-checked (2026-11-01). Next: step 5 (body card), which first needs
check-ins on the phone (sync + entry form) — write its task file.
