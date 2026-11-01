# 003 — One canonical Supabase project for real training history

Status: accepted  
Date: 2026-11-01  
Supersedes: the "real data is imported to prod only" part of [ADR 002](002-training-data-model.md)
("Import"); the rest of ADR 002 stands.

## Context

ADR 002 planned to import the real Airtable history only into a future prod project, after
an access review, keeping the existing project ("dev") synthetic. By 2026-11-01 that
project already held the owner's real exercises and active PPL program, and real logging
on the phone starts 2026-11-02. The owner wants the whole history in one place so the app
can show last values from the first workout and later build progress/regression views on
it (PRD: Supabase is the canonical store for every client).

## Decision

- The existing Supabase project is the canonical store for the owner's training data,
  including the imported Airtable history (sessions, sets, check-ins, health-state and
  training-habit context). It keeps the name "dev" in scripts and docs for now.
- Synthetic test data lives only in separate test accounts (test2 and the pgTAP users that
  roll back); owner-only RLS keeps it apart from the owner's rows.
- Schema work continues as tested migrations (`scripts/db test` must pass). A separate
  scratch project is created if an experiment ever needs destructive changes; migrations
  that drop or rewrite tables are no longer acceptable on this project once it holds real
  data (the 2026-11-01 drop-and-recreate was the last one).

### Access review before the import (2026-11-01)
- RLS enabled on all 10 public tables; no table grants to `anon`; owner-only policies
  asserted by pgTAP (001–006).
- Security-definer functions: `push_session`, `publish_program` — both require
  `auth.uid()` and scope every row to it (reviewed with their migrations).
- Trigger functions were executable by `PUBLIC`/`anon`; revoked
  (`20261101060000_revoke_trigger_function_execute.sql`, asserted in test 006).
- No storage buckets. Two auth users: the owner and test2.
- Clients use only the publishable key; no service-role key in any client or script.

## Alternatives considered

- **Create prod now and import there:** cleaner naming, but the phone, CLI and program
  already point at this project; a second project duplicates setup for one user and
  delays the history the owner wants now.
- **Wait for prod:** last values and progress views stay empty for weeks.
- **Import only sessions and sets:** less sensitive data in this project, but splits the
  history the owner explicitly wants in one place.

## Consequences

- Last values and future progress views work from the full history immediately.
- Destructive migrations now need a data-preserving plan, and Room's
  "building phase" reset applies only to the phone's local cache (server data is safe).
- Open risks to revisit: whether public sign-ups are enabled (owner to confirm in the
  dashboard: Authentication → Sign In / Providers → "Allow new users to sign up"); the
  account-wide Supabase CLI token noted in `docs/architecture.md`; connector (ChatGPT)
  privileges before any connector writes.
