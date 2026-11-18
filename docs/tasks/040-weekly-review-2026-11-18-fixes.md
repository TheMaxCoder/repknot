# 040 — Fixes from the 2026-11-18 weekly review

Status: in progress
Starting commit: 3de2859
Source: [weekly review 2026-11-18](../reviews/2026-11-18.md), findings 1–4 (each checked against
the code by the lead before filing).

## Outcome / acceptance
Each item below is fixed with a synthetic test that fails before the fix, or explicitly dismissed
here with a reason after an owner decision.

1. **Check-in Delete vs. upload race.** `SessionSync` reads `checkIns.pending()` and then upserts
   each row; a Delete tapped while that runs removes the local row, the upsert still lands, and
   the next refresh restores it as uploaded (no Delete). Since check-ins upload on save, the
   window is right after saving, when a correction is likeliest. Direction: Delete of a pending
   check-in also deletes the server row by id when signed in (RLS allows the owner's own rows;
   the integration test already does it), so a raced upload is undone; or re-check existence
   before upsert and delete after. Test: delete between `pending()` and `uploaded()`.
2. **Reminder tap can land in the logger.** `AppNavigation`: the reminder effect sets
   `focused = false` and opens Body, but when a current workout finishes loading afterwards the
   session effect (`session.row.id != previousId`) sets `focused = true`. Only when a workout is
   open at the tap. Direction: let the reminder suppress the next automatic focus (saveable
   flag) instead of racing it.
3. **Dip recovery rule is inconsistent (task 037).** 037's acceptance says a unit/timed mismatch
   is "evaluated, not below"; its outcome says a mismatch is "not evaluated". The code counts a
   mismatch as evaluated only when the latest top load is higher (`dipStatuses` fallback), so a
   higher-load timed-vs-reps follow-up resolves a dip. Pick one rule (lead proposal: mismatch is
   never evaluated; the higher-load fallback requires same unit and timed mode), fix the code
   and the 037 wording, add the higher-load mismatch test.
4. **Unit change rewrites past dips.** `sessionDips` picks each exercise's unit from its latest
   exposure and filters all history by it, so switching an exercise to another unit drops
   earlier dips (History labels, alerts). Direction: compare exposures per (exercise, unit)
   while walking sessions in order; follow-ups in `dipStatuses` use the dip's own unit. Test:
   appending a different-unit session keeps existing dips.

## Scope
`data` (CheckInStore/SessionSync for 1), `app` (AppNavigation for 2), `domain` (Progress.kt for
3–4) with tests. No schema change expected. Items are independent; commit each separately.

## Plan / progress
- [x] 1 — `CheckInUploads` serializes each row's upload with Delete (per-row lock, re-read before
  upsert); Delete also removes a server copy left by an unacknowledged upload.
- [ ] 2 · [ ] 3 · [ ] 4

## Decisions / discoveries
- Item 1 touches sync logic and item 3 changes a 037 rule: flag both under `Review:`.
- Item 1: when Delete loses the race (the row finished uploading first) nothing is deleted and the
  row shows as uploaded; deleting uploaded check-ins stays out of scope (they are add-only).
- Item 3 rule confirmed by the owner (2026-11-18): a mismatch (unit or timed mode) is never
  evaluated, at any load.

## Verification
- Item 1: `CheckInUploadsTest` (3 tests) fails on the old upload loop (deleted row still sent;
  delete not serialized) and passes with the fix. `:data:connectedDebugAndroidTest` on the emulator
  → 26 tests, BUILD SUCCESSFUL. `scripts/test-sync-integration` (test2, dev) → OK (2 tests),
  including the new lost-acknowledgement Delete case. `scripts/check` → BUILD SUCCESSFUL.

## Handoff
Next action: item 2 (reminder tap vs. workout focus in `AppNavigation`).
