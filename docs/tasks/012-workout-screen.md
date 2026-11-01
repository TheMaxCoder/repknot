# 012 — Workout screen (Compose)

Status: ready (starts after 011 is accepted)
Updated: 2026-11-01
Starting commit: (worker: record `git rev-parse --short HEAD` when you start)
Parent: [006](006-training-data-model.md) step 8 (UI half; logic is task 011) · Delegated:
yes (see [delegation](../delegation/README.md))

## Outcome and acceptance
The app's main screen renders `WorkoutViewModel` (task 011) so a workout can be started,
logged, edited, finished, shared and synced on the phone (PRD §3 items 1–6). Accepted when:
- The screen behaves as specified below, using only `WorkoutViewModel` state and actions.
- `scripts/check --offline` passes; no new lint errors.
- The lead's device smoke test (after you finish) passes; you do not need a device.

## Scope
In: `android/app/src/main/java/com/repknot/app/MainActivity.kt` (replace the interim
`HomeScreen`), new UI files under `android/app/src/main/java/com/repknot/app/ui/` if you
want to split, `android/app/src/main/res/values/strings.xml`, this file.
Out of scope: `WorkoutViewModel.kt` and anything in `data`/`domain` (if the ViewModel lacks
something you need, stop and ask), new libraries (no icon library: use text buttons),
theme changes (dark-only `RepknotTheme` stays as is).

## Context to read
`WorkoutViewModel.kt` (state, actions, `formatSet`), `MainActivity.kt` (current
`CloudSection`, edge-to-edge setup, Factory usage), `CloudViewModel.kt`,
`strings.xml`, `docs/architecture.md` rules (previous values are suggestions only).

## Screen (implement exactly; all text in `strings.xml`)
Every state ends with the existing `CloudSection` below a divider. Errors from
`WorkoutViewModel.error` show in the error color near the top.
1. `Loading`: a progress indicator.
2. `NoProgram`: text "Sign in and sync to load your program."
3. `Today`: program name (small, primary color); "Next workout" label; the suggested
   workout name (headline); a full-width **Start <name>** button → `start(key)`. Below, an
   "Other workouts" section: one outlined button per workout in `workouts` except the
   suggested one → `start(key)`.
4. `Session`: header = session title (headline) and date. Then one card per exercise in
   order, then **Add exercise**, session notes, and the finish/sync section (item 9).
5. Exercise card:
   - Name (title). If `substitutedFor != null`: "instead of <name>" where the name comes
     from `choices` (fallback: "prescribed exercise").
   - Prescription line from planned values, parts only when present:
     "3 sets · 8–12 reps · RIR 1–2 · 25 kg" (plates as `12p`, bodyweight as "bodyweight").
   - Previous line: `Prefill.Previous` → "Previous: " + its sets joined with ", " using
     `formatSet`-style text (`25 kg × 10`); `StartLoad` with a load → "Start load: 25 kg";
     otherwise nothing. Render in `onSurfaceVariant` so it reads as a hint, not a log.
   - Logged sets: "1. 25 kg × 10 · RIR 2" per set (same text as share), each with **Edit**
     and **Delete** text buttons. Edit loads that set's values into the input row and
     switches the button to **Update set** (with **Cancel**).
   - Input row: Load field (decimal keyboard; hidden when unit is bodyweight), unit chips
     kg/lb/plates/bodyweight, Reps field (decimal keyboard), RIR field (number keyboard,
     optional). When not editing, fields start from `suggestion` (load/reps as plain text,
     whole numbers without ".0"; unit from suggestion) and a small hint "Suggested from
     previous" shows when `previous` is `Previous`. Fields reset to the new suggestion after
     a successful log. **Log set** → `logSet(row.id, null or editing set id, …)`.
   - Actions row: **↑** / **↓** (`moveExercise`, disabled at the ends), **Substitute**
     (opens the picker → `substitute`), **Remove** (confirm dialog → `removeExercise`).
   - Exercise notes field + **Save notes** → `saveExerciseNotes`.
6. Picker (dialog): a search field filtering `choices` by name (case-insensitive) and a
   scrollable list; tapping an item confirms. Used by Add exercise and Substitute.
7. Field state uses `rememberSaveable` keyed by the row/set ids so rotation keeps input.
8. Session notes field + **Save notes** → `saveSessionNotes`.
9. Finish/sync section by `row.status`:
   - `in_progress`: **Finish workout** → `finish()`.
   - `finished`: "Upload pending" + **Retry upload** → `finish()`; **Done** → `done()`.
   - `pushed`: "Uploaded" + **Done** → `done()`.
   - `conflict` / `conflict_deleted`: the existing conflict texts + **Keep mine** /
     **Take theirs**.
   - Always (except `in_progress`): **Share** (ACTION_SEND chooser) and **Copy**
     (clipboard) using `share { … }`, as the old `FinishSection` did (see Git history:
     `git show a6e4f79^:android/app/src/main/java/com/repknot/app/MainActivity.kt`).
10. Remove strings that become unused (`logging_rebuilding`, etc.); reuse existing ones
    where the meaning matches.

## Plan / progress
1. [ ] Today / NoProgram / Loading + wiring in `MainActivity`.
2. [ ] Session screen: exercise cards, set input, picker, notes, finish/sync section.
3. [ ] `scripts/check --offline`; commit(s) with task 012 in the subject.

## Verification
(worker: exact commands and results)

## Handoff
(worker: what was done, anything unresolved or questioned)
