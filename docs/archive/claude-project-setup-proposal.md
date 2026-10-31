> Historical proposal. Superseded by [AGENTS.md](../../AGENTS.md), [architecture](../architecture.md), and [current status](../status.md). Not operational instructions.

# Repknot: Minimal Project Setup Proposal (Claude)

- **Status:** Proposed, not adopted
- **Date:** 2026-10-31
- **Based on:** `Repknot-PRD.md`, `docs/codex-project-setup-proposal.md`, and research into agent-driven development
- **Confirmed constraints:**
  - App: native Kotlin + Compose
  - Agents: Claude Code, Codex and Antigravity/Gemini
  - Git: trunk-based, with checks run locally only (no CI)
  - Supabase: hosted projects for dev and prod (no local Docker stack)

## Principle

Start with what blocks safe work on day one, and add everything else when a specific trigger shows it is needed.

Each guardrail has a cost: files the agents must read, checks they must run, and setup that can drift out of date. Add one when something actually fails, not because it might.

Three ideas drive the setup:

1. **Markdown explains, mechanisms enforce.** The things that really stop mistakes are Gradle module boundaries, database constraints, tests, the git hook and missing credentials. `AGENTS.md` tells agents where things are and why.
2. **Enforce in a place every agent shares.** Each CLI has its own hook system. The pre-commit hook and `scripts/check` apply to all of them.
3. **Write the invariants down early.** Offline sync and historical integrity are the hard parts of this product, and the invariants that protect them belong in the first docs.

## Day-0 skeleton (essentials only)

```text
Repknot/
├── Repknot-PRD.md
├── AGENTS.md                    # canonical rulebook (~1–2 screens)
├── CLAUDE.md                    # one line: @AGENTS.md
├── .gitignore                   # incl. .env*, data/, *.csv exports, build outputs
├── .githooks/pre-commit         # calls scripts/check --staged
├── scripts/check                # single verification entry point
├── docs/
│   └── architecture.md          # boundaries + Repknot invariants
├── android/
│   ├── gradlew, gradle/wrapper/ # committed, pinned
│   ├── gradle/libs.versions.toml
│   ├── app/                     # Compose UI, navigation, ViewModels, DI wiring
│   ├── domain/                  # pure Kotlin/JVM: models + rules
│   └── data/                    # Room, repositories, Supabase, sync
├── supabase/
│   ├── config.toml
│   └── migrations/              # append-only once applied
└── .claude/
    ├── settings.json            # one PreToolUse hook
    └── hooks/guard.sh
```

That is enough to start. `docs/decisions/`, `docs/tasks/`, `tools/`, `web/`, `supabase/tests/` and CI are deliberately missing; each is added when its trigger occurs (see "Add when needed" below).

### Why each piece is essential

| Item | Reason it can't wait |
| --- | --- |
| `AGENTS.md` + `CLAUDE.md` | Without them, every agent session rediscovers commands and boundaries. Claude Code reads `CLAUDE.md`, which imports `@AGENTS.md`. Codex reads `AGENTS.md` natively. Antigravity's loading must be checked (see the verification checklist). |
| `docs/architecture.md` | Holds the invariants that prevent data-integrity bugs, which are the costliest mistakes in this product. |
| Three Gradle modules | `domain` as a pure JVM module enforces the most important boundary at compile time, at no extra cost. Feature areas (logging, programs, history, check-ins) are **packages** inside `app`, not modules. |
| `scripts/check` | One definition of "passing" shared by humans, agents and the hook. If a required tool is missing it **fails loudly** rather than skipping silently. |
| `.githooks/pre-commit` | With no CI, this is the only gate that applies to every agent. Set it up with `git config core.hooksPath .githooks`, so no hook-manager dependency is needed. |
| `.claude/hooks/guard.sh` | Closes the main gap in a trunk-only setup: an agent bypassing the hook with `--no-verify`. It also protects applied migrations and prod. |
| `.gitignore` for data | The Airtable export is personal health data. It must never be committed, and once committed it is effectively there forever. |

## Architecture

```text
app → domain
app → data → domain
```

