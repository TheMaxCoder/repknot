# 030 — Bottom navigation and recorded history

Status: done
Starting commit: 4ea3f5f7013cee8a0d6cee6b35cf51ff4e6eac86
Date: 2026-11-02

## Outcome and acceptance

Implement the approved `repknot-navigation.html` (local mockup, not in the repo)
in native Compose, using actual local data, flat 8 dp tonal cards, dynamic dark Material
colors and safe insets. History's mock values are illustrative, not data to copy. Keep
the existing detailed Progress/Body presentation; the mock simplifies those only to
demonstrate navigation. Decisions: [ADR 004](../decisions/004-app-navigation-and-history.md).

### Navigation (lead phone checks; pure transitions also worker JVM tests)

- Bottom NavigationBar: Home, History, Progress, with icons and labels, selected state.
  Remove the old top Progress button. Settings gear is available on each root, with
  an accessible name; no app-name header. Home's From Progress link switches to Progress.
- Each tab has its own saveable detail stack and view state: tab switching retains detail,
  History search/subtab/list scroll, Home selection/scroll. Reselect active tab pops its
  details to its root. Back/visible detail Back pops the origin's stack, never hardcodes
  Progress. At another root Back selects Home; at Home root leave system Back unhandled.
  Settings opens above its origin and returns there. Body/check-in features remain intact.
- The same exercise-history screen opens from Progress, History/Exercises, or a completed
  workout's exercise. Each dated history entry can open that session's read-only detail;
  Back returns to the originating exercise detail. Routes use stable IDs, not names/dates.
  Deleted/missing records show an understandable empty state with Back, not stale data.
- Keep bottom navigation during browsing/details/Settings and workout preview. The existing
  preview Start action must sit above it safely. Hide navigation only inside the focused
  current logger/finish summary. System Back there returns to Home without finishing,
  discarding, uploading or clearing current ID. Home shows a Resume workout card for an
  in-progress current session; for a finished/conflicted current session offer View workout
  summary. Opening it restores the existing UI/actions. Do not offer another Start while
  a current session exists. After a new Start, enter the focused logger. Keep selected
  exercise and unsaved logger inputs across leaving/resuming via SaveableStateHolder.
  Existing rest alerts remain scheduled while browsing; no new timer behavior is requested.
  No changes to task 028's Done gate or upload/finish/discard semantics.

### Read-only History (worker JVM mapping tests; lead phone interaction/layout)

- History root has Workouts / Exercises tabs. Workouts list completed cached sessions,
  grouped by month, newest first, with actual date/title, exercise count, and status.
  Completion predicate: statuses finished, pushed, conflict, conflict_deleted; exclude
  in_progress and unknown statuses even if timestamps exist. Include finished phone-only
  and light sessions, including zero-set/empty workouts; imports may lack timing.
  Order by performedOn descending, finishedAt (null as Long.MIN_VALUE) descending,
  number (null as Long.MAX_VALUE) descending, ID ascending for deterministic ties.
  Dates display year where needed; month groups always include year. Do not derive dates
  from timestamps or infer duration. Optional duration only when both times are present
  and finish >= start. Title fallback is “Workout” if blank.
- Status text: pushed = Uploaded; finished + uploadRequested = Waiting to upload;
  other finished = On this phone; conflict = Sync conflict;
  conflict_deleted = Deleted on server · local copy retained. These labels never write
  records or request sync. Preserve/identify light-day context.
- Workout detail displays ALL exercise snapshots and actual recorded sets in order
  (position then ID for ties), session/exercise/set notes, RIR, duration and load/unit
  without dropping partial/unknown-unit records or fraction precision. Use existing
  formatting where accurate; app-local fallback must label unit unknown when a load
  has no unit and show an explicit missing-values marker for an empty recorded set.
  Empty workouts/exercises are understandable. Tapping an exercise name opens its history.
  Read-only: no Upload/Delete/Edit/Resume buttons for arbitrary historical rows.
