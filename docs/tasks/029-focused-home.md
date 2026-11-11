# 029 — Focused Home

Status: done
Starting commit: f1cf6f3bfb0375e88c373ccef801b41c66d7ecc6
Date: 2026-11-02
Interrupts: [027](027-workout-timing.md) remains paused at its watch handoff;
[028](028-explicit-upload.md) is done with owner interaction checks pending.

## Outcome / acceptance

Implement the owner's approved Focused Home in native Compose, using the refined
mockup `repknot-home-focused-flat.html` (local mockup, not in the repo)
as a visual guide. Flat tonal cards with about 8 dp corners, no card elevation,
no app name in the header. Native theme, accessibility, and actual data take precedence
over pixel matching. The mock's partial Progress/Settings dialogs are prototype shortcuts,
not requirements: navigate to the real existing screens.

- Home (Today state) has Progress and a Settings icon with an accessible label; no
  app title. Active program name above “Your next workout”. Use the dynamic dark
  Material scheme, with the existing fallback, safe insets, and scrollable content.
- A flat workout card shows “Next in rotation” for the suggested workout, otherwise
  “Your selection”, selected workout name, actual exercise count, first three ordered
  exercise names with numbers, and a compact line listing remaining exercise names.
  For 0–3 exercises omit the remaining line; for zero show an explicit empty message.
  Do not invent muscle groups, duration, history, names, or statistics. Use actual
  selected program prescriptions sorted by position. Names wrap without truncation.
- One filled full-width “Preview workout” action calls existing vm.preview(selectedKey).
  Preview must remain read-only, with the existing Start action and Plan/Last screen.
  No selection or preview action starts a workout or alters the program/rotation.
- “Choose another workout” opens a native modal bottom sheet, containing every workout
  in program order with its actual name and exercise count; mark the suggested one
  as next. Accessible single-choice rows (whole row tappable, >=48 dp targets).
  A pending selection is applied only with “Use selected workout”; dismiss/Back
  leaves the prior selection intact. Close the sheet on apply; preview is a separate tap.
  Selection survives recomposition/configuration changes and Progress/Settings visits,
  but falls back to the current suggestion if removed from the program. Fresh Home
  should suggest the correct next workout after a completed workout; no stale selection
  across a changed program/suggestion. Do not persist this preference to storage.
- A “From Progress” section has a flat clickable summary card opening existing Progress.
  Use ProgressUiState.declines (same possible-decline/recent predicate as Progress's
  “Needs a look” grouping), with proper singular/plural text: “1 exercise needs a look” /
  “N exercises need a look”, supporting “Review your training history”. While loading
  or when zero, use neutral “Training & body” / “Exercise history, weight and waist”;
  never imply zero means healthy or improving. Reuse a single ProgressViewModel in
  MainActivity for Home and existing Progress/Body/details; no duplicate calculation.
- Preserve Loading, NoProgram, error messages, Today/Preview navigation, session entry,
  active and finished workout screens, rest alerts, explicit Upload/Resume/Discard,
  and system Back. Only Today's header changes from Settings text to icon.

## Scope / out of scope

Allowed: `ui/WorkoutScreen.kt`, new `ui/HomeScreen.kt` if helpful, `MainActivity.kt`
(shared ProgressViewModel wiring), app resources (strings/plurals/vector icons), focused
app presentation helpers/tests only if new nontrivial logic needs coverage, and this task.
`WorkoutViewModel.kt` is also allowed solely to expose the existing `Program.id` as
`WorkoutUiState.Today.programId` and pass it from `todayState`; update/test affected
app state construction as needed. This metadata enables reliable UI selection reset;
do not change ViewModel actions, persistence, or workout choice rules.
Use small local drawable vectors for needed icons; no new dependencies. Do not change
domain/data behavior, ProgressScreen layout, PreviewScreen, session UI,
backend/sync/auth/migrations/build tooling or worker rules. Do not read `.env`, credentials,
private-data, or raw personal exports. Do not copy mockup health numbers or exercises
into product code. Leave unrelated changes untouched. Worker must not use adb or install.
Lead owns status/architecture updates after review.

## Plan / progress

- [x] Worker implements Home and native chooser, runs checks, inspects staged diff,
  and commits a coherent verified UI step with this handoff.
- [x] Lead reviews full diff, independently runs checks, resolves review findings.
- [x] Lead installs on phone; verifies Home, chooser apply/cancel, alternate preview,
  Progress/Settings navigation, and narrow/large-text layout without starting workouts.
- [x] Lead updates routing/architecture and commits verification handoff.

## Decisions / discoveries

This is a UI slice, no ADR needed. Starting tree is clean. Task 028 is the current
baseline: uploads are explicit per-record, not controlled by a Settings switch.
Flat means the approved mock's low-radius tonal blocks without elevation. The primary
button retains native Material shape. The decorative dumbbell icon is optional;
Settings accessibility and visual hierarchy take priority. Chooser apply/cancel behavior
is explicitly specified above rather than copying the prototype's immediate underlay update.

Lead scope clarification: worker correctly identified that Today lacked program identity.
Exposing the existing program ID in the app state is authorized; no new model or storage
is needed. This was a spec gap (delegation category 1), resolved before UI implementation.
Keep small presentational choices local and conservative. No remaining blocker.