| Layer | Rule |
| --- | --- |
| UI (`app`) | Compose renders state and sends actions. It never calls Supabase or DAOs directly. |
| `domain` | Pure Kotlin with no Android, Room or Supabase imports. The Gradle module setup makes this a compile error. |
| `data` | Implements the repositories `domain` needs. It owns Room, Supabase and sync. |
| Database | Owns authorization (RLS), relational constraints, uniqueness and atomic mutations. |
| All clients | Android, web, the CLI and future AI actions all go through the same RLS and constraints. None use the service-role key. |

Keep abstraction lean. Agents tend to over-abstract, so AGENTS.md says so explicitly:

- No generic base repositories.
- No interface per class.
- No use-case class wrapping a single getter.

Room is the local read source for the UI and Supabase is the canonical store, following Android's [offline-first guidance](https://developer.android.com/topic/architecture/data-layer/offline-first).

### Repknot invariants (go into `docs/architecture.md`)

1. **Suggestions aren't data.** Previous performance only prefills the UI and is never saved as a completed set.
2. **History is a snapshot.** Starting a session copies the prescription. Later program edits, and substitutions within a session, never rewrite history or the program.
3. **Stable IDs.** The client generates UUIDs for sessions, session exercises and sets, and they never change across retries.
4. **Atomic local writes.** A local edit and its pending-sync record are written in one Room transaction.
5. **Idempotent, all-or-nothing upload.** A retry, including after a lost response, cannot create duplicates. A partial upload cannot leave a session that looks complete.
6. **Detectable conflicts.** A concurrent edit is detected through a revision number and never silently overwritten.
7. **Imports are traceable.** Imported records keep their source ID, raw values and reconciliation status. Ambiguous values are flagged, never guessed.

**Proposed sync design**, to be confirmed as the first decision record during milestone 1:

- Upload a **whole session snapshot**: the session, its exercises, sets and notes, plus a revision number.
- Send it to **one Postgres function** that upserts by ID in a single transaction and rejects stale revisions.

Compared with syncing row by row, this satisfies invariants 5 and 6 with far less client logic. It fits because a session is edited on one device and is small.

## Agent instructions

### Draft root `AGENTS.md`

```markdown
# Repknot
Personal workout logger. Android (Kotlin/Compose) is primary; Supabase is the
source of truth. Product: Repknot-PRD.md. Architecture + invariants:
docs/architecture.md (read before touching data, sync, or schema).

## Commands
- `scripts/check` — all checks; must pass before you report done
- `scripts/check android` | `scripts/check db` — scoped
- Android build: `cd android && ./gradlew :app:assembleDebug`

## Layout
- android/app — UI, ViewModels, DI. Feature packages, not modules.
- android/domain — pure Kotlin. No Android/Room/Supabase imports.
- android/data — Room, repositories, Supabase, sync.
- supabase/migrations — schema. Append-only once applied.

## How to work
1. Run the relevant checks first to see the baseline.
2. For non-trivial work, propose a short plan and wait for approval.
3. Tests first for risky logic: sync, rotation, import mapping, authorization.
   Prefer tests on real Room/Postgres behavior over mocks.
4. Implement one complete behavior at a time; run checks; review your diff.
5. Report what you verified and how. Never claim a check passed if you didn't run it.

## Ask before
- Any migration, RLS policy, auth, or Postgres function change
- New dependency, Gradle module, or top-level directory
- Changing sync protocol, IDs, or anything in docs/architecture.md invariants
- Editing checks, hooks, tests you didn't write, or this file

## Never
- Commit with --no-verify, force-push, or rewrite pushed history
- Edit a migration that is already committed — add a new one
- Use or request prod credentials, or run anything against prod
- Put service-role/secret keys in code, or commit .env / Airtable exports
- Persist prefilled "previous" values as completed sets
- Add generic base classes, interface-per-class, or trivial use-case wrappers
```

The "Ask before → editing checks, hooks, tests" line deals with a common failure: an agent weakens a guardrail to get a check to pass. Changes to `scripts/`, `.githooks/`, `.claude/` and test assertions also need extra attention when you review the diff.

### Tool adapters

