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
- [ ] Lead reviews follow-up diff, repeats checks, and assesses device verification.

## Decisions / discoveries
- The user selected the body card from the first concept and grouped training history from the second, then removed all introductory copy.
- Body metrics use two columns when both measurements exist and available width divided by font scale is at least 250 dp; otherwise they stack. Goal caption and weight trend remain separate from the prominent measurements. BodyScreen retains its original presentation. The body-history tap region and check-in button are separate siblings. Group counts follow contiguous attention-ordered groups.

## Verification
`scripts/check --offline` passed for the original implementation and again for the follow-up (debug build, lint, and unit tests); Gradle reported deprecated features. The first follow-up run failed on a Kotlin cross-module smart cast; corrected before the passing run. Added formatting tests for metric precision, configured goal and weight trend. No device or visual verification was run.

## Handoff
Review feedback implemented and offline checks complete. Lead should review the follow-up diff, repeat checks, and assess device verification, including narrow screens, scaled text, missing metrics, and independent body-history/check-in actions. No known blockers or unfinished implementation changes. Unrelated untracked `work/` was left untouched.
