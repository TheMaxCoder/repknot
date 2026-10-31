# 002 — Dev backend and authenticated access

Status: in progress (device check pending)
Updated: 2026-10-31

## Outcome and acceptance
A confirmed hosted dev project stores synthetic records. Android and a CLI using a
normal signed-in user can read/write their records. A second user cannot access them.

## Scope and plan
- Inspect available dev configuration; resolve sign-in method when necessary.
- Add minimum migrations, ownership policies, and synthetic seed matching task 001.
  Session schema follows [ADR 001](../decisions/001-sync-model.md) from the first
  migration: UUID `id` (default `gen_random_uuid()`), identity `number`, `performed_on`,
  `revision` + `client_revision` + `source`, and
  the trigger that bumps `revision` on any change to a session or its children.
- Verify authenticated Android and CLI writes/read-back and negative user access.
- Record dev credential handling and any intentional temporary security relaxation.

Applied/shared migrations are append-only. Admin credentials may support development
operations, but must not be shipped or used as the ordinary app/CLI identity. Confirm
dev target before operations; keep secrets outside Git. No production setup required.

## Decisions and discoveries
- Dev project: `<dev-project-ref>` (free tier, only project; prod comes later as a
  second project). Sign-in: email + password; signups disabled. Users: owner + test2.
- CLI access uses `supabase login` + `supabase link` (temporary relaxation recorded in
  architecture). No DB password is stored: `db push --linked` and `db query --linked`
  use a short-lived login role / the Management API.
- `supabase test db` needs Docker, which is not installed. Tests run via
  `supabase db query --linked`, which returns only the last result, so each test file
  collects TAP lines in a temp table `tap` and returns them before `rollback`.
- Schema: `workout_sessions` (uuid id default, identity `number`, `performed_on`,
  `revision` set by trigger, `client_revision`, `source`) and `session_sets` (composite FK
  `(session_id, user_id)` so sets can't attach to another user's session). Owner-only RLS
  for `authenticated`; all grants revoked from `anon`.
- Identity numbers have gaps (rolled-back test runs consume them); harmless.
- Android: `CloudAccount` (data) uses supabase-kt 3.8.0 + Ktor 3.5.1; URL and publishable
  key come from root `.env` into `BuildConfig` (nothing else). "Cloud (dev)" section on the
  logging screen: sign in, session count, upload the logged set as a synthetic session.

## Verification (2026-10-31)
- `scripts/db push`: migration applied to dev.
- `scripts/db test`: 22/22 pass (ownership, second-user denial for read/update/delete/
  insert, anon denied, revision bump/forgery, constraints, cascade, RLS on all tables).
  Confirmed nothing persists afterwards; a deliberately failing test makes the script exit 1.
- CLI: owner `log-test` created #10 and `show` read back exact values; `--as test2`
  sees no sessions, cannot show or delete #10.
- `scripts/check`: build + lint clean (only pre-existing version notices).
- **Pending:** on-device sign-in, session persistence across restart, and upload of the
  logged set with CLI read-back. The phone was disconnected when this was reached.

## Handoff
Next: connect the phone, `cd android && ./gradlew :app:installDebug`, sign in as the owner,
log a set, tap "Upload logged set as test session", confirm with `scripts/repknot list`,
force-stop/relaunch to confirm the sign-in persists. Then mark done and start 003.
