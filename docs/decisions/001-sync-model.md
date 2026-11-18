# 001 — Sync model: server cache, local drafts, push on finish

Status: accepted, partly superseded (see the notes in sections 4 and Consequences)  
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

- **Session `id`:** a UUID, the hidden key that prevents duplicates.
  - The phone generates it when a session starts, before any push, so a session that
    has never been pushed already has one.
  - Writers with nothing to copy (e.g. ChatGPT logging a workout from scratch) leave it
    out, and the database default `gen_random_uuid()` supplies it.
- **Session `number`:** a friendly identity column assigned by the server (`#42`), for
  humans and chat ("update workout #42"). It is assigned on the first save to Supabase
  and is never used as the sync key.
- **Children (session exercises, sets):** UUIDs. Server writes replace a session's
  children wholesale, so child IDs never have to match across writers.
- **`performed_on`:** an ordinary date column, not unique. Several sessions can share a
  date: restarts, two sessions in a day, workouts that cross midnight, imported history.
  "You already have a workout today" is a prompt in the UI, not a database constraint.
- **Why not auto-increment or date + content as the key:**
  - A server-assigned number doesn't exist while the phone is offline or before the
    first push.
  - A lost acknowledgement, or a paste to ChatGPT before pushing, would then create a
    duplicate.
  - Content changes whenever ChatGPT or the user corrects something, so content can't
    identify a session.

### 4. Push

- Finishing a session shows an overview. Confirming starts a push straight away.
  *Superseded 2026-11-02 by [task 028](../tasks/028-explicit-upload.md): finishing keeps the
  workout on the phone; only its Upload tap marks it for push, and retries then apply as below.
  Check-ins upload on save (2026-11-18). Current behavior: [architecture](../architecture.md).*
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
   Implementation refinement (2026-11-01): a replay is acknowledged only when the
   server is still at the exact revision recorded for that phone revision. If another
   writer changed it after the original push, the server reports a conflict. A receipt
   records that revision; resolving with keep mine advances the local revision.
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
- If the server session was deleted, the prompt offers keep mine (recreate with the
  same ID) or take theirs (delete the local copy). A concurrent recreation is checked
  again and can produce a new conflict.
- Nothing is merged automatically.

### 6. Other writers

- **Web and CLI** write as the signed-in user, through RLS-protected tables or functions.
  They never use a service-role key.
- **ChatGPT** (via the Supabase connector in ChatGPT web) saves sessions only through
  `save_session(payload jsonb)`. In one transaction, that function:
  - uses the payload's `ref` session ID, or generates one if there is none;
  - validates the payload;
  - replaces the session's children;
  - sets `source = 'chatgpt'`;
  - bumps `revision` (through the trigger).

  ChatGPT project instructions must say:
  - Save sessions only with `save_session`.
  - Always reuse the `ref` from pasted text, and never invent IDs.
  - Read the current session before updating it and supply its revision as
    `expected_revision`; on mismatch or deletion the function returns a conflict and
    leaves the server unchanged.
- **Connector risk (unverified):** the connector likely runs SQL with owner privileges.
  That would bypass RLS, and depending on authorization it could reach every project on
  the account, prod included. Before relying on it:
  - restrict it to one project, if the connector supports that;
  - enable backups or point-in-time recovery;
  - verify its actual scope.

  The revision trigger still fires for owner-privileged writes, so conflict detection holds.
- Changes ChatGPT makes at the user's request are normal writes. Autonomous AI
  suggestions still need the user's approval before they change programs or logs (PRD §5).

### 7. Sharing a session with ChatGPT

- The finish overview has **Share / Copy**. It produces plain text through the Android
  share sheet, and always includes the session `ref` (UUID). Once a `number` exists, it
  is included too:

  ```
  Workout #42 · Sat 2026-10-31 · Push A
  ref: 7f3c2a1e-9b4d-4c1a-8e2f-0d6b5a7c3e91
  Dumbbell bench press
    1. 32.5 kg × 7 · RIR 3
    2. 32.5 kg × 6 · RIR 2 — grip slipping
  Session notes: short on time
  ```

- Sharing records the session's `local_revision` at that moment as `shared_local_revision`.
- **If ChatGPT saves the session before the phone pushes it,** the next pull or push finds
  the session's own ID already on the server:
  - Local unchanged since sharing (`local_revision == shared_local_revision`): the phone
    adopts the server copy and marks the session pushed. No prompt.
  - Local edited since sharing: a normal conflict (keep mine / take theirs).
- **If the phone pushed first,** ChatGPT's later save is an ordinary update by ID. The
  phone picks it up on its next pull, because its local copy has no unpushed edits.

## Alternatives considered

- **Continuous row-level sync with a per-set outbox:** much more machinery, and not
  needed while drafts live only on the phone.
- **An off-the-shelf sync engine (e.g. PowerSync):** adds an external service and
  dependency. Revisit if several devices need to edit drafts.
- **Last write wins, with no revisions:** a re-push from the phone would silently erase
  changes made by ChatGPT or the web app.
- **The date as session ID:** a second session on the same day would overwrite the first.
- **Manual push only:** a session finished offline, or an app killed after finishing,
  would leave data stranded on the phone. *(Task 028 later chose an explicit Upload tap per
  workout; a tapped upload still retries automatically, so this risk applies only to
  workouts never tapped.)*

## Consequences

- **Supabase schema (task 002):** the first migration includes `id` (uuid, default
  `gen_random_uuid()`), `number` (identity), `revision`, `client_revision`,
  `performed_on`, and `source`, plus the revision trigger. `save_session` arrives with
  task 003.
- **Room:** the session table gets `status`, `local_revision`, `pushed_local_revision`,
  `base_revision`, `shared_local_revision`, and the server `number` once known. Task 001's per-set revision fields move to the session level when
  the session table is added.
- **Today's workout:** rotation is a pure function in `domain`. Its inputs are server
  history plus finished sessions that are not pushed yet, so an offline gym day doesn't
  repeat a workout.
- **History, dashboard, and progress screens:** they query Supabase directly (online
  only) to begin with. Only the data needed offline is cached. *Superseded: these screens
  read the local Room copy of the full history ([ADR 003](003-single-canonical-project.md),
  [ADR 004](004-app-navigation-and-history.md)).*
- **In-progress sessions:** web and ChatGPT can't see them until they are pushed. That
  is acceptable for V0.
- **Deletions:** a set removed locally drops out of the snapshot, and the server's
  replace deletes it. Deleting a whole session is an edit, pushed like any other.
