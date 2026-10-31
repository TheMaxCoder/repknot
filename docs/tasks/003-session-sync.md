# 003 — Reliable session synchronization

Status: ready
Updated: 2026-10-31

## Outcome and acceptance
Connect local logging (001) to authenticated dev storage (002). A lost acknowledgement
and retry cannot duplicate data. Stale revisions are detected, and edits during upload
remain pending until saved. Android/CLI read-back agrees with the recorded session.

## Scope and plan
- Decide and record a short sync ADR; evaluate whole-session transactional upload.
- Implement stable mutation identity and expected revision semantics.
- Define removed sets/exercises and retry acknowledgement behavior.
- Verify lost-response retry, stale writes, and edits during an upload.
- Verify offline/reconnect on device, then update architecture/status.

Keep single-device editing of active sessions. No general collaboration framework.
This completes the original thin engineering slice, not the broader PRD first milestone.

## Decisions and discoveries
A replay must return its prior successful result instead of becoming a revision conflict.
Reuse a mutation ID only for the same payload. Never clear newer local edits on ack.

## Verification
Not started. Record integration test and device evidence here.

## Handoff
Depends on tasks 001 and 002. Inspect their actual state before starting implementation.