- **Claude Code:** `CLAUDE.md` contains only `@AGENTS.md`, plus any notes specific to Claude.
- **Codex:** reads `AGENTS.md` directly.
- **Antigravity/Gemini:** add an adapter only if the verification checklist shows it doesn't load `AGENTS.md`. The adapter should point to the shared file, not copy it.
- **Rule for all tools:** never keep separately edited copies of the rules.

## Enforcement (day 0)

### `scripts/check`

- **`android`:** `./gradlew test lint`, plus `:app:assembleDebug`. Run it only when `android/` changed, or when called explicitly.
- **`db`:** while there are no migrations, it confirms the folder exists and that applied migrations are unchanged (see below). Once pgTAP tests exist, it runs them against the **dev** project.
- **`secrets`:** greps staged files for `sb_secret_`, `service_role` and JWT-shaped strings, and blocks `.env*` and data exports. This avoids needing gitleaks on day 0.
- **`--staged`:** limits the checks to the areas touched by staged files, so commits stay fast.
- **Missing tools:** any required tool that isn't installed (JDK, Supabase CLI) makes the script exit non-zero with a clear message.

### `.claude/hooks/guard.sh` (`PreToolUse` on Bash and Edit/Write)

The hook exits with code 2, which blocks the action and shows the reason to the agent, in these cases:

- A `git commit` with `--no-verify` or `-n`.
- `git push --force` / `-f`, or `git reset --hard` on pushed commits.
- `supabase link`, or any command containing the **prod project ref**. Only the dev project is ever linked.
- An Edit or Write to a file under `supabase/migrations/` that is already tracked by git (`git ls-files --error-unmatch`). New migration files are allowed.
- An Edit or Write to `.env*`.

Claude hooks only bind Claude. The pre-commit hook is the backstop for every agent. Codex's own command rules and sandbox should be configured to forbid the same commands; verify how your installed version does this.

### Credentials and access: the real security boundary

- **Machine contents:** the agent's environment holds only **dev** values: the project URL, the publishable key and a dev DB connection string. Prod credentials live in your password manager and are entered only when you push to prod yourself.
- **Supabase CLI login token:** `supabase login` stores a token that can reach every project in your account, prod included. The guard hook reduces the risk; it doesn't remove it. Options:
  - Run `supabase logout` after linking dev, and use `--db-url` for day-to-day work.
  - Keep prod in a separate Supabase account.

  Choose one deliberately.
- **Prod deploys:** a human-only step, for example `supabase db push --db-url "<prod url from password manager>"` after reviewing pending migrations.
- **Data:** dev holds only synthetic data. Real Airtable data goes to prod only, after a dry run against dev.

## Add when needed

| Add | Trigger |
| --- | --- |
| `supabase/tests/` (pgTAP), with **negative RLS tests as a second real user** (`set local role authenticated` + JWT claims) | The first migration that creates a table. Every table after that ships with RLS enabled, policies and a test showing user B can't read or write user A's rows. Checking that RLS is "enabled" isn't enough. |
| Uniqueness constraints + a retry test for a lost response | Milestone 1 sync work |
| `docs/decisions/NNNN-*.md` | The first decision an agent could reasonably re-argue. The sync design is the likely first one. |
| `docs/tasks/NNN-*.md` (outcome, acceptance examples, non-goals, verification, open questions) | The first feature that spans more than one session |
| `docs/data-model.md` | The schema is no longer obvious from the migrations |
| Konsist or forbidden-import checks | A boundary rule in AGENTS.md gets broken once. Turn it into a check instead of rewording the prose. |
| ktlint/detekt | Formatting noise starts to clutter diffs |
| Claude `Stop` hook running `scripts/check --staged` | An agent reports "done" while checks are failing |
| Nested `AGENTS.md` (plus `CLAUDE.md` → `@AGENTS.md`) | The root file grows past ~2 screens, or an area gets distinct commands (`supabase/`, `tools/`) |
| Claude skills / slash commands | You've typed the same procedural instructions about three times (for example "new migration + RLS + test") |
| `tools/airtable-import/` with synthetic fixtures | The import phase. Build the dry run and the reconciliation report first, against fixtures, never real exports in git. |
| `web/` | The Android logging loop works. Decide the framework then. |
| Supabase MCP (read-only, dev project only) | Agents need to inspect dev data more often than you want to paste it |
| CI (GitHub Actions) | You work from a second machine, a `--no-verify` bypass slips through, or a dependency stops building reproducibly |
| Parallel agents / worktrees | Two tasks have fully separate ownership. Until then, run one implementation task at a time. |

