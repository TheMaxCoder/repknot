# 014 — Settings page, workout preview, discard, upload switch

Status: done
Updated: 2026-11-01
Starting commit: 81e7a43
Lead task; the screens are delegated as [015](015-settings-preview-screens.md).

## Outcome and acceptance
Owner request (2026-11-01): a settings page holding the account; a switch so workouts can
be used without logging them online ("we will turn on online logging later"); view a
workout without starting it; stop a started one. Accepted when:
- Upload is a device setting, **off by default**. While off, refresh still downloads
  programs and sessions (today's workout stays correct) but nothing is uploaded; finish
  keeps the session local (`finished`). Turning it on triggers a refresh that uploads
  pending sessions.
- Tapping a workout opens a preview (prescriptions + previous values) without storing
  anything; **Start** from the preview creates the session.
- A started workout that was never uploaded can be discarded (confirm), returning to today.
- Settings page reachable from the main screen; account section moved there.
- `scripts/check --offline` passes; device + integration tests pass when a device is connected.

## Plan / progress
1. [x] Lead logic: `SessionSync(uploadEnabled = …)` skips pushing; `SessionDao.discard`
   (in-progress, no server number/base revision only); `AppSettings` (prefs, default off);
   `RepknotApplication.setUploadEnabled` (refresh on enable); `WorkoutViewModel`:
   `Preview` state + `previewState` helper, `preview`/`closePreview`/`discard`,
   `uploadEnabled` flow, `finish` only syncs when enabled. Tests: `WorkoutStateTest`
   preview; device `discardRemovesOnlyANeverUploadedWorkoutInProgress`; integration test
   step "upload disabled keeps a finished session local".
2. [x] Screens (task 015, delegated).
3. [x] Device tests + integration + smoke (see Verification).

## Decisions
- "Online logging" = upload of workouts. Downloading programs/sessions stays on so the
  app keeps the active program current; signing in remains optional for local use once
  the program is cached.
- Discard is limited to never-uploaded sessions; uploaded ones are server data and would
  need a delete-sync design (not requested).

## Verification
- `scripts/check --offline`: BUILD SUCCESSFUL; WorkoutStateTest 4/4.
- Test phone: `:data:connectedDebugAndroidTest` — SessionStoreTest 5/5,
  WorkoutDatabaseTest 6/6 (incl. discard); `scripts/test-sync-integration` — OK (1 test),
  incl. "upload disabled keeps a finished session local" against dev.
- Smoke: see task 015 review (preview→start and a real discard not exercised: owner's
  in-progress Pull 1 was left alone).

## Handoff
Done. Turn on **Upload workouts** in Settings when ready to log online.