Today now carries `Program.id`; distinct programs may share names and workout keys.
The workout saveable state holder retains selection across navigation, and selection is
reset when program ID or suggestion changes or the selected workout disappears. The
chooser uses a separate pending selection. The existing Progress state supplies declines.

## Verification responsibilities

Worker: `scripts/check --offline` (debug build, lint, JVM tests); `git diff --check`;
inspect explicit staged files and commit. Do not add shallow layout tests or weaken tests.
If new pure selection/formatting logic is extracted, cover its meaningful edge cases;
reuse existing `declines` and plural resources rather than duplicating domain rules.
Lead: independently repeat `scripts/check --offline` and diff review. Device manual
checks own visual/accessibility/interaction criteria above, including single-row/long-name
wrapping and chooser scrolling under larger text. Preserve system settings after testing.
No DB/device destructive operations, Upload/Finish/Start/Discard or account changes needed.
Existing real workouts must be preserved. Screenshot success alone is not interaction proof.

## Verification

Before implementation: `git status --short` clean; connected test phone
currently shows Home with Pull 1 next, no active workout visible (reported by lead; worker
used no device). No app state mutated.

Worker baseline: `scripts/check --offline` passed (debug build, lint, JVM tests;
95 tasks up-to-date); `git diff --check` passed before task-note changes.

Worker implementation: `scripts/check --offline` passed (106 tasks, 27 executed);
`git diff --check` passed. No device checks or installation performed.

Lead milestone: independent `scripts/check --offline` passed, full worker diff reviewed,
and `cd android && ./gradlew :app:installDebug --offline` installed. Chooser cancellation,
alternate selection/apply, real Plan/Last preview and Back, Progress summary navigation,
Settings icon navigation and selection retention all passed on the test phone. No workout
was started or modified. Large-text test at font scale 1.8 found the default partially
expanded sheet could leave its apply action below the screen; original font scale 1.0
was restored. Lead is making the native chooser skip its partial anchor, then will rerun
checks and repeat large-text verification (bounded worker retry already used on scope).
The chooser now uses `rememberModalBottomSheetState(skipPartiallyExpanded = true)`;
`scripts/check --offline` passed again after this change (106 tasks, 15 executed).
This is a one-off UI miss found by device testing; no worker rule changes are needed.

### Final lead verification

- Reviewed the complete implementation diff: app UI/resources, one existing program-ID
  mapping assertion, shared ProgressViewModel wiring and saveable selection only. No
  data/domain/sync/backend changes, dependencies, weakened tests, or new upload actions.
- `scripts/check --offline` passed independently for 125ca7a and again after e0960fe
  (build, Android lint and JVM tests). Gradle still reports its existing deprecation notice.
  `git diff f1cf6f3..HEAD --check` passed. Both installations used
  `cd android && ./gradlew :app:installDebug --offline` and succeeded on the test phone.
- Device script `verify_home_phone.py` in the mockup's thread directory passed:
  choose Push 1 then Android Back retains Pull 1; choose/apply Legs 2 changes Home;
  Preview opens that workout's real Plan/Last screen with Start untouched; Back retains
  selection; the attention summary opens Progress; Settings icon opens Settings;
  returning from both retains the alternate. Progress displayed its matching Needs a look group.
- After the fully expanded-sheet fix, `verify_home_large_text.py` passed at font scale
  1.8: selected long workout name wraps, selection survives Activity recreation,
  chooser's final long label and apply action remain reachable. First attempt before
  the fix failed because the default partial anchor hid the action. An initial rerun
  hit transient adb UI-dump readiness; the script now rejects stale dumps and passed.
- `verify_home_narrow.py` passed with a temporary 720×1600 display size (about 327 dp
  width at the existing density) and font scale 1.5. Screenshots before/after a list
  swipe show chooser content moving while the apply button remains fixed; the long
  final label wraps fully. Home's exercise remainder text also wraps. XML confirmed
  a single-choice scrollable list and accessible Settings label; this is not a TalkBack test.
- Restored physical display 1080×2400 and original font scale 1.0; retained the original
  density override 352. Restored selected workout to suggested Pull 1 and scrolled Home
  to its top. No sessions/check-ins were started, edited, finished, uploaded, or discarded.
  No Repknot entries appeared in `adb logcat -b crash -d -v brief`.
- Screenshots and the small device scripts/report are outside Git under the thread's
  visualization directory; `home-final-installed.png` is the final default-size Home.
  Empty/zero/singular-data presentation and program-ID/suggestion reset were reviewed in
  source (existing state test covers ID mapping), not exercised by modifying phone data.
  TalkBack and separate tablet/landscape checks remain unverified. Owner visual feedback
  is the next action; no known functional blocker remains.

## Handoff

Implemented, reviewed, checked and installed. Worker UI commit: 125ca7a. Lead large-text
fix: e0960fe. Planning/scope records: 1e79277, 7bff5ba, 37b697b. Final routing/architecture
and this verification are committed separately. No unfinished code or unrelated drift.
Next: owner reviews Home on the phone; continue screen-by-screen only on request.
Task 027's paused watch work and task 028's pending owner upload-flow check remain intact.
