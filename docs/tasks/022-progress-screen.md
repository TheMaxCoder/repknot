# 022 — Progress screen and exercise history

Status: done
Updated: 2026-11-01
Starting commit: 1e2482e
Parent: [019](019-progress-milestone.md) step 3 · Delegated: yes (see
[delegation](../delegation/README.md))

## Outcome and acceptance
A Progress screen that tells the owner which exercises need attention and why, plus a
per-exercise history. Uses the engine from [020](020-progress-engine.md) (`progress`,
`attentionOrder`, `sessionDips`); no new progress rules. The owner is cutting: "holding
steady" is presented as fine, never as a warning. Accepted when the items below behave as
specified, JVM tests cover the pure functions, and `scripts/check --offline` passes. The
lead runs the device check.

## Scope
In: new `android/app/src/main/java/com/repknot/app/ProgressViewModel.kt`, new
`ProgressText.kt` + `ProgressTextTest.kt`, new `ui/ProgressScreen.kt`, `MainActivity.kt`
(navigation only), `ui/WorkoutScreen.kt` (header button only), `strings.xml`, this file.
Out of scope (do not modify): `domain`, `data`, `WorkoutViewModel.kt`, sync, new libraries
(no navigation, chart or icon library). Do not read `private-data/`.

## Behavior (implement exactly)
1. **Navigation.** `MainActivity`'s `screen` gains `"progress"` and `"exercise:<id>"`.
   The workout header row gets a **Progress** text button left of **Settings**. Progress
   has a header row with **Back** (→ workout) and the title "Progress"; tapping an exercise
   opens `exercise:<id>` whose **Back**/system back returns to progress. `BackHandler` on
   both. Same scaffold/insets/scroll padding as Settings.
2. **`ProgressViewModel(sessions: SessionDao, reference: ReferenceDao, today: () ->
   LocalDate = LocalDate::now)`** with a Factory like `WorkoutViewModel`'s. State
   `ProgressUiState(loading: Boolean, items: List<ExerciseProgress>, dips: List<SessionDip>,
   names: Map<String, String>)`, recomputed on every emission of
   `sessions.observeAllSessions()`: `history = sessions.history()`; `prescriptions =
   reference.activeProgram()?.workouts?.flatMap { it.exercises }.orEmpty()`; `items =
   attentionOrder(progress(history, prescriptions, today()))`; `dips =
   sessionDips(history)` filtered to dates within 28 days before `today()`, newest first;
   `names` = exercise id → `ExerciseProgress.name` (used for dip lists). Computation off
   the main thread (`Dispatchers.Default`).
3. **Pure text functions in `ProgressText.kt`** (English; numbers drop `.0`; loads `25 kg`,
   `9p`, bodyweight omitted; dates `MMM d` English):
   - `groupLabel(p: ExerciseProgress): String` — the group as ordered by `attentionOrder`:
     `daysSinceLast > 14` → "Not trained recently"; POSSIBLE_DECLINE → "Needs a look";
     `readyStreak ≥ 2` → "Ready to move up"; IMPROVING → "Improving"; MAINTAINING →
     "Holding steady"; NOT_ENOUGH_DATA → "Not enough data yet".
   - `reasonText(r: ProgressReason): String`: BackAfterGap → "Back after N days — judged
     again after 2 sessions"; FewSessions → "1 session so far — needs 3" / "N sessions so
     far — needs 3"; MovedUp → "Moved up to 27.5 kg on Oct 26"; MoreReps → "More reps at
     25 kg on Oct 26" (bodyweight/null load: "More reps on Oct 26"); BelowUsual → "Below
     your usual at 5 kg for 2 sessions" (null load: "Below your usual for 2 sessions");
     LoadDown → "Load down from 25 kg to 21 kg for 2 sessions"; NoNewBestSince → "Holding
     steady — last best Oct 16, N sessions since" (null date: "Holding steady — no best
     yet"). "N sessions since" uses "1 session since" for 1.
   - `flagText(p: ExerciseProgress): List<String>`: `readyStreak ≥ 2` → "Top of the range
     N sessions in a row — move up, or change tempo or pause if the stack is maxed";
     `daysSinceLast > 14` → "Not trained for N days".
   - `exposureLine(e: Exposure): String` → `"Oct 30 · " + previousSummary(e.sets)`, plus
     `" · light"` when `e.light`.
   - `dipText(d: SessionDip, names: Map<String, String>): String` → "Broad dip on Oct 26 —
     4 of 7 exercises below usual (Seated row, Bar curl, …). Check sleep, food and
     recovery." (all names, comma-separated, id when missing).
   - Any input combination not listed: the most conservative readable text; record it under
     Decisions and keep going.
