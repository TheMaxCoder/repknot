# 015 — Screens: settings page, workout preview, discard

Status: ready
Updated: 2026-11-01
Starting commit: (worker: record `git rev-parse --short HEAD` when you start)
Parent: [014](014-settings-preview-local-mode.md) (logic done by the lead) · Delegated: yes

## Outcome and acceptance
The UI for task 014 using the existing ViewModels. Accepted when the screens below behave
as specified and `scripts/check --offline` passes. The lead runs the device smoke test.

## Scope
In: `android/app/src/main/java/com/repknot/app/MainActivity.kt`,
`android/app/src/main/java/com/repknot/app/ui/WorkoutScreen.kt` (and new UI files under
`ui/` if you split), `android/app/src/main/res/values/strings.xml`, this file.
Out of scope (do not modify): `WorkoutViewModel.kt`, `AppSettings.kt`,
`RepknotApplication.kt`, `CloudViewModel.kt`, anything in `data`/`domain`, new libraries
(no navigation or icon library). Reading any of them is fine.

## Context to read
`WorkoutViewModel.kt` (`WorkoutUiState.Preview`, `PreviewExerciseUi`, `preview`,
`closePreview`, `discard`, `start`, `uploadEnabled`), `AppSettings.kt`,
`RepknotApplication.setUploadEnabled`, `MainActivity.kt` (`CloudSection`),
`ui/WorkoutScreen.kt` (existing patterns: `Prescription`, `PreviousHint`, `FinishSection`).

## Screens (implement exactly; all text in `strings.xml`)
1. Navigation: a `rememberSaveable` screen value in `MainActivity` (`workout` | `settings`).
   The main screen gets a header row: "Repknot" (primary color, small) on the left and a
   **Settings** text button on the right. Settings shows a header row with **Back** and the
   title "Settings"; the system back gesture also returns (`BackHandler`). Both screens keep
   the existing edge-to-edge padding and dark theme.
2. Settings content, in order:
   - Section "Workouts": a row with label **Upload workouts** and a `Switch` bound to
     `app.settings.uploadEnabled` (collectAsState); toggling calls
     `app.setUploadEnabled(checked)`. Below it, the description (onSurfaceVariant):
     off → "Workouts stay on this phone. Programs still download when signed in.";
     on → "Finished workouts upload to the cloud."
   - Divider, then the existing `CloudSection` (moved here; removed from the workout screen).
3. Today screen: the suggested workout's big button reads **Preview <name>** →
   `preview(key)`; "Other workouts" buttons → `preview(key)`. Nothing starts from Today.
4. Preview screen (`WorkoutUiState.Preview`): program name (small, primary); "Next workout"
   label only when `suggested`; workout name (headline); one card per exercise in order with
   the exercise name (title), the prescription line (same format as the session card:
   "3 sets · 8–12 reps · RIR 1–2 · 25 kg", built from `Prescription` fields; plates `12p`,
   bodyweight "bodyweight") and the previous hint (same text rules as `PreviousHint`). Then a
   full-width **Start workout** button → `start(workout.key)` and an outlined **Back** button →
   `closePreview()`. System back also calls `closePreview()` while previewing.
5. Session screen: in the finish section, for status `in_progress` add an outlined
   **Discard workout** button next to/below **Finish workout**; it opens a confirm dialog
   ("Discard this workout? Logged sets on this phone will be deleted.") with **Discard** →
   `discard()` and **Cancel**. For status `finished`: when `uploadEnabled` is false show
   "Saved on this phone. Upload is off (Settings)." and hide **Retry upload**; when true keep
   the current "Upload pending" + **Retry upload**.
6. Remove the interim `is WorkoutUiState.Preview -> Text(...)` branch; remove strings that
   become unused.

## Plan / progress
1. [ ] Navigation + settings screen (items 1–2).
2. [ ] Today → preview, preview screen (3–4).
3. [ ] Discard + finished-status copy (5); cleanup (6).
4. [ ] `scripts/check --offline`; commit(s) with task 015 in the subject.

## Verification
(worker: exact commands and results)

## Handoff
(worker: what was done, anything unresolved or questioned)
