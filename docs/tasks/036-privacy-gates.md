# 034 — Privacy gates for commits and pushes

Status: done (2026-11-12)
Starting commit: `dc134d8`

## Outcome
Personal data and secrets are caught automatically before they are committed or pushed,
for every agent and for the owner, without anyone having to remember a prompt.

Acceptance:
- `scripts/privacy-check` scans staged changes, a commit message, a revision range, or
  all history. It blocks on generic patterns (home paths, emails outside placeholder
  domains, Supabase URLs and keys, tokens, private keys, AI attribution) and on private
  values: the ignored `private-data/privacy-denylist.txt` plus credentials and the project
  ref read from `.env`. It never prints a private value.
- `scripts/push-gate` runs the check on everything being pushed, then an independent,
  tool-less AI review of the outgoing commits (different model from the usual author),
  and blocks unless every chunk returns `VERDICT: PASS`. It fails closed.
- Tracked hooks in `.githooks/` (pre-commit, commit-msg, pre-push), enabled per clone by
  `scripts/install-hooks`. Overrides need an explicit reason and are logged in `.git/`.
- The current history passes the check.
- `AGENTS.md` documents the gates, "describe, don't quote", synthetic-first verification,
  and that the web companion reuses the same setup.

## Scope
New scripts and hooks, the private denylist (ignored), `AGENTS.md`, `README.md` setup line.
Out of scope: CI, GitHub settings (owner enables push protection when publishing).

## Plan / progress
1. [x] `scripts/privacy-check` (Python, stdlib only).
2. [x] `scripts/ai-privacy-review` + `scripts/push-gate` (bash; reviewer via `pi`).
3. [x] `.githooks/*`, `scripts/install-hooks`; install in this clone.
4. [x] Private denylist in `private-data/`; baseline `--history` passes.
5. [x] Tests: planted leaks blocked at commit and message; push gate against a throwaway
   local bare remote: leak commit blocked, clean commit passes.
6. [x] `AGENTS.md` and README; commit.

## Decisions and discoveries
- Owner chose blocking hooks (reverses the earlier "no command-blocking hooks" stance for
  this purpose only), the AI review at push time only, and a different-model reviewer.
- `/tmp` paths are not flagged (not personal); placeholder email domains are allowed.
- Inline `privacy-check: allow` silences generic rules on that line only; private values
  can never be allowed inline.
- The owner's Git config uses mnemonic diff prefixes (`c/`, `i/`); both scripts force
  `a/`/`b/` prefixes, otherwise no file paths are parsed and nothing is checked.
- `.env` passwords shorter than 12 characters are not scanned (a short password matches
  ordinary text); the check warns instead. The owner's current password is short, so
  changing it before publishing is required.
- Pushes review `<local sha> --not --remotes=<remote>`: everything that remote lacks, which
  also covers force-pushes and new branches. A first publish reviews the whole history
  (19 chunks of at most 80 KB with the current history).

## Verification
- `scripts/privacy-check --history` on the current history: exit 0 (no findings).
- Commit hooks in a scratch clone with this repo's private list and `.env`: a home path and
  email, a private denylist value, and the project ref from `.env` were blocked, with private
  values masked in the output; an inline allow worked for a generic rule but not for a
  private value; a `Co-Authored-By` trailer in the message was blocked; a clean commit
  passed; an override committed and was logged.
- Push gate against a throwaway local bare remote: a commit with an invented but
  realistic personal gym note (no pattern matches) passed the commit hook and was blocked
  by the AI review (remote unchanged); a clean commit passed and was pushed; with `pi`
  unavailable the push was blocked.
- Full-history dry run with a stand-in reviewer: 19 chunks, each at most 80 KB; a reply
  without a verdict blocks.
- `bash -n` on the shell scripts and hooks; Python sources parse.

## Handoff
Hooks are enabled in this clone. Owner actions before publishing: change the account
password (then update `.env`), enable GitHub push protection after creating the repo, and
add any further real values to `private-data/privacy-denylist.txt`. The first push runs the
full-history review automatically; `scripts/push-gate --revs HEAD --not --remotes` runs it
beforehand without pushing.
