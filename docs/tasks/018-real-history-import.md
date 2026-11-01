# 018 — Import the real Airtable history (ADR 003)

Status: done
Updated: 2026-11-01
Starting commit: aef47cf
Lead task (real-data operation, owner-approved 2026-11-01).

## Outcome
The owner's Sep–Oct 2026 training history lives in the canonical Supabase project
([ADR 003](../decisions/003-single-canonical-project.md)), so the phone shows last values
from the first workout and future progress views have the full history.

## Steps and verification
1. Access review (read-only queries) and trigger-function grant fix — recorded in ADR 003;
   `scripts/db test` 110/110.
2. `scripts/airtable-import private-data/airtable-health-2026-10-31/normalized` as the owner.
   First run stopped at sets: a few sets recorded as failed attempts have `reps = 0`, which the schema rejects
   (reps > 0). No sets had been written (the failing batch rolled back); sessions and
   session exercises were in and are skipped on re-run by source ref.
3. Importer fix: a 0-rep set is kept as a failed attempt — `reps` null,
   `raw = {"reps": 0, "review": "failed attempt (0 reps)"}` — so it is preserved but never
   counted as performed reps. `deno check` passed.
4. Re-run: every exercise, session, session exercise, set, check-in and context entry
   imported. Third run: every table `0 new`. Server: the failed attempts are kept in
   `raw`.
5. Test phone (upload still off): relaunch refreshed; Push 1 preview shows
   `Last · …` lines matching the history; rotation still suggests Push 1 (imported sessions carry no program).

## Open
- Public sign-ups confirmed off by the owner (2026-11-01).
- Previous program published inactive as `upper-lower` v1 (owner accepted the draft as-is,
  2026-11-01); PPL v1 stays active.
