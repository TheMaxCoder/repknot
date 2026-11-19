# 042 — Workout editor (customize before and during a workout)

Status: done (owner phone check pending) · starting commit `8e87639`

Owner request (2026-11-19): customizing a workout (add, remove, move) took a lot of back and
forth. Design agreed with the owner after a UX review by a second model (Sol) and the lead:
a full-list editor; "Replace <current>" stays in the ⋯ menu; editing also works from the
preview before Start. Background: task 041, task 039 (one ⋯ per screen).

## Outcome / acceptance
1. **Edit workout screen**: a full-screen list of the workout's exercises in order. Each row shows
   its number, name, "instead of <original>" when replaced, and either "<k> sets logged" or the
   plan "3 × 8–12" when none. Tapping a row expands its actions (one row expanded at a time):
   **Move to…**, **Add after**, **Replace**, **Remove**. A bottom bar has **Add exercise** (appends)
   and **Done**. System Back closes an open picker/sheet first, then the editor (same as Done).
   No visible Back button. Changes apply immediately (no save step).
2. **Move to…** opens a bottom sheet of destinations: "Before <name>" for every other row except the
   one directly after it, plus "At the end" unless it is already last. One tap moves it there, closes
   the sheet, and the moved row stays expanded.
3. **Add after** opens the exercise picker and inserts the chosen exercise directly after that row;
   **Add exercise** appends. The picker marks exercises already in the workout with " · in workout"
   (duplicates allowed).
4. **Remove**: a row with no logged sets is removed at once and a snackbar offers **Undo** (restores it
   at the same position). A row with logged sets asks first: "Remove <name> and its <k> logged
   sets?" (Cancel keeps everything). No undo for that case.
5. **Replace** (renamed from Substitute everywhere in the UI): same data semantics as today
   (`substituteExercise`: keeps the slot's plan, sets `substitutedFor`, replacing back with the
   original clears it). Disabled on a row with logged sets, with supporting text "Has logged sets".
6. **Moves keep everything with the row**: its id, sets, notes, plan and `substitutedFor`.
7. **In-session ⋯ menu** becomes, in order: **Edit workout**, **Replace <current exercise name>**
   (disabled with "Has logged sets" when it has sets; hidden when no current exercise), divider,
   **Session notes**, **Light day**, divider, **Discard workout** (only when discardable, as today).
   The order arrows, Remove and Add exercise leave the menu. **Edit workout** and **Replace** show only
   while the workout is `in_progress`.
8. "View all exercises" list gets an **Edit workout** button at its end (in progress only).
9. **Before Start (preview)**: the preview header gets an **Edit** action that opens the same editor on
   a draft of today's rows (not stored). The preview list then shows the draft (names, order,
   plan; a replaced row shows "–" for plan load like 041). **Start** creates the session from the
   draft, and the clock starts then. Closing the preview (system Back) or choosing another workout
   discards the draft. In the draft, logged-set rules do not apply (no sets exist).
10. Structural writes (move-to, insert, remove, replace) are no-ops unless the session is
    `in_progress`, enforced in the DAO, not only the UI. Every structural write bumps the session
    revision once, as the existing operations do.

## Scope
- data: pure ordering functions + DAO operations; app: `WorkoutViewModel`, `WorkoutScreen.kt` (or
  a new `ui/WorkoutEditor.kt`), strings. No Room schema change, no migration, no server change.
- Out of scope (do not modify): `SessionSync.kt`, sync/conflict/revision logic other than calling
  the existing `bump`, `supabase/`, Home/History/Progress screens, set logging UI.
- Deferred: drag handles, a Swap action, persisting a preview draft across process death.

## Plan / progress
- [x] 1. **Ordering logic (data, JVM-tested)**: new `data/.../ExerciseOrder.kt` with pure functions on
      `List<SessionExerciseRow>` that return the list renumbered `position = 0..n-1` in list order:
      `moveTo(rows, id, toIndex)`, `insertAt(rows, row, index)`, `remove(rows, id)`,
      `replace(rows, id, exerciseId, exerciseName)` (substitutedFor rule as in
      `SessionDao.substituteExercise`). Unknown id → list unchanged (renumbered). Index clamps to
      `0..size`. JVM tests in `data/src/test/.../ExerciseOrderTest.kt` covering: move first→last,
      last→first, to own index (no change), insert at 0 / middle / end, remove middle, replace and
      replace-back clears `substitutedFor`, positions always contiguous, row ids preserved.
