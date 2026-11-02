# 029 — Focused Home

Status: in progress
Starting commit: e6eea0950212e3fa3a9f5f1cc2338cb32d7d9d52
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
Use small local drawable vectors for needed icons; no new dependencies. Do not change
WorkoutViewModel/domain/data behavior, ProgressScreen layout, PreviewScreen, session UI,
backend/sync/auth/migrations/build tooling or worker rules. Do not read `.env`, credentials,
private-data, or raw personal exports. Do not copy mockup health numbers or exercises
into product code. Leave unrelated changes untouched. Worker must not use adb or install.
Lead owns status/architecture updates after review.

## Plan / progress

- [ ] Worker implements Home and native chooser, runs checks, inspects staged diff,
  commits a coherent verified UI step using AGENTS.md, updates this task.
- [ ] Lead reviews full diff, independently runs checks, resolves review findings.
- [ ] Lead installs on phone; verifies Home, chooser apply/cancel, alternate preview,
  Progress/Settings navigation, and narrow/large-text layout without starting workouts.
- [ ] Lead updates routing/architecture and commits verification handoff.

## Decisions / discoveries

This is a UI slice, no ADR needed. Starting tree is clean. Task 028 is the current
baseline: uploads are explicit per-record, not controlled by a Settings switch.
Flat means the approved mock's low-radius tonal blocks without elevation. The primary
button retains native Material shape. The decorative dumbbell icon is optional;
Settings accessibility and visual hierarchy take priority. Chooser apply/cancel behavior
is explicitly specified above rather than copying the prototype's immediate underlay update.

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

Not run yet. Before implementation: `git status --short` clean; connected test phone
currently shows Home with Pull 1 next, no active workout visible. No app state mutated.

## Handoff

Next: worker implementation via `scripts/delegate docs/tasks/029-focused-home.md`.
No known blocker. Phone checks and lead review pending. Keep task in progress until
acceptance and relevant checks are complete; list remaining manual limitations honestly.