- Exercises tab lists distinct exercise IDs with at least one recorded set in completed
  sessions (a partial imported set still counts; unused prescriptions do not). Latest
  performed date/name from those snapshots; sort date descending then name then ID.
  Case-insensitive name search, trim surrounding query whitespace; no-results/loading/
  error/empty states. Search query and list/subtab state survive detail/tab/config changes.
- Shared exercise detail preserves current Progress status/reason/flags/best markers
  when available, while listing complete recorded sessions for that ID from History
  snapshots, newest first, with actual units/sets/notes/light context and links to session
  IDs. Include older unit variations and unknown units; do not alter progress algorithms.
  If no Progress item exists, use the historical name and neutral history/no-data copy.
  Never rely on an exposure's day or display name as a unique key.

## Scope and file pointers

App: MainActivity/settings extraction if useful; new small navigation state/model;
new HistoryViewModel/presentation mapping and ui/HistoryScreen; ui/WorkoutScreen and
HomeScreen for shell/Resume/back wiring; ui/ProgressScreen only for shared history
content/callbacks and root chrome/insets (retain existing body/group functionality).
WorkoutViewModel may expose read-only current state/presentation metadata or preview
close/start routing callbacks as needed, but DO NOT change persistence/upload actions.
App resources/local drawable vectors and meaningful app JVM tests are allowed.
Data: SessionStore.kt may gain an additive read-only @Transaction all-snapshots method
using existing allSessions/allExercises/allSets, to avoid three inconsistent reads.
No schema/migration/version changes. UI reads through ViewModels, not direct DAOs.
Use the existing shared ProgressViewModel. Preserve errors instead of a false empty list.

Out of scope: new dependencies/build tooling, domain calculations, database writes,
backend/auth/sync/conflicts/revisions, upload intent changes, arbitrary history editing,
library/catalog of never-performed exercises, new filters/charts, other screen redesigns,
rest-alert behavior, delegation rules. Do not read .env/credentials/private-data/exports.
Do not use adb/install on the owner's phone as worker. Preserve unrelated changes.

## Plan / progress

Worker implementation, lead review, offline checks and scoped phone checks are complete.

- [x] Worker implements/test pure history mappings and navigation transitions; run
  scripts/check --offline and commit this coherent step before wiring UI if feasible.
- [x] Worker implements native shell/history/shared details and safe current-workout
  routing, runs scripts/check --offline, inspects staged diff and commits verified UI.
- [x] Lead reviews full diff, independently reruns checks and handles review findings
  through one bounded worker feedback retry, then fixes locally if necessary.
- [x] Lead installs and tests navigation/Back/search/details/state retention plus
  narrow/large text. Verify default and configured phone settings are restored.
- [x] Lead updates architecture/routing, verification/handoff; commit and finish.

## Decisions / discoveries

Tree was clean at worker entry (fec27a0; task starting commit 4ea3f5f). The first worker
checkpoint added pure mappings/transitions, then the resumed pass added transactional
reads, expanded tests and full UI integration. Task 029's approved design and installed
Home are the baseline. Task 027's
paused watch handoff and task 028's pending owner upload-flow check are unchanged.
Current SessionDao already exposes read-only rows and per-ID snapshots. Progress's
lineage excludes unknown units and earlier unit variations, so use raw snapshots as
the complete archive. A recorded set, even partial, is evidence for the directory;
an exercise prescribed but never logged is not. This does not change analysis rules.
Navigation/library choice is recorded by the lead, not left to the worker to decide.
Use conservative native presentation choices without stopping over routine spacing,
icon or helper naming. Expose small presentation metadata if needed within scope.
The worker used a saveable per-tab stack with depth-qualified detail state, a separate
focused-logger flag, and a transactionally reloaded archive triggered by all three tables.
Home uses a current-workout card while the focused logger is closed. Review the shell's
insets, focus transitions, state retention, archive presentation, and rest-alert continuity
on device; these interaction/layout behaviors have not been device-verified.

## Verification responsibilities