4. **Progress screen.** Loading → a centered progress indicator. Empty items → "No
   finished workouts yet." Otherwise: each dip as a card (`dipText`, errorContainer
   colors) at the top; then the items in order with a group header (`groupLabel`,
   labelLarge, primary) whenever the group changes. Each item is a clickable row/card:
   name (titleMedium); `reasonText` (bodyMedium; `error` color for "Needs a look",
   primary for "Improving", onSurfaceVariant otherwise); each `flagText` line (bodySmall,
   tertiary); the last 3 exposures' `exposureLine`s (bodySmall, onSurfaceVariant), newest
   first.
5. **Exercise screen** (`exercise:<id>`, the item with that id from the same state): name
   (headlineSmall), group label, `reasonText`, flags, then every exposure newest first as
   `exposureLine`, with a trailing " · best" when its `sessionId` is in `bestSessionIds`.
   Unknown id → "Exercise not found" and Back.

## Tests
`ProgressTextTest`: every branch of `groupLabel` (including the override for > 14 days),
`reasonText` (all reasons, singular/plural, null load/date), `flagText` (none, ready, not
trained, both), `exposureLine` (normal, light), `dipText` (known and missing names).
ViewModel and screens are checked by the lead on the device.

## Plan / progress
1. [x] `ProgressText.kt` + tests.
2. [x] `ProgressViewModel` + navigation + screens.
3. [x] `scripts/check --offline`; commit(s) with task 022 in the subject.

## Decisions and discoveries

- Pre-existing untracked `work/` is unrelated and left untouched.
- For unusual bodyweight MovedUp/LoadDown inputs, omit load values and use “Moved up on …” / “Load down for 2 sessions”; normal engine output does not generate these. Exposure summaries use `previousSummary` exactly as specified, including its bodyweight label.
- Recent dips include today and the date 28 days before today; future dates are excluded. Same-date dips retain engine order.
- Review: commits `dd871d7` and `3a8add1` omitted their Not verified and Review lines due to commit command mistakes. Device verification remains pending; review the unusual bodyweight fallback and recent-dip boundary above.

## Verification

- `cd android && ./gradlew --offline :app:testDebugUnitTest` — passed, including ProgressText tests.
- `scripts/check --offline` — initially failed due to ambiguous R import, fixed; subsequently passed twice (debug build, lint, JVM tests), including after loading layout adjustment.
- Device check not run; delegated to lead.

## Handoff

- Implementation and offline checks complete; lead should review the diff and run the specified device check, including loading, navigation/back, grouping, and exercise history. Review the edge-case formatting and recent-dip boundary above. No unfinished task changes. Untracked `work/` remains untouched.

## Lead review (2026-11-01)
Accepted without clarification stops. ViewModel, navigation and text functions read
against items 1–5; the two recorded fallbacks (bodyweight MovedUp/LoadDown text, inclusive
28-day dip window) are fine. Test phone on the real history: dip, "Needs a look" and
"Improving" cards matched the history; exercise history lists every session with "best" tags; Back
and system back work; no crashes.
Tuning note (not changed): a "best" counts +1 on reps+RIR, so the same reps reported one
RIR easier is a best (seen in the real history). Revisit after a few weeks of phone data
if bests feel too generous.
