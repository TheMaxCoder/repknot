# 035 — Rest timer visible while typing; coach notes in the workout; mark notes done

Status: done
Starting commit: b016f24
Date: 2026-11-12
Follows: [034](034-coach-notes.md), [ADR 005](../decisions/005-coach-notes.md)

## Outcome / acceptance

Owner-approved design (options "A + B" for the rest timer, plus per-note done on Home):

1. **Log closes the keyboard (A).** Tapping Log set / Update set in the logger clears focus so
   the keyboard closes and the footer with the rest timer shows. The load field has
   `ImeAction.Next` (moves to reps); the reps field has `ImeAction.Done`, which only clears focus
   (it does **not** log the set). Focus is cleared on the tap even if validation then fails.
2. **Rest strip above the keyboard (B).** While the keyboard is open in an in-progress session
   and a rest is running (`restState` non-null, same rules as `RestLine`), the Scaffold bottom bar
   shows only a compact rest strip that sits directly above the keyboard (bottom bar gets
   `imePadding()`); the rest of the footer stays hidden as today. Content must not get double
   bottom space: apply `padding(padding).consumeWindowInsets(padding).imePadding()` (in that
   order) to the content column, so the IME inset is not counted twice when the strip is shown.
   Without a running rest, nothing is shown above the keyboard.
