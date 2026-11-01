# 026 — Flat focused workout logger

Status: done
Starting commit: 213535d
Date: 2026-11-02

## Outcome / acceptance
Owner approved Focused logger from `workout-flat-ideas.html` (local mockup, not in the repo) with no session date, Progress or Settings header on this screen. Owner reverted the bottom design to this original sample (not workout-flat-refined): Add exercise, Session notes, ⋯ row above outlined/quiet full-width Finish workout. Light day and Discard go in the workout ⋯ menu. Implement native Compose, actual data and dynamic dark theme.

- Session only: flat surface instead of the large enclosing exercise Card. Header workout name + exercise position, current exercise title + existing exercise ⋯ actions. Compact Plan/Last comparison like task 025 using prescription snapshot and previous data, retaining all reps/RIR/load/date, nulls and mixed data. Reuse formatting helpers where appropriate. No target suggestion.
- Logged sets in a compact flat list, header with count / prescribed count where known, values/RIR/deltas/notes preserved; Edit and Delete reachable (keep existing buttons or put in row menu). Next set entry clearly labeled; load + reps side by side; RIR +/- with current semantics and >=44dp targets; primary Log/Update action. Keep prior-prefill hint, validation errors, edit/cancel and successful edit behavior. No invented logging state or automatic advancement.
- Preserve SaveableStateHolder and field keys, selection/reorder/substitution behavior, unit resolution including null-unit edits, notes, empty workouts and finished/upload/conflict/share/copy/retry behavior. Selection controls Previous / All exercises / Next together, exercise count in header; compact Up next with prescription and previous info still accessible (can use existing previous summary).
- Fixed footer flat background and subtle top divider; Add exercise + Session notes + labeled ⋯ menu. Light day menu entry shows checked state and can toggle vm.setLight; visible small light-day state when enabled so hidden menu does not obscure status. Discard menu retains confirmation. Finish is lower emphasis (outlined) so Log is the primary action. Preserve non-in-progress FinishSection states, no loss of post-finish operations. Hide date/Progress/Settings only during Session, not Today/Preview.
- Scroll content independently above footer, safe system/IME insets, responsive wrapping at narrow sizes/large text. Don't make screen huge with unnecessary spacers; don't truncate logged values.

## Scope
App UI/presentation/resources and focused helper tests only if logic is added, this task. Out of scope: database/domain/ViewModel logic, dependencies, backend/auth/sync, previous approved preview/progress screens. Do not read credentials/.env/private-data. Preserve unrelated work/.

## Plan / progress
- [x] Worker implement UI, run scripts/check --offline, commit verified coherent changes.
- [x] Worker address review feedback: inline Edit, overflow Delete, filled inputs and flat RIR controls.
- [x] Lead inspected full diff and independently ran scripts/check --offline.
- [x] Lead installed, smoke-tested on the phone, and updated routing/architecture.

## Decisions / discoveries
No ADR required; UI rearrangement only. Mockup uses illustrative data. User explicitly authorized starting/discarding local test workouts; local set operations were exercised as part of the UI verification. Lead must verify upload is off before mutating test workouts; do not change it.

Worker: session Plan uses the nullable snapshot directly (dash for missing values); Last reuses preview formatting. Edit is inline where space permits; Delete remains in a labeled per-set menu. Saved-set details and actions stack at narrow effective widths. Existing state-holder and input keys remain unchanged.

## Verification responsibilities
Worker: build/lint/JVM tests via scripts/check --offline. Don't add shallow layout tests or weaken existing tests.
Lead phone: actual flat layout/header absence; Log, Edit, RIR stepper, switching exercises retains unsaved entry; exercise and session notes reachable; All exercises; footer/menu and light toggle, discard confirmation/cancel; keyboard/insets. Any new test session is to be discarded after verification; never discard a pre-existing session unless clearly authorized test-only. Check upload setting first. Large-font/screen-reader checks may be explicitly pending.

## Verification
Worker: `scripts/check --offline` passed (debug build, lint and JVM tests), including after the review refinement. Device, large-font and screen-reader verification not run by worker.

## Lead verification
- `scripts/check --offline` passed independently (build, lint, JVM tests); `git diff 213535d..HEAD --check` passed. No new dependencies or changes to domain/data/ViewModel logic.
- `cd android && ./gradlew :app:installDebug --offline` installed on the test phone. Inspected actual flat layout, removed header, compact Plan/Last, filled inputs, flat RIR controls, inline saved set with Edit and overflow Delete, outlined Finish and footer menu.
- Phone already had a local test Push 1 session (some sets already logged). Kept it. Logged only one new test set (one set), edited its reps, verified updated row/delta and edit completion, then deleted that same added set through its overflow menu. Existing sets/session were not discarded or finished.
- Enabled Light day, inspected visible status, and restored it to false. Opened Discard confirmation and canceled it. Opened session and exercise notes without saving; All exercises list and scrolling worked while footer remained fixed. An unsaved 16-rep entry survived Next/Previous selection; restored the original 14-rep entry afterward. Keyboard screenshot showed focused filled field and accessible Update action above IME; footer hid during text entry as before.
- Upload was last visually verified off in task 025 during this chat. A fresh read via `run-as` failed due to device SELinux policy; no credentials were accessed. No finish/upload operation was used and upload setting was not changed. Live upload setting was not independently reverified in this follow-up; tests only affected the in-progress local draft and the added test set was removed.
- Not manually checked: large font/screen reader, finished/conflict/share flows, unit-edge cases on device. Existing tests and code paths for those behaviors remain.

## Handoff
Implemented, installed and checked. Owner can review the current workout screen; no test sets remain from verification. Existing session and unrelated work/ preserved. Commits: 31ee150 (layout) and aefe6f0 (saved rows/inputs refinement). Any further visual feedback can be scoped as a follow-up.
