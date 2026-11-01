# 017 — Workout redesign

Status: blocked
Starting commit: 024ddf2
Updated: 2026-11-01

## Outcome / acceptance
Implement the owner-approved combined mockup: compact current exercise card, small date
in header, no app title, Up next and View all exercises, separated bottom actions.
Dark wallpaper-derived dynamic colors on Android 12+; existing dark fallback on older
versions. Preserve every existing workout operation and saved data. RIR defaults to 1
with quick minus/plus; editing restores recorded value, including values above 10.
No load-unit choice while logging: show kg/lb/plates label, hide load for bodyweight.

## Scope
App Compose UI/theme/resources, pure UI selection helpers and app tests, this task.
Lead updates AGENTS.md/architecture/status to reflect approved dynamic colors.
Out of scope: data/domain/schema/backend/auth/sync changes, new dependencies, unit-setting
editor, private-data, .env, unrelated work/ mockup. Do not read credentials or exports.
Read architecture, ADR 002, existing UI and tests. Reference approved visual:
workout-refined.html (local mockup, not in the repo)
HTML is only a visual reference; implement native Compose and real behavior.

## Plan
1. Implement theme and session UI; run scripts/check --offline; commit coherent checked steps.
2. Lead reviews diff, reruns scripts/check --offline, installs and checks device.
3. Record verification/limitations and handoff.

## Detailed behavior / decisions
- Current exercise: initially first exercise below prescribed set count (unplanned/zero
  target counts as unfinished); fall back to last if all complete. Save selected exercise
  id per session so logging doesn't auto-hide its inputs. View all toggles a compact list
  allowing selection of any exercise (including completed). Removing selected exercise
  falls back safely; empty sessions still allow adding. Up next means next exercise after
  selected in session order; none at end. Selection/reordering must not discard unsaved
  field state: use SaveableStateHolder keyed by exercise id or equivalent.
- Header workout title and small formatted performed date; Settings remains accessible.
- Compact selected card: prescription, existing Last summary, saved sets with Edit/Delete,
  labeled weight and reps side by side, RIR stepper on its own row (44+ dp controls),
  full-width Log/Update. Existing notes/menu/substitution/removal/reordering stay reachable.
- Unit resolution retains existing prescription snapshot precedence, then exercise catalog
  default (already computed by sessionState). Existing set edits keep their recorded unit.
  Remove unit FilterChips. No automatic unit conversion or relabeling of old values.
  Added/substituted exercises must still use correct resolved unit. Keep previous prefill.
- Separated tonal bottom action area (Scaffold bottomBar, safe system/IME insets), Add exercise
  and Session notes grouped above full-width Finish; retain discard confirmation as a quiet
  action, plus all finished/pushed/conflict/share/copy/retry behavior. Avoid enormous footer
  when notes/keyboard open: session notes can be dialog with explicit save/cancel.
- User explicitly overrides AGENTS prohibition on dynamic colors. Use dynamicDarkColorScheme
  on API >=31, existing palette otherwise. No light theme, no toggle. No new icon library.
- No exercise configuration editor in this step: use existing configured units. This is
  a scoped interpretation; retain snapshot semantics per ADR 002.

## Verification responsibilities
Worker: scripts/check --offline; meaningful JVM tests for selection fallback/order and unit
behavior as needed. Preserve existing tests. No fake Compose tests mirroring layout.
Lead: review complete diff and rerun check; installDebug and device UI checks (header, unit
picker absent, RIR +/-, list selection, bottom separation, keyboard). Do not log/delete/
finish/discard actual session during smoke test. Device tests pending until recorded.

## Progress / discoveries
- Starting worktree contains only untracked work/ from earlier visual exploration; preserve.
- Connected wireless device available. Existing code uses snapshot unit then catalog default.
- No implementation started. `SetRow.unit` is nullable, including for imported sets;
  `SetInput` falls back to the suggested unit when editing a null-unit set, and
  `WorkoutViewModel.logSet` requires a concrete `LoadUnit` and writes it to the row.
  Removing the unit picker without a decision risks relabeling an unknown recorded unit.

## Verification
- `scripts/check --offline` passed on the unchanged implementation (106 tasks: 2 executed,
  104 up-to-date). This is a baseline, not verification of the redesign.
- Redesign JVM tests, lead review, device installation and UI acceptance remain pending.

## Handoff
Blocked on owner/lead answer: when editing an existing set whose recorded unit is null,
should the app preserve the null unit (and how should its load be labeled and validated),
prevent editing that set, or intentionally assign the resolved exercise unit? The last
option would relabel previously unknown data. This decision affects saved data and the
requirement to preserve recorded units; do not guess. After clarification, implement plan
step 1 and rerun the checks. No implementation changes are uncommitted; untracked `work/`
is unrelated and must remain untouched. No destructive actions or backend work.
