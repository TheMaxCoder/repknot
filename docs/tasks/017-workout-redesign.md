# 017 — Workout redesign

Status: in progress
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
- Implemented dynamic dark theme, selected exercise card and saved selection/field state,
  Up next/list, separated actions, RIR stepper, configured-unit input, and notes dialog.
- Following lead clarification, existing set edits retain their recorded unit, including
  null; null edits validate using the resolved fallback without recording that fallback.
  New sets still use the resolved unit. Review: this changes how existing row copies are
  constructed and must not relabel recorded values.

## Verification
- `scripts/check --offline` passed on the unchanged implementation (106 tasks: 2 executed,
  104 up-to-date). This is a baseline, not verification of the redesign.
- `scripts/check --offline` passed after implementation (106 tasks: 25 executed,
  81 up-to-date): debug build, lint, and JVM tests, including selection and unit tests.
- Lead review, independent check, device installation and UI acceptance remain pending.

## Handoff
Next: lead review the implementation and rerun `scripts/check --offline`, then install
and perform the non-destructive device checks listed above. Verify keyboard/insets and
selection state on device, and inspect preservation of existing recorded units. Device
verification is pending; task remains in progress. Lead's unstaged `AGENTS.md`,
`docs/architecture.md`, `docs/status.md`, and untracked `work/` remain untouched.
No destructive actions or backend work.

## Lead clarification
For legacy null-unit set edits, preserve the null unit in the saved row (never infer a
recorded unit). Label load simply Load for these edits. Keep existing parse/validation
using the resolved fallback; only preserve existing.unit when copying an existing row in
WorkoutViewModel.logSet. Concrete saved units stay unchanged. New sets use configured
units. This narrow ViewModel edit is in scope; test the pure unit-preservation helper.
No schema or domain changes. Proceed with the UI implementation.
