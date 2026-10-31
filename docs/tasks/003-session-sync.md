# 003 — Pull on open, push finished sessions

Status: ready
Updated: 2026-10-31

## Outcome and acceptance
Implement [ADR 001](../decisions/001-sync-model.md) end to end against the dev backend (002).

- Opening the app refreshes the cached server data in one transaction. When the app is
  offline or the fetch fails, the previous cache stays. A pull never touches unpushed
  local sessions.
- Finishing a session shows an overview. Confirming pushes it. If the phone is offline,
  the screen says it will upload, and the upload happens automatically after reconnecting
  or on the next app open.
- Lost-response retry: the server saved the session but the phone missed the
  acknowledgement. The retry leaves exactly one record and the phone marks it pushed.
- An edit made during an upload stays unpushed and goes out with the next push.
- If another writer (simulated by a CLI signed in as the same user) changes the session
  after the phone's base revision, the push reports a conflict and overwrites nothing.
  "Keep mine" and "take theirs" both work.
- Android and the CLI read back the same session, including sets and notes.

## Scope and plan
- Room: add a session table with status and revisions, per the ADR. Move task 001's
  per-set revisions to the session. No migration is needed (building phase).
- Supabase: the `push_session` function with the ADR's three rules. Use pgTAP or
  SQL-level tests for replay, conflict, and apply, run as an authenticated user.
- Pull: the minimum cache needed for one synthetic program workout.
- Retry on app open and when the network returns. Keep the mechanism simple.
- A minimal finish/overview screen and a minimal conflict prompt.

Defer: real ChatGPT integration (a separate task: verify connectivity per PRD §4),
program versions and rotation, dashboards, multi-device drafts.

## Decisions and discoveries
The design is in ADR 001. Record any deviation there, as a superseding note.

## Verification
Not started. Record server-test, device, and CLI evidence here.

## Handoff
Depends on 001 (done) and 002. Check the actual state of both before starting.