Worker: scripts/check --offline and git diff --check for every coherent commit.
JVM tests must exercise completion filtering, ordering/ties, local/upload/conflict labels,
partial/mixed-unit records, repeated exercise instances, empty and directory/search cases;
navigation tests exercise origin Back, per-tab stacks, reselection and root Back.
No shallow mirror/layout tests, no weakened tests. New read-only DAO method can reuse
existing Room infrastructure; full connected DB tests are unnecessary for unchanged schema.
Lead: independently run scripts/check --offline; installDebug --offline; phone navigation
Home/History Workouts/detail/exercise/session/Back, History Exercises/search/Back and
tab switch retention, Progress exercise/Back, Settings from roots/details, Body; verify
Home selection and preview still work and nav is not behind system bars/IME. Use temporary
font/display changes with restoration. Never upload/finish/discard a pre-existing workout.
Current-session route/unsaved-entry behavior may use a clearly newly created empty local
test session, leave owner data intact and request no upload. Lead must record any such
test creation and clean up only that exact test record through existing Discard confirmation.

## Verification

Lead phone milestone (test phone): installed the reviewed build with
`cd android && ./gradlew :app:installDebug --offline` successfully. Home has the three
bottom destinations and Settings; real completed workouts, including a zero-exercise
imported rest day, opened read-only. Workout → exercise → Settings and Back restored
the exact originating flow. Exercise search, History subtab, independent workout list
position and per-tab detail stack retention passed. Progress's exercise link opened
the shared complete history and Back returned to Progress. Body/Settings origin passed.
Preview survived a tab switch, and reselecting Home closed it. The selected Material
navigation item reports clickable=false in the UI dump even though pointer reselection
works; the initial automation missed it, then a native coordinate tap verified the
transition. The helper now recognizes selected navigation nodes.

After verifying no current session on Home, lead created one NEW empty local Pull 1
solely for unsaved-input verification. Entered 17 reps without Log, left the logger via
Back, browsed History, returned Home and resumed: selected exercise and unsaved 17 were
retained with zero saved sets; no bottom navigation inside the logger. The same input
survived a temporary font scale of 1.5. Restored 1.0 and discarded only that newly created
test through its existing Discard confirmation. Home returned to the original suggested
Pull 1. No Log, Finish, Upload or edits to pre-existing sessions/check-ins were used.
The external verification marker records started=true, cleaned=true. Rest-alarm continuity
is code-reviewed (scheduler unchanged and no cancel-on-navigation disposal), not newly
phone-tested with a logged set or alarm delivery.

Lead review milestone: the worker's first pass stopped after preliminary models without
a blocker; one feedback retry completed the remaining UI. This is a one-off early-stop
miss, recorded here without changing worker rules. Lead reviewed the full mapping,
read-only DAO additions, shared details and routing. Review found History Workouts and
Exercises shared one scroll offset and eagerly composed the full archive. Lead replaced
the root lists with separate saveable lazy-list states and fixed header/tabs/search,
keeping route-level state providers. Lead also consumes outer Scaffold insets so nested
screens do not double-pad system bars, hides the bottom bar while the keyboard is open,
and improves complete raw-set formatting with focused edge-case tests. Exercises that
were prescribed but not performed retain a historical title and neutral no-data detail.
Initial lead check hit a cross-module smart-cast compile error in the new formatter;
replaced direct nullable property reads with a local value before rechecking.
The next run found non-observable default-locale access in a new Compose date label;
aligned it with the app's existing English date formatting. Lead
`scripts/check --offline` then passed (106 tasks, 16 executed); no lint suppression,
baseline, or weakened test was added. Phone verification follows this reviewed build.

Resumed worker: `scripts/check --offline` and `git diff --check` passed after transactional archive read and expanded directory tests. UI integration initially failed compilation due to a cross-module smart cast; fixed and subsequent `scripts/check --offline` passed. Final integrated check and diff check passed; an additional test confirms light/empty workouts and that timestamps alone do not imply completion. No device operations.

Worker: `scripts/check --offline` passed after correcting a navigation test assertion;
initial run failed that assertion. `git diff --check` passed. No device operations run.
The new JVM tests cover basic completion/order/status, partial and mixed-unit preservation,
directory search and navigation transitions; additional cases and UI verification remain.

