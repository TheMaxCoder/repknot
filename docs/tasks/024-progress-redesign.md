# 024 — Progress screen redesign

Status: in progress
Starting commit: 6d0fcb7

## Outcome and acceptance
Implement the owner's approved combined mockup at `progress-combined.html` (local mockup, not in the repo) as a native Compose screen. The mockup is a visual guide, with illustrative data: do not hardcode its values. No introductory slogan or extra header copy. Keep the dark dynamic Material theme and accessible contrast.

- Body card directly under the title, with a body label and affordance to open body history, waist and weight prominent where available, goal distance and last check-in, and a separate Add check-in action. Preserve all existing trend, verdict, missing-data, and check-in information/behavior (it can flow beneath the prominent metrics inside the card). Avoid nested click targets swallowing the add action. Use the current configured goal and display measurement precision accurately.
- Exercises retain existing attention order and group labels. Show group headers with a small status marker and count; exercise cards show name, reason, existing flags and up to three recent exposures as separate compact rows, newest first, plus a tap affordance. Preserve special cases, broad-dip alerts, empty/loading states and exercise-detail navigation. Keep existing date/load/set formatting and light-day markers.
- Keep layout readable on narrow phones and when text scales. No decorative fake data, charts, new packages, or changes to domain/sync logic. The sample is direction not pixel-exact; Compose semantics and dynamic colors take precedence.

## Scope
Implement in `android/app/src/main/java/com/repknot/app/ui/ProgressScreen.kt` and add focused presentation helpers/resources/tests only if needed. Do not modify existing unrelated `work/`. Out of scope: other screens, data logic, database, dependencies, and changing the text of existing domain conclusions.

## Plan / progress
- [x] Build the body summary and grouped training cards, preserving functionality.
- [x] Run `scripts/check --offline`; record results and commit verified changes under AGENTS.md.
- [x] Address review feedback: responsive side-by-side body metrics, preserving existing body detail and actions.
- [x] Lead reviewed the implementation and repeated offline checks.
- [x] Address further review feedback: align rate rows and make the footer responsive.
- [ ] Verify the latest screen visually on the phone, including scaled text and actions.

## Decisions / discoveries
- The user selected the body card from the first concept and grouped training history from the second, then removed all introductory copy.
- Body metrics use two columns when both measurements exist and available width divided by font scale is at least 250 dp; otherwise they stack. Goal caption and weight trend remain separate from the prominent measurements. BodyScreen retains its original presentation. The body-history tap region and check-in button are separate siblings. Group counts follow contiguous attention-ordered groups.

## Verification
`scripts/check --offline` passed for the original implementation and again for the follow-up (debug build, lint, and unit tests); Gradle reported deprecated features. The first follow-up run failed on a Kotlin cross-module smart cast; corrected before the passing run. Added formatting tests for metric precision, configured goal and weight trend. Lead reran `scripts/check --offline` successfully and `git diff 6d0fcb7..HEAD --check` passed. `cd android && ./gradlew :app:installDebug --offline` installed successfully on the test phone. The phone was locked; no visual or interaction verification was performed.

## Handoff
The latest alignment follow-up is implemented and offline checks pass. Next: lead review, install the latest build if the phone is available, and visually check both column and stacked layouts, long stale dates, scaled text, and independent actions. Device visual, accessibility and interaction verification remain pending. Unrelated untracked `work/` was left untouched.

## Body card follow-up — owner feedback

The lead inspected the installed screen on the unlocked phone on 2026-11-01: the body card is noisy because waist and weight rates, goal forecast, date, verdict and action are successive lines. The owner wants the 4-week rates and goal forecast visible, arranged by measurement. Implement this focused follow-up:

- In the Progress body card show waist and weight as two clearly labeled columns (stack on narrow/large-text layouts), each with measurement and its own rate under a `Last 4 weeks` label. Waist shows its goal distance. When available, show the goal forecast separately and explicitly as an estimate (e.g. `Estimated 80 cm around Dec 20`). Use actual configurable goal, dates, and existing formatting. Do not invent a forecast when absent.
- Place the existing verdict in its own visually separated row and the last check-in date and Add check-in action in a separate footer. Keep body detail navigation and check-in independent accessible targets. Keep stale check-in guidance, no-check-in/waist/weight, no-trend and goal-reached cases understandable. Trend absence should say not enough data, not imply zero. The weight percentage remains available in Body details; existing BodyScreen and its text should remain intact. Keep existing full verdict including caution.
- No changes to domain calculations, sync, training alerts, exercise cards, or other screens. No new dependencies. Scope: ProgressScreen.kt, body presentation helpers/resources and focused tests, task notes only.
- Worker: run `scripts/check --offline`, commit verified implementation and update this handoff. Lead: review diff, re-run check, install on connected phone if available, and inspect screenshot without interacting with personal data. Record remaining manual checks. Do not declare device interaction checks passed from a screenshot.

Progress: [x] implement and test; [x] address further alignment feedback; [x] lead review and phone visual check; [ ] owner feedback and remaining interaction/edge-case checks.

### Decisions and discoveries
- Each measurement has its own four-week rate; missing measurements are labeled explicitly and missing rates say there is not enough data. A real zero rate remains zero. The estimate uses the configured goal at measurement precision and is omitted without a forecast. Body details and its percentage text are unchanged.
- The verdict remains in the history navigation target, separated by a divider; the dated footer and check-in action are outside that target. The existing responsive width/font-scale threshold also applies when a measurement is missing.
- In two-column layouts the measurement blocks share a row and the rate blocks share a separate row, so the waist goal caption does not offset the weight rate. In stacked layouts each rate remains with its measurement. The footer uses a wrapping row so the date and action share space when they fit and stack when they do not.

### Verification
- `scripts/check --offline` passed (debug build, lint, unit tests; Gradle deprecation warning). Focused tests cover signed and missing rates, zero, estimate precision/absence, and unchanged percentage in Body details.
- After the further alignment feedback, `scripts/check --offline` passed again (debug build, lint, unit tests; Gradle deprecation warning).
- Lead reran `scripts/check --offline` and `git diff e815089..HEAD --check` successfully; `cd android && ./gradlew :app:installDebug --offline` installed on the test phone. A screenshot of the real Progress screen showed aligned four-week rates, labeled estimate, separated verdict and date/action footer without visible clipping at the phone’s current size. No accessibility or interaction checks were performed. Commit `c66231e` omitted its intended `Not verified:` trailer because the commit command split incorrectly; the missing limitation is recorded here and in the subsequent handoff commit.

### Handoff
Obtain owner feedback and arrange manual checks of scaled/narrow layout, missing data, long stale guidance, scrolling and both independent actions; the current-size screenshot alone does not verify interaction. Review: no known requirement guesses or plan deviations. Unrelated untracked `work/` remains untouched.

## Progress header follow-up
- Owner requested removal of the on-screen Back button from Progress. The Android system back handler in `MainActivity` still returns to Workout; Body and exercise detail keep their existing Back buttons.
- `scripts/check --offline` passed after the change. Phone installation and system-back interaction verification remain pending: the phone currently has an in-progress workout, so do not interrupt it or risk its unsaved input. Next: install once the workout is safely finished or the owner chooses, then check Progress → system Back returns to Workout.
- Owner confirmed workouts are local test data and authorized starting/discarding test workouts. `cd android && ./gradlew :app:installDebug --offline` installed the header change on the test phone. The Progress screen showed no header Back button and `adb shell input keyevent KEYCODE_BACK` returned to the workout screen. The workout preview was opened for visual inspection; no workout was started or discarded during this check.
