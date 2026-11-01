# 021 — Workout card: today's target, per-set ▲/=/▼, planned vs done

Status: in progress
Updated: 2026-11-01
Starting commit: 499d8c6
Parent: [019](019-progress-milestone.md) step 2 · Delegated: yes (see
[delegation](../delegation/README.md))

## Outcome and acceptance
The workout card tells the owner what to aim for today under double progression and
whether each logged set beat the matching set last time. Uses the engine from
[020](020-progress-engine.md) (`todayTarget`, `setDelta`); no new rules here. Accepted when
the items below behave as specified, JVM tests cover the pure functions, and
`scripts/check --offline` passes. The lead runs the device check.

## Scope
In: `android/app/src/main/java/com/repknot/app/WorkoutViewModel.kt` (only `ExerciseUi`,
`PreviewExerciseUi`, `sessionState`, `previewState`), `ui/WorkoutScreen.kt`, new
`TargetText.kt` + `TargetTextTest.kt`, `ExerciseSelection.kt` + `ExerciseSelectionTest.kt`
(rename only), `WorkoutStateTest.kt`, `strings.xml`, this file.
Out of scope (do not modify): `domain`, `data`, sync, other ViewModel behavior, new
libraries. Do not read `private-data/`.

## Behavior (implement exactly)
0. **Rename** the app's `internal data class ExerciseProgress` (ExerciseSelection.kt) to
   `ExerciseCompletion` and update its uses — the domain now has a public
   `ExerciseProgress` and both are visible via `com.repknot.domain.*`.
1. **State.** Add `target: TodayTarget?` to `ExerciseUi` and `PreviewExerciseUi`, and
   `deltas: List<SetDelta>` (one per logged set, same order) to `ExerciseUi`.
   - Session: `target = if (row.substitutedFor != null) null else
     todayTarget(prescriptionFor(inputRow, unit), previousHistory)` (a substitute does not
     inherit the original exercise's progression).
   - Deltas: for logged set `i`, `setDelta(PastSet(set.position, set.load, unit(set),
     set.reps, set.durationS, set.rir), i, row.exerciseId, previousHistory)`; `NONE` when the
     set's unit is null. `previousHistory` is the existing list without the current session.
   - Preview: `target = todayTarget(prescription, history)`.
2. **`targetText(target: TodayTarget): String`** in `TargetText.kt` (English, same number
   formatting as `PreviousSummary.kt`: drop `.0`; plates `12p`; kg/lb `25 kg`):
   - `MoveUp` with `to != null`: `Target · 27.5 kg × 8+ — all sets hit the top last time`
     (`× <repMin>+` omitted when `repMin` is null).
   - `MoveUp`, `to == null`, `from != null`: `Target · move up from 25 kg — pick the next load`.
   - `MoveUp`, bodyweight (`from == null`): `Target · top of the range — add load or a harder variation`.
   - `Repeat`: `Target · 25 kg at RIR 1–2, add a rep where it's there (last 10, 9, 10)`;
     bodyweight `Target · bodyweight at RIR 1–2, …`; RIR part `at RIR 1` when min == max,
     `at RIR 1+` when only min, omitted when both null; `(last …)` omitted when `lastReps`
     is empty. Reps keep `.5` (`8.5`).
   - Added 2026-11-01 (worker question): only `rirMax` → `at RIR ≤2`. A null load with a
     non-bodyweight unit: `Repeat` omits the load (`Target · repeat at RIR 1–2, add a rep
     where it's there (last …)`); `MoveUp` with `from == null` and `to == null` reads
     `Target · top of the range — pick the next load`; `to != null` behaves as above.
   - Any other combination not listed: the most conservative readable text; record it.
3. **Card.** Under the `Last · …` lines: `targetText(target)` in `MaterialTheme.colorScheme.primary`
   (bodyMedium), when `target != null`. Same in preview cards and in the "Up next" block.
4. **Set rows.** After the existing description, a separate `Text`: `▲` (primary) for UP,
   `=` (onSurfaceVariant) for SAME, `▼` (error) for DOWN, nothing for NONE; with a
   `contentDescription`-equivalent via `Modifier.semantics { contentDescription = … }`:
   "better than last time" / "same as last time" / "below last time" (strings.xml).
5. **Planned vs done.** In the card header row, between the name and `⋯`: `2/3`
   (labelLarge; onSurfaceVariant, primary once done ≥ planned) when `plannedSets` is
   non-null and > 0.

## Tests
`TargetTextTest`: every branch in item 2 (kg, plates, bodyweight, half reps, RIR forms,
empty lastReps, repMin null). `WorkoutStateTest`: target present for a prescribed row with
history, null for a substituted row; deltas length equals logged sets and UP for a heavier
logged set; preview target present. `ExerciseSelectionTest` compiles after the rename.

## Plan / progress
1. [x] Rename (0), state (1), `TargetText.kt` (2) + tests.
2. [ ] UI items 3–5.
3. [ ] `scripts/check --offline`; commit(s) with task 021 in the subject.

## Decisions and discoveries

- `TodayTarget.Repeat` permits `rirMin == null` with a non-null `rirMax`; session
  prescription snapshots also permit these independently. The specified RIR wording
  does not cover this case.
- `todayTarget` can produce a null top load for KG, LB, or PLATES when historical
  sets have null loads. Both `Repeat(load = null)` and `MoveUp(from = null)` can
  therefore represent an unknown load rather than bodyweight. The specified wording
  only describes null loads as bodyweight.
- Pre-existing untracked `work/` is left untouched.
- Catch-all: if a bodyweight target contains a numeric load, show bodyweight rather than inventing a load unit. Review this conservative formatting choice.

## Verification

- Inspected task, state mapping, UI, domain target engine and data models.
- `scripts/check --offline`: first run failed on cross-module Kotlin smart casts; corrected. Second run passed (build, lint, unit tests).

## Handoff

Clarifications are in item 2. Next: implement UI plan step 2 and rerun checks; lead device check remains pending.
