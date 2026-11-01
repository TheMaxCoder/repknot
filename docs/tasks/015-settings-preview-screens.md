# 015 — Screens: settings page, workout preview, discard

Status: done
Updated: 2026-11-01
Starting commit: 3a54670
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
1. [x] Navigation + settings screen (items 1–2).
2. [x] Today → preview, preview screen (3–4).
3. [x] Discard + finished-status copy (5); cleanup (6).
4. [x] `scripts/check --offline`; commit(s) with task 015 in the subject.

## Decisions and discoveries
- Settings retains the existing cloud divider and uses the same Scaffold/inset and scroll padding as workouts.
- Review: discard confirmation invokes existing deletion of the local in-progress workout and its logged sets.

## Verification
- `scripts/check --offline` — passed after each of steps 1, 2, and 3 (debug build, lint, unit tests).

## Handoff
Implementation complete; next the lead reviews the commits and runs the device smoke test, including navigation/back, preview, upload switch, and discard confirmation. No device verification was run by the worker. The step 1 commit message omitted the pending device check due to a shell command formatting error; it remains pending.

## Lead review (2026-11-01)
Accepted without retry or clarification. Diff matches items 1–6 (shared `PrescriptionLine`
for preview and session cards is a good refactor). Test phone: Settings opens from
the header, shows **Upload workouts** off by default with the off description and the
account section; system back returns; the session shows **Discard workout** under
**Finish workout**; the confirm dialog opens and **Cancel** keeps the session; no crashes.
Not exercised on device: Today → Preview → Start, and an actual discard — the phone
had a Pull 1 workout in progress that the owner started on the previous build, which the
lead did not discard. Preview state is JVM-tested (`WorkoutStateTest`) and discard is
device-tested at the DAO level.
