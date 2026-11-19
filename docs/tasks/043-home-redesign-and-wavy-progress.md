# 043 — Home redesign and wavy progress

Status: in progress · starting commit `0a28df0`

Owner request (2026-11-19), after a UX review by a second model (Sol) and the lead:
- Keep Preview (often wanted as an overview), add Start next to it: two buttons.
- Drop the exercise list from Home ("half the workout") so the coach brief gets the room.
- The workout list must offer Preview / Start per workout; today previewing a non-next workout
  means select → back → Preview.
- Agreed from the review: current-workout state on Home says what it is; attention rows only when
  something needs doing; coach brief right under the workout card.
- Progress bars get the Material 3 Expressive wavy look, animated.

Space check (owner's phone, 2026-11-19): the workout card plus exercise list
fill about half of Home today; the coach brief starts near the middle.

## Outcome / acceptance
1. **Workout card (no current workout)**: label ("Next in rotation"), workout name (displaySmall),
   one line "<n> exercises · <first exercise name>", then a row of two equal-width buttons:
   **Preview** (outlined, opens the existing preview) and **Start** (filled, starts that workout
   directly, same as Start on the preview). Below them a text button **Other workouts**. No exercise
   list, no "Your next workout" heading. Start is disabled for a workout with no exercises.
2. **Other workouts** sheet: one row per program workout in sequence order (name, exercise count, a
   "Next" tag on the suggested one), each with **Preview** and **Start** buttons on the row. No radio
   buttons and no "Use selected": the Home card always shows the suggested workout, so the
   selection state (`selectedKey`/`selectedProgram`/`selectedSuggestion` in `WorkoutScreen`,
   `HomeScreen.onSelect`) is removed. Preview from the sheet closes the sheet and opens that
   workout's preview (its Start and 042's Edit work as for the next workout).
3. **Current workout on Home** (replaces the "Current workout" block in `AppNavigation`): the same
   card position shows the session title and a status line, with one filled button:
   - in progress → status "In progress · <elapsed>" (elapsed via `elapsedWorkoutText`), button
     **Resume**;
   - finished, upload not tapped → "Saved on this phone · not uploaded", button **Review & upload**;
   - finished, upload requested → "Upload pending", button **Review & upload**;
   - pushed → "Uploaded", button **Open** ; conflict → "Needs a decision", button **Open**.
   All buttons open the logger as Resume does today. Remove the "From Progress" text button there.
4. **Coach brief** directly under the workout card (both states). It starts **expanded** (it now has
   the room); collapsing still works and is remembered for that note while the app runs. The brief
   is marked seen when shown expanded on Home (existing `markSeen`). Its per-workout grouping uses
   the suggested workout (or the current session's program workout when one exists, if available
   from state; otherwise the suggested one).
5. **Attention rows** below the brief, each a full-width tappable row (icon, title, chevron), shown
   only when true:
   - **Weight & waist check-in due**: Mon/Wed/Fri at or after 08:00 local time and no check-in dated
     today (same days/time as `CheckInReminder`; put the rule in a pure function next to it and
     JVM-test it: before 08:00, on a non-reminder day, with today's check-in, and the due case).
     Tap opens Progress → Body with the Add check-in dialog open (the notification's route).
   - **Training alert**: when `progress.dipAlert` is non-null show its existing alert text; else when
     `progress.declines > 0` show the existing "<n> exercises need a look". Tap opens the Progress tab.
   The old always-visible "From Progress" heading and card are removed.
6. **Wavy progress** (custom, no new dependency; Material3 1.4.0 has no public wavy API):
   a `WavyLinearProgress(progress: () -> Float, modifier, color, trackColor)` composable in
   `ui/WavyProgress.kt`, drawn with `Canvas`: the filled part is a sine wave (wavelength 32 dp,
   amplitude 3 dp, stroke 4 dp, round caps), the remaining track a flat line of the same stroke with
   a 4 dp gap between them; the wave scrolls continuously (phase animation, about one wavelength per
   second via `rememberInfiniteTransition`); amplitude eases to 0 when progress is ≤ 0.02 or ≥ 0.98
   so the bar reads flat when empty or complete; progress changes animate (`animateFloatAsState`).
   Respects the system "remove animations" setting (no phase scroll when animator duration scale
   is 0). Used for: the rest-timer bar (`RestLine`, wave color switches to tertiary when the rest
   target is reached, as now) and the upload bar (`UploadProgress`, task 041).
   The full-screen loading spinner stays as it is.
7. Dark theme colors only from `MaterialTheme.colorScheme`; 48 dp touch targets; buttons wrap text
   at large font scales without clipping.

## Scope
- App module: `ui/HomeScreen.kt`, `AppNavigation.kt` (current-workout block, attention-row
  navigation), `WorkoutScreen.kt` (selection state removal, `RestLine`, `UploadProgress`),
  `CheckInReminder.kt` (shared due rule), new `ui/WavyProgress.kt`, strings, tests.
- Out of scope (do not modify): data module, `SessionSync`, sync logic, the workout editor from
  042 beyond wiring Start/Preview, Progress/History screens' own layouts.

## Plan / progress
- [x] 1. Check-in-due rule + JVM tests; attention rows; remove "From Progress".
- [x] 2. Workout card (Preview | Start, Other workouts), sheet with per-row Preview/Start, selection
      state removed; coach brief moved under the card and expanded by default.
- [ ] 3. Current-workout card states (acceptance 3).
- [ ] 4. `WavyLinearProgress` + use in rest and upload bars.
- [ ] 5. `scripts/check --offline` after each step; commit per step; architecture note; this file.

## Verification
- Before implementation: `scripts/check --offline` passed (build, lint, JVM tests; most tasks up-to-date).
- Steps 1 and 2: `scripts/check --offline` passed after each (build, lint, JVM tests).
- `scripts/check --offline` (build, lint, JVM tests) required after each step.
- Lead: emulator (test2) screenshots of Home without and with a current workout, the Other workouts
  sheet, a rest bar mid-rest and at target, an upload; then install on the owner's phone.

## Decisions / discoveries
- Custom wavy indicator instead of upgrading Material3 to 1.5.0-rc01 (pre-release, app-wide).
  Replace with the official `LinearWavyProgressIndicator` once a stable BOM ships Material3 1.5.
- Home always shows the suggested workout; choosing another workout happens in the sheet by
  previewing or starting it directly (owner: the select-then-preview round trip was the problem).
- Owner clarified: leave `WorkoutViewModel` unchanged. Pass the workout name and exercise ID set to
  `CoachNotesCard`: use the suggestion without a current workout, and the session title and session
  exercise row IDs with a current workout.

## Handoff
- Next action: implement step 3. Steps 3–5 remain unfinished.
  Lead emulator screenshots and owner phone verification remain pending.
