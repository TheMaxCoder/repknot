# 014 — Settings page, workout preview, discard, upload switch

Status: in progress
Updated: 2026-11-01
Starting commit: a93df78
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
2. [ ] Screens (task 015, delegated).
3. [ ] Device smoke + run device/integration tests.

## Decisions
- "Online logging" = upload of workouts. Downloading programs/sessions stays on so the
  app keeps the active program current; signing in remains optional for local use once
  the program is cached.
- Discard is limited to never-uploaded sessions; uploaded ones are server data and would
  need a delete-sync design (not requested).

## Verification
- `scripts/check --offline`: BUILD SUCCESSFUL; WorkoutStateTest 4/4.
- Not run: device tests and integration test — the phone disconnected (`adb devices` empty).

## Handoff
Next: task 015 screens; then run `:data:connectedDebugAndroidTest`,
`scripts/test-sync-integration`, and a device smoke (upload off: start → finish → stays
local; switch on → uploads) once the phone is connected.
