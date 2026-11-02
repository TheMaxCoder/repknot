# 028 — Explicit per-workout upload

Status: in progress
Starting commit: c0e69c5
Interrupts: [027](027-workout-timing.md) (paused at the watch investigation; its handoff is unchanged)

## Outcome / acceptance
While the owner is still testing on the phone, nothing reaches Supabase without an explicit tap.
- Finish never uploads. The finished screen offers **Upload**, **Resume** and **Discard**
  (Discard asks for confirmation). Done is offered only once Upload was tapped.
- Upload marks that one session (`uploadRequested`) and pushes it. If offline, later automatic
  syncs (sign-in, network) retry it. Unmarked sessions are never pushed by any sync.
- A session that was uploaded once (acknowledged or downloaded from the server) stays marked:
  edits to it sync as before.
- Resume (never-uploaded only) clears the mark. Discard works for in-progress or finished
  sessions that were never uploaded and not marked.
- Check-ins follow the same rule: a new check-in is local until its **Upload** is tapped; a
  local, unmarked check-in can be deleted.
- The global Settings switch "Upload workouts" is removed.
- Room v7 → v8 migration keeps all data. Backfill: every session not `in_progress` and every
  check-in is marked (preserves the old behavior: upload was on, so they were already meant to
  upload). An in-progress workout stays unmarked.

## Scope
- `data`: `SessionRow.uploadRequested`, `CheckInRow.uploadRequested`, DAO queries, `SessionSync`
  (drop the `uploadEnabled` parameter), `WorkoutDatabase` v8 + `MIGRATION_7_8`, schema `8.json`.
- `app`: `WorkoutViewModel` (finish/upload/discard/resume), `FinishSection`, body screen
  check-in actions, `AppSettings`/Settings screen (switch removed), strings.
- Tests: migration 7→8, discard/upload DAO rules, integration tests updated.
- Out of scope: server changes; the web companion; deleting existing server rows.

## Plan / progress
- [ ] Data layer + migration + tests
- [ ] App wiring + UI
- [ ] `scripts/check`, data androidTests on device, install and phone-check

## Decisions / discoveries
- Kept with the lead (sync logic). Migration instead of destructive reset: discussed with the
  owner 2026-11-02 — the phone holds real workouts; old migrations can be pruned later because
  there is a single device.
- `run-as` is blocked on the owner's phone (MIUI SELinux), so the local DB could not be
  inspected. The server has only one Android session (#405) and no stray uploads, so no
  finished-but-unuploaded sessions are expected; the backfill marks any that exist anyway.
- Session `number` gaps (#153 → #405) come from rolled-back pgTAP tests consuming the identity
  sequence; harmless.

## Verification
(pending)

## Handoff
Next action: implement the data layer.
