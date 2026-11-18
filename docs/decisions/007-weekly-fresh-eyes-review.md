# ADR 007 — Weekly fresh-eyes review

Status: accepted · 2026-11-18

## Context
Every change is checked (`scripts/check`, device tests) and pushes pass the privacy gate (036),
but nobody steps back to ask whether the project still hangs together: docs drifting from code,
the same rule in two places, boundary leaks, sync or migration paths that can strand data. The
lead reviews the worker's diffs, but the lead also wrote the plans, so it shares their blind spots.

## Decision
Once a week the owner or the lead runs `scripts/weekly-review`. A model that wrote none of the
code (default `openai-codex/gpt-6.1-sol`, the privacy reviewer's model, not the delegation
worker's) reads AGENTS.md, the architecture notes, the ADRs and the commits since the last
review in a clean temporary checkout with read-only tools (read, grep, find, ls: no shell,
no writes, tracked files only). It reports at most 10 findings to `docs/reviews/YYYY-MM-DD.md`.

In the same session the lead triages each finding under the report's Triage heading: fixed
(commit), task (link) or dismissed (reason), then commits the report. The next run starts
where the last report's range ended and sees that report, so dismissed items are not repeated.
The review is advisory: it blocks nothing.

## Alternatives
- Review every task or every push: more cost and noise for a solo project; per-diff review also
  misses drift that only shows across several changes.
- Pull requests for every change: the owner would mostly approve their own work.
- A scheduled job: needs this machine awake and the reviewer signed in; revisit if weeks get
  skipped.
- A Claude session as the reviewer: same model family as the lead, so less independent.

## Consequences
- Reports are tracked and public, so they go through the privacy gate like any doc.
- The checkout holds tracked files only, so `.env` and `private-data/` are not in it. The read
  tools are not sandboxed, though: an absolute path elsewhere on the machine would be readable.
  The prompt keeps the reviewer in its working directory.
- The reviewer's model and prompt live in the script; tune them there when reports get noisy.