## Final lead verification and handoff

- Complete worker diff reviewed, including all read-only DAO additions. Lead build/lint/
  JVM tests passed via `scripts/check --offline` after the review fixes (106 tasks,
  16 executed). Gradle's existing deprecation warning remains. Installation passed
  via `cd android && ./gradlew :app:installDebug --offline` on the test phone (API 36).
- Phone checks above passed, followed by `verify_navigation_narrow_030.py`: temporary
  720×1600 display (about 327 dp at existing density) and font scale 1.5; three-tab
  navigation, no-results search, keyboard visibility/insets, long History cards,
  Settings origin, and selecting a long Home workout through the native chooser.
  Inspected screenshots for wrapping and safe controls. A preliminary script assumed
  scrolling three gestures would return to the newest workout; the retained list was
  correctly still in October. Removed that unrelated position assumption and reran
  the complete narrow test successfully. No app fix was needed for that test issue.
- Restored physical display 1080×2400, density override 352 unchanged, font scale 1.0.
  Cleared the test exercise search and restored suggested Pull 1 on Home. No temporary
  workout remains. No Repknot crash-buffer entries were found. Verification scripts,
  reports, and screenshots are outside Git in the approved mockup's thread directory;
  `navigation-installed-home-final.png` shows the restored installed Home.
- Updated architecture and routing docs. Also reconciled the obsolete global-upload
  descriptions with already-shipped task 028 (documentation only): uploads are explicit
  per record, not a Settings switch. No sync, auth, database-write or migration behavior
  changed in this task. No new dependencies, test suppressions, or weakened tests.
- Not phone-tested: TalkBack, tablet/landscape, network/error/conflict/missing-record
  fixtures, or new rest-alert delivery/continuity with a logged set. Raw partial/mixed
  units, empty/light records and status/order rules have JVM coverage; rest scheduling
  is unchanged and was source-reviewed. No retained real data was modified for fixtures.
- Commits: fec27a0 plan/ADR; a142b03 models; d8579a9 transactional read and directory
  tests; 576d2da UI/navigation; 103964c empty/light coverage; 0ca00fd lead state/insets/
  formatting refinements; final documentation/verification committed separately.

Task complete and installed, no known unfinished implementation or unrelated drift.
Next: owner reviews Home/History/Progress on the phone and gives screen-specific feedback.
Task 027's watch investigation and task 028's owner upload-flow check remain separate.

## Owner follow-up — compact exercise history

The owner reported that the new exercise history was too verbose: the earlier screen
was a simple chronological list with best-session markers. Lead restores that presentation
directly as a scoped follow-up. Keep status/reason/flags and best markers, dates, compact
performance values, RIR and light markers; remove cards, repeated names, session titles,
session/exercise/set notes from the exercise list. Rows still open the full workout detail,
where all notes and sets remain. Preserve earlier unit variations and partial recorded
values in compact fallback rows. No analysis or data changes. Added a regression test
covering note exclusion, previous-summary formatting, RIR and unknown-unit precision.
Starting commit: 2faf05f. Done 2026-11-03.
Lead review (resumed after the session stopped mid-step): the row reuses the pre-030
`exposureLine` format (`MMM d · previousSummary · light`, no year, as before); only sessions
with recorded sets for the ID are listed, so no "0 sets" rows. Restored the old header
typography (headlineSmall name, labelLarge status, tertiary flags) and dropped the now-unused
exercise filter from `ExerciseRecords`. Rows keep a 48 dp touch target, so they are spaced
more than the old non-clickable list.
Verified: `scripts/check --offline` → BUILD SUCCESSFUL (HistoryMappingTest 7/7, incl. the
new compact-row test). `./gradlew --offline :app:installDebug` on the test phone (first attempt
failed while adb reconnected; retry installed). Phone: History → Exercises → Lateral raise
shows compact rows with status, reason, `best` marker and mixed-load rows (Nov 2 row matches
#405); tapping a row opened Push 1 detail with all sets; Back returned to the exercise. No
crash-buffer entries. Not checked: the Progress-tab entry point to this screen (same composable).
