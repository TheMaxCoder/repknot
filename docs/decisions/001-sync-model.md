# 001 — Sync model: server cache, local drafts, push on finish

Status: accepted  
Date: 2026-10-31

## Context

Supabase holds the canonical data. Android must keep working offline in the gym.
More than one client writes data: Android, the web companion, ChatGPT acting at the
user's request (PRD §4), and coding CLIs. There is one user, and one session is active
at a time on the phone. We need:

- no lost drafts;
- no duplicates from retries;
- no silent overwrite of one writer's change by another.

## Decision

### 1. Who owns which local data

- **Server-owned cache:** programs, exercises, last performance per exercise, and
  sessions already pushed.
  - Pulled when the app opens.
  - Replaced in one Room transaction, and only after the whole fetch has succeeded.
  - A failed or offline fetch keeps the previous cache.
- **Phone-owned:** in-progress sessions, and finished sessions with changes not yet
  pushed. A pull never overwrites these.

### 2. Session lifecycle

`in_progress → finished → pushed`

- Every edit is saved to Room immediately.
- An in-progress session exists only on the phone. Supabase first sees it when it is
  finished and pushed.
- Starting a session snapshots its prescription, so a cache refresh never changes an
  active session.

### 3. IDs

- The phone generates a UUID when it creates a session, a session exercise, or a set.
  The UUID never changes, and it is the key that makes retries safe.
- `performed_on` (date) is an ordinary column and is not unique. Several sessions can
  share a date: restarts, two sessions in a day, workouts that cross midnight, imported
  history, or sessions ChatGPT creates.
- "You already have a workout today" is a prompt in the UI, not a database constraint.

### 4. Push

- Finishing a session shows an overview. Confirming starts a push straight away.
- Until a push is acknowledged, the app retries automatically: on app open, and when
  the network returns. The UI shows "saved on phone · will upload".
- The push goes through one server function,
  `push_session(payload, local_revision, base_revision)`. In a single transaction it
  writes the session and replaces its children with the snapshot.

### 5. Revisions (conflict detection between writers)

**On the server, per session:**

- `revision` increases on every change to the session or any of its children, whoever
  makes it. A trigger does the increment, so no write path can skip it.
- `client_revision` is the last local revision applied from the phone.
- `source` records who made the last write: android, web, chatgpt, or cli.

**On the phone, per session:**

- `local_revision` increases by 1 on every local edit.
- `pushed_local_revision` is the highest local revision the server has acknowledged.
- `base_revision` is the server revision the local copy is based on. It is null if the
  session has never been pushed.
- The session has unpushed changes whenever `local_revision > pushed_local_revision`.

**What `push_session` does, in order:**

1. If `client_revision >= local_revision`, the server already applied this push (a retry
   after the acknowledgement was lost). It returns the current revision and writes nothing.
2. If the server's `revision` differs from `base_revision`, another writer changed the
   session. That is a **conflict**: the server returns its copy and writes nothing.
3. Otherwise it applies the snapshot, increments `revision`, sets
   `client_revision = local_revision`, and returns the new revision.

**When the phone gets an acknowledgement:**

- It sets `pushed_local_revision = max(pushed_local_revision, acked local_revision)` and
  `base_revision` to the returned revision.
- Any edit made during the upload still counts as unpushed and goes out with the next push.

**On a conflict (V0):**

- The phone keeps its copy and marks the session `conflict`.
- The user chooses **keep mine** (re-push using the server's revision as the base) or
  **take theirs** (discard the local copy).
- Nothing is merged automatically.

### 6. Other writers

- Web, ChatGPT, and CLI clients write as the signed-in user, through RLS-protected tables
  or functions. They never use a service-role key.
- ChatGPT gets narrowly scoped actions authenticated as the user. Whether it can connect
  at all still has to be verified (PRD §4).
- Changes ChatGPT makes at the user's request are normal writes. Autonomous AI
  suggestions still need the user's approval before they change programs or logs (PRD §5).

## Alternatives considered

- **Continuous row-level sync with a per-set outbox:** much more machinery, and not
  needed while drafts live only on the phone.
- **An off-the-shelf sync engine (e.g. PowerSync):** adds an external service and
  dependency. Revisit if several devices need to edit drafts.
- **Last write wins, with no revisions:** a re-push from the phone would silently erase
  changes made by ChatGPT or the web app.
- **The date as session ID:** a second session on the same day would overwrite the first.
- **Manual push only:** a session finished offline, or an app killed after finishing,
  would leave data stranded on the phone.

## Consequences

- **Supabase schema (task 002):** the first migration includes `revision`,
  `client_revision`, `performed_on`, and `source`, plus the revision trigger.
- **Room:** the session table gets `status`, `local_revision`, `pushed_local_revision`,
  and `base_revision`. Task 001's per-set revision fields move to the session level when
  the session table is added.
- **Today's workout:** rotation is a pure function in `domain`. Its inputs are server
  history plus finished sessions that are not pushed yet, so an offline gym day doesn't
  repeat a workout.
- **History, dashboard, and progress screens:** they query Supabase directly (online
  only) to begin with. Only the data needed offline is cached.
- **In-progress sessions:** web and ChatGPT can't see them until they are pushed. That
  is acceptable for V0.
- **Deletions:** a set removed locally drops out of the snapshot, and the server's
  replace deletes it. Deleting a whole session is an edit, pushed like any other.
