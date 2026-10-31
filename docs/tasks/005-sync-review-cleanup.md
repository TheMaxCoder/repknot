# 005 — Sync review cleanup

Status: done
Updated: 2026-11-01

## Outcome and acceptance
Address review findings 3–9: limit redundant network-triggered syncs, remove legacy
per-set revision code and obsolete build-phase migration, preserve set identity across
reorders, surface action failures, make the credentialed integration test discoverable,
move finish-screen text to resources, and prevent clients forging push receipts.
ChatGPT connector identity (finding 1a) is outside scope.

## Scope and plan
1. Protect receipts using narrowly scoped server-side privileges and prove legitimate
   pushes still work with isolation and forgery tests.
2. Refactor Android sync scheduling, set storage/identity, and action feedback.
3. Use resources for UI text and document a safe integration-test command.
4. Run server, build, unit, device and live integration checks; inspect diff and commit.

## Decisions and discoveries
- User explicitly approved resetting local synthetic workout data on the phone when
  installing the schema change; the synced dev copy and app sign-in were retained.
- Room v3 removes the old v1-to-v2 migration and the per-set revision columns. A
  destructive fallback is limited to the known synthetic v1/v2 schemas; it must not
  become a general fallback for later versions.
- `push_session` now runs as a tightly scoped security-definer function. It requires
  auth.uid(), pins search_path, scopes parent and receipt access to that user, and
  retains the existing idempotency and conflict rules. Clients can select their own
  receipts but cannot insert, update, or delete them.
- Server set UUIDs now remain attached to their own contents, including reordering;
  the single-set editor follows the first set rather than assuming a fixed child ID.
- Network-triggered sync happens on a validated-network transition, rather than every
  validated capability callback. Conflict and other action errors are shown in the UI.

## Verification
- Applied `20261101020000_protect_push_receipts.sql` to confirmed dev project
  `<dev-project-ref>`. `scripts/db test`: 56/56 rollback-only checks pass,
  including receipt forgery, legitimate replay, and cross-user isolation.
- `scripts/check --offline`: debug build, lint, and unit tests pass, including network
  transition and share formatting tests.
- `:data:connectedDebugAndroidTest --offline`: eight Room tests passed; the opt-in
  credentialed test was explicitly skipped. Reorder and removal retain server IDs.
- `scripts/test-sync-integration --offline`: credentialed phone/backend integration
  passed against test2; test2 was empty afterward. The wrapper fails if skipped.
- Installed the build on the test phone, reset the v2 synthetic database with user
  approval, and confirmed sign-in and the synced set were restored. Wi-Fi was
  restored after the offline check. The error message is wired and built, but the
  attempted offline visual reproduction did not trigger it; no visual pass claimed.

## Handoff
Done. ChatGPT connector identity and its live integration remain a separate task.
Before real health data, revisit broader table permissions and recovery, and use
non-destructive schema migrations.
