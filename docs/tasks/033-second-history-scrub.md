# 033 — Second history scrub

Status: done (2026-11-12); account password changed 2026-11-16
Starting commit: `f964b8b` (rewritten; the pre-rewrite history is kept only in a local bundle)

## Outcome
Close the gaps an external release review found after [031](031-public-history-scrub.md),
so the history can be published.

Acceptance:
- No blob or commit message contains the backend project ref, the quoted real failed-set
  note (task 018), device-check quotes derived from the real program (tasks 012, 026, 027),
  or the fixture value copied from real health context (pgTAP test 006). The categories
  searched in 031 remain absent.
- `scripts/test-sync-integration` no longer hard-codes a project; it reads
  `REPKNOT_DEV_PROJECT_REF` from `.env` and keeps the three-way match (URL, configured ref,
  linked project).
- Task 031 and the status page no longer claim more than was verified.

## Scope
A second one-time history rewrite, explicitly approved by the owner on 2026-11-12 (the
"Never rewrite existing commits" rule still stands). Same commits, subjects, authors,
dates and per-commit file lists; only text inside files changed. Commit IDs in current
notes were remapped to the new IDs.

## Decisions and discoveries
- 031 searched for full backend URLs, not the bare project ref, and for counts and
  measurements rather than quoted set notes; that is how these were missed. The verify
  list now includes the bare ref and the reviewer's terms.
- Kept: format examples in specs (`25 kg × 10`), generic test dates, and the
  program-size facts noted in 031.
- The account password is weak but untracked; changing it is the owner's action
  (Supabase dashboard, then `.env` and the phone sign-in).

## Verification
- Scan of every reachable blob and message: no hits except the AGENTS.md rule text and
  031's description of it.
- Per-commit file lists and subjects identical to the pre-rewrite history.
- Guard logic run in isolation: passes only when URL, configured ref and linked project
  match. `bash -n scripts/test-sync-integration` passed.
- `scripts/db test` not run: the Supabase CLI now requires interactive consent for SQL on
  the hosted project and refuses in agent mode. The only test change is a fixture string
  that no assertion reads.

## Handoff
Next: (password changed 2026-11-16 with `scripts/repknot set-password`) owner optionally runs `scripts/db test` themselves,
then decides on publishing.