- [x] 2. **DAO**: `moveExerciseTo(id, toIndex)`, `insertExercise(row, index)` (row's sessionId set by
      caller), and make `removeExercise`/`substituteExercise` use the step-1 functions; each in one
      `@Transaction`, each a no-op unless the session is `in_progress`, each `bump`s once on change.
      Keep `moveExercise(id, delta)` working (callers may be removed in step 4; delete it if unused).
      Device tests in `SessionStoreTest`: move-to keeps set rows attached to the moved exercise id;
      insert in the middle renumbers; remove deletes only that exercise's sets; any of them on a
      `finished` session changes nothing (positions and localRevision unchanged).
- [x] 3. **ViewModel**: `moveExerciseTo`, `insertExercise(exerciseId, afterIndex)`, `removeExercise`
      returning/holding an undo token for unlogged rows (`undoRemove()` re-inserts the same row at
      its index), `replace`. Preview draft: `MutableStateFlow<List<SessionExerciseRow>?>` built from
      `startRows` for the previewed key; edits apply the step-1 functions to it; `closePreview` and a
      different preview key clear it; `start` uses the draft's rows (re-keyed to the new session id)
      when present. Preview state renders the draft with the same unit/substitution handling as
      `sessionState` (refactor a shared per-row helper rather than duplicating). JVM tests in
      `WorkoutStateTest` for draft → preview mapping (replaced row: no plan load, no target).
- [x] 4. **UI**: editor screen per acceptance 1–5 (Material 3, dark theme colors, 48 dp targets,
      row semantics "<n>. <name>, <k> sets logged"), menu per 7, View all button per 8, preview Edit per
      9. The rest strip keeps showing while editing a running workout; Finish is hidden while editing.
- [x] 5. `scripts/check --offline`; device tests; commit per step; update `docs/architecture.md`
      (workout editing paragraph) and this file.

## Verification (commands)
- Steps 1–5: `scripts/check --offline` passed after each step (build, lint, JVM tests).
- Steps 2 and 5: `cd android && ANDROID_SERIAL=emulator-5554 ./gradlew :data:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.repknot.data.SessionStoreTest` passed (7 tests each). Step 5 printed an emulator-console warning, but the tests ran and passed.
- `scripts/check --offline` (build, lint, all JVM tests) after each step.
- `cd android && ANDROID_SERIAL=emulator-5554 ./gradlew :data:connectedDebugAndroidTest
  -Pandroid.testInstrumentationRunnerArguments.class=com.repknot.data.SessionStoreTest`
  (the emulator is running; never run device tests without `ANDROID_SERIAL=emulator-5554`).
- Lead (not the worker): emulator walk-through as test2 — in one editor visit reverse two
  non-adjacent rows, insert one between them, remove a third and undo; preview Edit → Start.
- Lead review 2026-11-19: re-ran `scripts/check --offline` (BUILD SUCCESSFUL, lint back to the
  pre-task 28 after removing 12 resources the old menu left unused). Emulator (test2): preview Edit →
  move first row before the last (one tap, row stays expanded) → remove + Undo (restored in place) →
  Add after inserts at the right spot, picker marks exercises in the workout → Done shows the draft
  in the preview → Start creates the session in the edited order with the clock at 0. In-session ⋯:
  Edit workout, Replace <current>, Session notes, Light day, Discard. Lead polish: 2×2 row actions,
  Move sheet title, snackbar names the exercise, added rows read "Added · no plan".
- Owner follow-up (2026-11-19): drag handle on each row (playlist style) and swipe left to
  remove; Replace stays a button (lead recommendation: a swipe should not open a picker, and two
  swipe directions raise accidental triggers). Built with `sh.calvin.reorderable` 3.1.0
  (`ReorderableColumn`, pinned). Emulator (test2): dragging the last row to the top lifts it and
  reorders on drop (saved: logger shows the new order); swipe left on an unlogged row removes it
  with "<name> removed · Undo"; on a logged row it asks "Remove <name> and its 1 logged set?" and
  Cancel slides it back with the set kept; rest strip stays visible while editing. TalkBack gets
  Move up / Move down actions per row; Move to… stays in the tap menu.

## Decisions / discoveries
- Owner (2026-11-19): keep "Replace current" in ⋯; build preview editing now (customizing should
  not start the workout clock).
- Preview draft lives in ViewModel memory: lost if Android kills the app before Start (accepted).
- Structural DAO operations now check session status and bump the revision on change. Review these
  transaction and revision changes, including the retained append and relative-move entry points.

## Handoff
- Done. Owner checks on the phone: customize before Start and during a workout. Next: task 043.
