# 031 — Public history scrub

Status: done (2026-11-12)
Starting commit: `1f214f4` (rewritten head; the pre-rewrite head no longer exists in this repo)

## Outcome
The repository can be published: no personal data in any commit's files or messages, no
AI attribution trailers, and rules that keep it that way.

Acceptance:
- Every blob and commit message in history is free of body measurements and goal prose,
  quoted real training results and counts, phone/watch models and vendor, local absolute
  paths, and `Co-Authored-By`/"Generated with" lines.
- Same 153 commits, subjects, authors, dates and per-commit file lists as before.
- `scripts/check --offline` passes on the rewritten head.
- `AGENTS.md` forbids AI attribution and personal data; Claude Code attribution is off in
  `.claude/settings.json`.

## Scope
History rewrite (one-time, explicitly authorized by the owner on 2026-11-11 as an
exception to "Never rewrite existing commits", which still stands), commit-ID fix-ups in
current notes, and the new rules. README, license and publishing are separate follow-ups.

## Decisions and discoveries
- Credentials, `.env`, `private-data/` and delegate logs were never committed; the leak was
  real values quoted in task notes, tests and messages.
- Measurement values in tests were shifted consistently (not deleted), so tests still
  agree; `BodyTest` expectations derived from them were recomputed.
- Kept: exercise names in the Airtable name mapping (needed by the importer), the app's
  configurable default waist goal (a default, no longer tied to the owner in prose),
  generic fixture sets, and program-size facts (exercise counts in the PPL program).
- Session-note examples that may have been real were replaced with neutral text.
- Phone/watch references now say "the test phone" / "the watch"; mockup paths keep only
  the file name.
- Commit IDs inside current task notes were remapped to the rewritten IDs; IDs inside
  older historical versions of those notes still name pre-rewrite commits.

## Verification
- `git filter-repo` (run via `uvx`, not a project dependency) with blob and message
  callbacks on a fresh clone; rules lived outside the repo.
- Marker scan over every blob and message of the rewritten history: 0 hits.
- Per-commit file-list and subject comparison against the original: identical.
- `scripts/check --offline` on the rewritten head: BUILD SUCCESSFUL (after recomputing
  two `BodyTest` expectations).
- Every commit ID referenced in current docs resolves to a commit.

Not verified: instrumented device tests (`:data:connectedDebugAndroidTest`) after the
value shift; they only store and reload the shifted values.

## Handoff
The original history is kept outside the repo as a local bundle (contains personal data;
never publish it). Next: README for visitors, license, fresh-clone build check, then
publish.
