# 003 — Pull on open, push finished sessions

Status: done
Updated: 2026-11-01

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
- Share / Copy from the finish overview produces the ADR §7 text, including `ref`.
- When a shared, unpushed session is saved through `save_session` (simulating ChatGPT),
  the phone adopts the server copy without a prompt if it hasn't been edited since
  sharing. If it has been edited, the phone raises a conflict. Either way, exactly one
  record exists.

## Scope and plan
- Room: add a session table with status and revisions, per the ADR. Move task 001's
  per-set revisions to the session. No migration is needed (building phase).
- Supabase: the `push_session` function with the ADR's three rules. Use pgTAP or
  SQL-level tests for replay, conflict, and apply, run as an authenticated user.
- Pull: the minimum cache needed for one synthetic program workout.
- Retry on app open and when the network returns. Keep the mechanism simple.
- A minimal finish/overview screen with Share / Copy, and a minimal conflict prompt.
- `save_session(payload jsonb)` for ChatGPT, plus a short ChatGPT-instructions snippet
  in `docs/` (ADR §6). Verify what the connector can actually access before relying on it.

Defer: real ChatGPT integration (a separate task: verify connectivity per PRD §4),
program versions and rotation, dashboards, multi-device drafts.

## Decisions and discoveries
The design is in ADR 001. Record any deviation there, as a superseding note.
- Initial inspection: task 002 provides owner-scoped sessions and sets with revision
  triggers, but Android still stores revisions per set and the cloud upload is a test
  scaffold. The server increments revisions for each child write, so RPC responses must
  read the final revision after replacing children.
- Replay handling needs care when another writer has subsequently changed the session:
  acknowledging an old phone revision must not silently mark a newer server version as
  the base of a local edit. Cover this interleaving in server and client tests.

## Verification (2026-11-01)
- Dev migrations `20261031200000_session_functions.sql` and
  `20261031203000_push_receipts.sql` applied to `<dev-project-ref>`.
  `scripts/db test`: 39/39 rollback-only pgTAP checks passed, including replay,
  external-write conflict, keep mine, shared ref, validation, and cross-user denial.
- `scripts/check --offline` passed (debug build, lint, and unit tests including the
  ADR share text). `:data:connectedDebugAndroidTest --offline`: 8/8 device tests
  passed, including late acknowledgements, shared adoption/conflict, and revision
  preservation on cache refresh.
- Test phone: existing Room v1 synthetic set survived migration to v2; phone uploaded
  session #24 and CLI read back load, reps, RIR, and both notes. A same-user CLI edit
  produced a conflict; both Keep mine and Take theirs were exercised. With Wi-Fi off,
  an edit showed pending, and after restoring Wi-Fi it appeared in the CLI without
  reopening the app. Wi-Fi was restored to its original enabled state.
- Copy was pasted into an unsaved field and Share showed the Android share-sheet preview;
  both included ref, set and session notes. No message was sent.
- `SharedSessionIntegrationTest` ran on the phone against the isolated test2 dev
  account: a shared, never-pushed session saved through `save_session` was adopted
  unchanged; an edit after sharing produced a conflict. Each case had exactly one
  server record; cleanup left test2 with no sessions. The test is opt-in and skips
  without runtime-supplied credentials.
- ChatGPT connector access and its identity remain unverified; `save_session` requires
  an authenticated user JWT. This is the separately deferred integration task.

## Handoff
Done for the synthetic workout slice. The separate ChatGPT integration must verify
its authenticated identity and project scope; see [instructions](../chatgpt-session-instructions.md).
Before real data, review the account-binding UX and synthetic-dev receipt grants.
Program caching beyond the current synthetic workout is deferred until those tables exist.