## Setup verification checklist (do once, right after scaffolding)

- [ ] In a fresh session of **each** tool (Claude Code, Codex, Antigravity), ask: "What command verifies this repo, and what must you never do with migrations?" Correct answers show the rulebook actually loaded. Fix the adapter if a tool fails.
- [ ] Commit a file containing a fake `sb_secret_…` string. The hook must block it.
- [ ] Ask Claude to commit with `--no-verify`. The guard must block it.
- [ ] Ask Claude to edit a committed migration. The guard must block it.
- [ ] Uninstall or rename the Supabase CLI on PATH. `scripts/check db` must fail, not pass.
- [ ] Confirm which `supabase test db` flags your installed CLI version supports for a remote dev database.

## First milestone: the thin slice that carries the most risk

> Sign in → open a seeded workout → log one set → kill and reopen the app → go offline/online → sync once → read the exact saved values back from Supabase (from Android **and** a CLI script signed in as you, not as the service role).

It must include a **lost-response retry**: the server commits, the client never sees the acknowledgement, the client retries, and there are still no duplicates.

This one slice tests auth, RLS, Room drafts, the sync design and the CLI-access requirement in PRD §4 together. Then expand, in this order:

1. Session editing
2. Previous-performance prefill
3. Program versions and rotation
4. Airtable import and reconciliation
5. Web companion

## Decisions still needed

- **Sign-in method.** Proposed: email OTP or Google sign-in, with signups disabled after your account exists.
- **When the rotation advances.** Proposed: when a session completes. The client advances optimistically, and the server's record of completion is authoritative after sync.
- **Who may edit an in-progress session.** Proposed: only the Android device that started it. Other clients can edit completed sessions, with revision checks.
- **How to contain the Supabase CLI token.** Log out after linking, or keep prod in a separate account (see above).
- **Which imported program is active on day one.**
- **ChatGPT/Dots integration feasibility.** Verify before committing to anything (PRD §4).

## Differences from the Codex proposal

**Adopted from Codex (my earlier recommendation missed or underweighted these):**

- Three Android modules with feature packages, instead of seven-plus modules and a separate build-logic project.
- The sharper invariants: prescription snapshots, atomic local edit plus pending-sync record, partial uploads that can't look complete, and conflict detection.
- Negative RLS tests as a second user, instead of only checking that RLS is enabled.
- Keeping real health data out of git and building the importer against synthetic fixtures first.
- Watching for agents weakening the guardrails themselves.
- Explicitly avoiding over-abstraction.
- Tests first for risky logic only, not as a ritual for every change.
- One agent at a time until tasks have separate ownership.
- Checking that each tool actually loads the rulebook, instead of assuming it does.
- Scripts that fail loudly when a tool is missing.
- A first milestone aimed at offline sync with the lost-response case.

**Changed from Codex, to fit the confirmed constraints and the minimal-start principle:**

- **No CI or protected merges on day 0.** You chose trunk-based development with local checks. The pre-commit hook plus the Claude guard against `--no-verify` take their place, and CI is a triggered addition.
- **No local or disposable database.** You chose hosted dev and prod. Tests run against the dev project inside rolled-back transactions.
- **Fewer day-0 docs.** No README, `development.md`, `data-model.md`, `decisions/` or `tasks/` until their triggers occur.
- **Concrete agent-side enforcement.** The guard hook's exact rules: no `--no-verify`, migrations append-only, and prod blocked.
- **The Supabase CLI token risk.** It is account-wide, which neither earlier proposal addressed.
- **A specific sync design proposal.** Whole-session snapshot sent to one transactional function, so that invariants 5 and 6 are cheap to uphold.
- **Explicit triggers.** A trigger for every deferred item, so "add as needed" is a rule instead of a judgement call.
