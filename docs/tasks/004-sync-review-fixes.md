# 004 — Guard external saves and resolve deleted-session conflicts

Status: done
Updated: 2026-11-01

## Outcome and acceptance
- An authenticated `save_session` update requires the revision read by the writer and
  returns a conflict without changing data if it is missing or stale.
- A locally edited session deleted on the server becomes a visible deletion conflict;
  keep mine recreates it, take theirs removes the local copy, and one failed upload
  cannot prevent attempts for other pending sessions.

## Scope and plan
1. Add an append-only dev migration and authenticated rollback-only tests for guarded
   saves, including missing/stale revisions and deletion races.
2. Handle deleted conflicts and independent push failures in Android; test device
   persistence and both resolution paths.
3. Run database, build/lint/unit and device checks, inspect the diff, and commit.

## Decisions and discoveries
- The existing privileged ChatGPT connector identity is intentionally a separate task;
  this change keeps the authenticated-user boundary.
- `expected_revision` is a field in the save payload. A missing/stale revision on
  update or a revision for a deleted record returns `status: conflict`, with the
  current snapshot or null, without writing.
- Deleted conflicts are stored as `conflict_deleted`; recreating clears the base
  revision and increments the local revision. A concurrent recreation conflicts again.
- Per-session push exceptions are collected after attempting remaining sessions;
  cancellation propagates immediately.

## Verification
- Applied `20261101010000_guard_save_session.sql` to confirmed dev project
  `<dev-project-ref>`; `scripts/db test`: 49/49 rollback-only checks pass.
- `scripts/check --offline`: build, lint and unit tests pass.
- `:data:connectedDebugAndroidTest --offline`: eight Room tests passed; the optional
  credentialed integration test was skipped in this plain run.
- Ran the credentialed `SharedSessionIntegrationTest` on the test phone against the
  isolated test2 account: deletion conflict, keep mine recreation, take theirs local
  deletion, and continuation past an invalid session all passed. Test2 was empty
  afterward. Final debug APK installed on the phone.
- Deletion-conflict screen wording compiled, but was not separately exercised visually
  with the retained user workout.

## Handoff
Done. Next: decide and separately implement the ChatGPT connector identity approach;
review findings 3–9 remain outside this task.