3. **Rest progress bar (both places).** `RestLine` (footer) and the strip show a thin
   `LinearProgressIndicator` (4 dp, full width, rounded) under the "Rest m:ss" text with progress
   `restProgress(elapsedS, targetS)` = elapsed / target clamped to 0..1 (target ≤ 0 → 1). Before the
   target: `primary` indicator; at/after target: `tertiary` indicator and the existing primary text
   color. The strip shows "Rest 1:24 / 2:00" (existing strings where possible) and the same
   target toggle button as `RestLine` (one shared composable is preferred). Only the rest
   composables read the ticking clock (keep the existing recomposition comment's intent).
4. **Coach note in the logger.** In `ExerciseCard`, directly under the exercise title row (above
   "instead of" / Plan), show each not-done coach-note item whose `exerciseId` equals the session
   exercise's `exerciseId` (not `substitutedFor`), in brief order: a tonal box (8 dp corners) using
   the item's kind container color, 20 dp kind icon tinted like on Home, and the text in
   bodyMedium with `maxLines = 2` + ellipsis when collapsed. Tapping the box toggles full text
   (`rememberSaveable` per session exercise + item index; role Button; state description
   expanded/collapsed). No box when there is no note.
5. **Markers before an exercise.** "Up next" in the logger and each exercise in `PreviewScreen`
   show, when that exercise has a not-done note, one line: the first matching item's kind icon
   (16 dp) + its text in bodySmall, `maxLines = 2`, ellipsis, onSurfaceVariant.
6. **Mark done on Home (device-local).** In the expanded Home card each item gets a trailing
   48 dp `IconButton` with a check icon (`ic_note_done`, new vector): contentDescription
   "Mark done" / "Mark not done". Done items leave their group and appear in a last "Done"
   group (heading shown under the same "≥ 2 non-empty groups" rule), rendered at 50 % alpha,
   tappable to undo. Collapsed summary: `"N notes"` counts not-done items; append `" · M for
   <workout>"` (not-done, for the selected workout) when M > 0 and `" · K done"` when K > 0. When every
   item is done the whole summary is `"All K done"` (new plural-aware string; K = item count).
   Done items are hidden in the logger, Up next and preview (items 4–5).
   Persistence: `AppSettings` stores done items as a string set of `"<noteId>:<index>"`
   (`doneCoachItems: StateFlow<Set<String>>`, `setCoachItemDone(noteId, index, done)`); writing
   drops entries for other note ids (a new brief starts fresh). Nothing is sent to the server.

### Model changes
- `CoachNoteItem` gains `val index: Int` = the element's position in the stored JSON array
  (stable identity within a brief; skipped malformed elements keep their position gaps).
  `parseCoachNoteItems` sets it.
- `groupCoachNotes(items, workoutExerciseIds, done: Set<Int> = emptySet())`: done items (by
  `index`) go only to a new `done` group (input order); other groups unchanged.
- `CoachNotesUiState` gains `done: Set<Int>` (indexes for the current note) and helper
  `activeItemsFor(exerciseId: String): List<CoachNoteItem>` (not done, matching exercise, brief
  order). `CoachNotesViewModel.toggleDone(index)`.
- Share kind styling (icon, label, container, tint) between Home and the workout in a new
  `ui/CoachNoteStyle.kt` instead of duplicating `NoteStyle`.

## Scope / out of scope

Allowed: `android/app/src/main/java/com/repknot/app/ui/{WorkoutScreen,CoachNotesCard,HomeScreen}.kt`,
new `ui/CoachNoteStyle.kt`, `{AppSettings,CoachNotesViewModel,CoachNotesText,WorkoutTiming,
AppNavigation,MainActivity}.kt` (wiring only for the last two), `android/data/.../CoachNotesStore.kt`
(index only), `android/domain/.../CoachNotes.kt`, app resources (strings, plurals, new vector
drawable), JVM tests in app/data/domain, and this task file's Decisions/Verification/Handoff.

Out of scope (do not modify): `supabase/`, `scripts/`, sync (`SessionSync`), Room schema and
migrations, ViewModel workout actions (`logSet` etc.), set validation, rest-target rules,
alert scheduling, navigation behavior, other screens, build files/dependencies, other docs,
worker rules. Do not read `.env`, credentials, `private-data/`, `work/`. No adb/install/DB commands.
Synthetic values only in tests.

## Plan / progress

- [x] Worker: implement 1–6 and model changes; JVM tests; `scripts/check --offline`; commit in
  two steps if natural (rest timer; coach notes), each verified.
- [x] Lead: review, rerun checks, install, phone check (keyboard close on Log, strip above
  keyboard while typing during rest, progress colors, note box/expand, markers, done/undo and
  persistence across restart).

## Verification responsibilities

Worker (JVM): `restProgress` (0, mid, exact target, beyond, target 0); `groupCoachNotes` with done
(done items only in `done`, order kept); `parseCoachNoteItems` indexes incl. a skipped element;
done-set pruning/encoding if extracted as a pure function; `activeItemsFor`. No shallow UI tests.
Lead (device): all visual/keyboard/interaction behavior above.

## Decisions / discoveries

- Done state is device-local by design (ADR 005 keeps the phone read-only for coach notes).
- Done key uses the item index within a brief; briefs are append-only, so indexes never shift.
- Rest timer and keyboard slice implemented in `495eb51`; coach-note slice implemented separately.
- Review: the first commit message omitted its pending device verification due to a shell command error; no commit was rewritten.

## Verification

- `scripts/check --offline` passed after the rest timer and keyboard slice, and again after the coach-note slice (build, lint, JVM tests).
- JVM tests cover rest progress, grouped done items, skipped JSON indexes, active items, and done-key encoding/pruning.
- Device keyboard, layout, interaction and persistence verification remain with the lead; no device commands were run.
- Lead: full diff reviewed and accepted. `scripts/check --offline` passed; domain/data/app unit
  tests rerun passed; `:app:installDebug` on the test phone.
- Lead, test phone (throwaway session, discarded afterwards; server list unchanged): preview
  markers on noted exercises; note box under the exercise title; Log set with the keyboard open
  closed it and the footer showed the rest timer with progress; tapping Reps during rest showed
  the rest strip directly above the keyboard, ticking; keyboard ✓ closed it without logging
  (still 1/3); after 2:00 the bar was full in the tertiary color. Home: mark done moved the item
  to a dimmed Done group; the collapsed summary counted the done item after force-stop and
  relaunch; undo restored the previous counts.
- Not verified: note box expand/collapse tap, TalkBack, large fonts, "All N done" state.

## Handoff

Done. Possible polish (not authorized): an empty-circle vs filled-check icon for not-done/done
items (both currently show a check, only alpha differs); the at-target tertiary color is subtle
on some wallpapers.
