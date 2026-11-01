# 025 — Compact workout preview

Status: in progress
Starting commit: a7ff9ee

## Outcome / acceptance
Owner approved the compact comparison design in chat (2026-11-01): `workout-preview-comparison.html` (local mockup, not in the repo). Implement native Compose using real data. All exercises in program order, compact numbered headings, aligned Plan/Last rows with columns Sets × reps, RIR, Load, and last-session date at far right. No program name, redundant standalone sets/reps/RIR legend, target suggestion or bottom Back button. Keep Next workout indication when suggested, workout name and exercise count, Progress and Settings actions. System back closes the preview. Start workout stays fixed at bottom with safe insets while all exercises scroll above it.

## Scope
App UI, pure presentation helper and focused tests, resources, this task. Remove TargetHint presentation from preview, current exercise and Up next per explicit owner request. Keep existing target computation/data/tests to avoid expanding into domain changes. Other session UI/behavior stays intact. Preserve unrelated untracked work/.
Out of scope: domain/data calculations, schema, backend, auth/sync, new dependencies, changes to Today selection screen. Do not read credentials, exports or .env.

## Rules and verification responsibilities
Worker JVM tests: formatting using actual Prescription/Prefill/PastSet. Preserve all per-set reps, RIR (including missing represented by dash), load and unit, bodyweight, timed seconds, and nulls without inventing values. Plan shows count × rep range, RIR range and actual configured load/unit (kg/lb/plates/bodyweight); null rep/RIR bounds show available bounds or dash. Last shows comma-separated per-set reps/times and RIR in matching positions; shared load may be one value only when identical; mixed loads/units must stay aligned per set (multiple Last rows acceptable for this edge case). No previous data: show No previous session. Missing date: dash; compact age today/1d ago/2d ago through 6d ago, older date MMM d (include year if different year), future use date. No truncating logged values or silent omission of sets. Use wrapping or responsive row layout on narrow screens/large fonts instead of shrinking text excessively. Typical case matches compact table and dynamic dark theme; font/bodySmall as appropriate, no fixed card heights.
Lead device: verify full list scroll, Start remains visible, no overlap system bars, Start creates local session, system back from preview returns selection, Targets absent in session/Up next. Owner explicitly authorized starting/discarding local test workouts. Never change upload setting or remote data.

## Plan / progress
- [x] Implement preview layout and formatting for the existing Prefill; remove target presentation.
- [x] Worker run scripts/check --offline and commit verified step(s).
- [x] Apply review clarification: preserve existing prefill/history selection; acceptance applies to the Prefill passed to the presentation helper.
- [x] Adjust responsive threshold and run checks.
- [ ] Lead inspect diff and re-run checks, install and device-check.

## Decisions / discoveries
- Design only was iterated until explicit implementation approval; full previous performance including RIR and units must survive compaction.
- Existing scripts/delegate workflow applies. No ADR required for this presentation change.
- Review clarification: acceptance for preserving all sets and mixed units applies to the Prefill passed to the presentation helper, which already preserves them. Existing `prefill()` history selection and unit filtering remain unchanged; this is not a blocker.
- Use effective width (available dp divided by font scale) below 250 for the stacked layout, retaining wrapping table cells at ordinary phone widths.

## Verification
- `scripts/check --offline` passed twice: debug build, lint and available JVM tests, including new preview formatting tests. Gradle reported deprecation warnings.
- Review fix: `scripts/check --offline` passed (debug build, lint and available JVM tests; Gradle deprecation warning). Device/visual checks and lead diff review remain pending. Implementation commit: `c3894d7`.
- Commit message correction: a shell line break dropped the intended `Not verified` and `Review` paragraphs from `c3894d7`; they are recorded in the following handoff commit and above/below.

## Handoff
Next: lead inspect the diff, re-run checks, install and perform the lead device checks (scroll, fixed Start/insets, local start/discard, system back, target absence, ordinary/narrow widths and large font). Lead updates status/architecture. No device actions performed by worker. Preserve unrelated `work/` and lead changes to `docs/status.md`.
