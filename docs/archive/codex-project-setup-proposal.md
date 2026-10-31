> Historical proposal. Superseded by [AGENTS.md](../../AGENTS.md), [architecture](../architecture.md), and [current status](../status.md). Not operational instructions.

# Repknot — Project Setup and Engineering Workflow Proposal

Status: Proposed foundation; implementation details remain adjustable  
Updated: 2026-10-31  
Basis: `Repknot-PRD.md`, review of `docs/claude-project-setup-proposal.md`, and user direction

## Working principle

Start with a useful foundation and keep it easy to change. Add mechanisms when they solve an actual problem or protect data we are about to rely on. Do not build an agent governance system before building the workout logger.

The user explicitly accepts lighter security during initial development. This is intentional, not an oversight. Agents should be able to adjust the development database, schema, functions, policies, tests, and dependencies as part of authorized work without repeatedly asking permission.

Prioritize reliable workout data, understandable code, and a working feedback loop. Security hardening and additional tooling have explicit triggers below.

## Preferences and constraints

- Native Kotlin + Jetpack Compose.
- Coding tools: Codex, Claude, and Antigravity.
- Trunk-based development with local verification; no CI initially.
- Hosted Supabase dev and prod; no local Docker stack initially.
- One implementation task at a time until parallel work is actually useful.
- Android first; web after the logging loop works.

## Minimal starting skeleton

```text
Repknot/
├── Repknot-PRD.md
├── README.md                   # Setup, prerequisites, check commands
├── AGENTS.md                   # Short shared project guidance
├── docs/
│   └── architecture.md         # Boundaries and important data rules
├── android/
│   ├── gradlew
│   ├── gradle/wrapper/
│   ├── gradle/libs.versions.toml
│   ├── app/                    # Compose, ViewModels, navigation, wiring
│   ├── domain/                 # Pure Kotlin models and business rules
│   └── data/                   # Room, Supabase, repositories, sync
├── supabase/
│   ├── config.toml
│   └── migrations/
└── scripts/
    └── check                   # One entry point for verification
```

Commit the Gradle wrapper and pin build/dependency versions. Add small tool-specific instruction adapters only as needed to load `AGENTS.md`. Verify loading once in a fresh session of each tool.

Do not create empty task, decision, importer, web, or test directories simply to complete a template. Add them with their first useful contents. Keep setup instructions in README until they warrant another document.

## Architecture

```text
app → domain
app → data → domain
```

- `app` owns UI state, rendering, user actions, and dependency wiring.
- `domain` holds business models and rules without Android, Room, or Supabase dependencies.
- `data` implements repositories and owns persistence and sync.
- Room is the Android UI's local read source. Supabase is the canonical shared store.
- Compose and ViewModels use repositories rather than calling Supabase or DAOs directly.

Use feature packages inside `app`. Avoid generic base repositories, an interface for every class, trivial use-case wrappers, and additional modules without a concrete need. Start with Gradle boundaries; add custom architecture checks only if ordinary review proves insufficient.

## Data rules worth getting right early

- Previous performance is a suggestion, not a completed set.
- Sessions preserve the prescription they started with; editing a program does not rewrite history.
- Session substitutions do not mutate the program.
- IDs remain stable across retries.
- Local edits survive app termination and network loss.
- A lost response and retry cannot duplicate a session or set.
- Sync acknowledgement cannot discard edits made while an upload was running.
- Imports preserve source IDs, raw values, and ambiguity rather than guessing.

These rules protect the product itself. They matter earlier than sophisticated hooks or extensive documentation.

### Initial sync candidate

Prefer a whole-session snapshot sent to one transactional Postgres function, if the first implementation confirms it is simpler than row-by-row synchronization.

For V0, Android owns editing an in-progress session. Do not build general multi-device collaboration now. Use stable operation identity for retries and an expected revision to detect stale writes; define removal of sets and exercises explicitly. A retry after a lost response should acknowledge the earlier successful save, not report a new conflicting edit.

Persist the local edit and pending-sync state together. Keep this implementation small and prove it with the first slice. Record the chosen protocol in a short decision note once settled; this proposal is not a mandate for an elaborate sync framework.

## Agent autonomy and process

`AGENTS.md` should contain the project purpose, relevant document links, real commands, architecture boundaries, and these operating rules:

1. Inspect the relevant code and current state before changing it.
2. For nontrivial work, state a short plan and proceed within the authorized task. Do not require a separate plan approval by default.
3. Make the smallest complete change that delivers the requested behavior.
4. Run relevant verification and inspect the diff.
5. Report what changed, what was verified, and any known limitations.

Agents may make routine supporting changes to code, tests, dependencies, documents, dev migrations, dev functions, and dev RLS policies. They may apply migrations to the configured dev project as part of authorized implementation work. Permission to build a feature includes the normal development database work needed to build it.

Ask only when a material product choice is unresolved, the action is destructive to data worth retaining, the work expands beyond the request, or a production/real-data operation has not been authorized. Existing authorization remains valid; do not ask again for every step.

Tests and verification scripts are editable project code. Update them when behavior or tooling legitimately changes. Do not weaken assertions just to hide failures; explain substantive verification changes in the completion report.

## Supabase access and intentional security deferrals

Use a clearly identified hosted dev project with synthetic data. Agents may use the dev administration credentials needed for schema and function work. Keep those credentials outside version control and out of shipped clients. Application clients should use normal authenticated access; admin access is for development operations.

No bespoke command-blocking hooks, exhaustive permission rules, or production isolation machinery are required on day one. Local checks are feedback, not a tamper-proof security boundary. We intentionally accept that limitation while developing on synthetic data.

Before a database operation, confirm the configured target is dev. Routine non-destructive dev changes need no additional approval. Do not reset a project or delete data merely because it is labelled dev; first establish that its contents are disposable or obtain authorization.

Keep schema changes in migrations. Applied or shared migrations should normally be followed by a new migration; unshared, unapplied drafts may be edited. Do not build a migration-protection hook initially.

Add straightforward ownership policies with the first user-owned tables, but defer a comprehensive security suite. Any temporary relaxed policy must be recorded in `docs/architecture.md` with its scope and removal trigger. Do not mistake a passing test under admin credentials for proof that application authorization works.

Before real health data is imported or the app is relied on beyond synthetic development, review RLS, client credentials, account access, and recovery procedures. The PRD's privacy requirements still apply to the usable product; early development deferrals do not silently remove them.

## Verification without ceremony

Use one `scripts/check` entry point with only the modes we actually need. Initially it should run the Android build, relevant unit tests, and lint. Add database and device checks with the features they verify. Report missing tools or unavailable environments clearly rather than silently treating a check as passed.

- Domain tests: workout rules and nontrivial transformations.
- Room/device tests: persistence across app restart and pending-sync behavior.
- Supabase integration tests: atomic saves, retries, and authenticated access.
- Manual device checks: logging usability and the offline/reconnect flow.

Use tests first when helpful for risky logic; do not mandate TDD for every edit. Do not rerun the entire suite for every documentation change. No coverage percentage target or custom staged-snapshot checking system initially.

A pre-commit hook is optional convenience and can be added if missed checks become a recurring problem. Task completion still requires relevant verification even without a hook. No CI or protected-merge setup initially.

## Milestone 0: prove the foundation

> Sign in → open a seeded workout → log one set → kill and reopen the app → reconnect → sync → read the exact saved values from Android and an authenticated CLI client.

Also verify:

- A lost server response followed by retry creates no duplicate records.
- An edit during an upload remains pending until saved.
- The CLI can perform an authorized write and read it back without using an admin key as its application identity.

This is an engineering slice, not completion of the PRD's first milestone. Then expand to session editing, previous-performance suggestions, program versions and rotation, body check-ins, Airtable import/reconciliation, and the companion web view.

## Add when needed

| Addition | Trigger |
| --- | --- |
| Short architecture decision note | A consequential choice is settled and worth preserving, starting with sync |
| Task note | Work spans sessions and needs a durable handoff |
| Database tests | First implemented database behavior needing verification |
| Negative authorization tests and security review | Before importing real health data or relying on the app with real records |
| Import tooling and reconciliation reports | Start of the Airtable import phase; synthetic fixtures first |
| Secret scanner or pre-commit hook | Before the first real-data/production release, or earlier if routine mistakes justify it |
| Additional architecture checks | A boundary repeatedly drifts despite ordinary review |
| Separate development/data-model documentation | README or architecture notes become difficult to navigate |
| CI | Local verification becomes unreliable or repeated setup on another machine warrants it |
| Additional agent skills/hooks | A repeated workflow has a demonstrated need for automation |
| Web app | Android logging loop works |
| General multi-client editing | A real use case requires it |

## Decisions still open

- Sign-in method.
- Active program version on day one.
- Exact rotation advancement behavior, including offline completion and later sync.
- Final sync implementation after the thin slice.
- Supported ChatGPT/Dots integrations and their permissions.

Resolve these when they block the next piece of work. Avoid turning all open questions into prerequisites for scaffolding.

## Research background

The earlier proposal drew on practitioner discussions about planning, behavior-focused tests, deterministic checks, and the review cost of parallel agents. These support a lightweight process; they do not establish a requirement for elaborate governance.

- [Staff engineer discussion of planning and testing](https://www.reddit.com/r/ClaudeCode/comments/1qd64xx/tdd_workflows_with_claude_code_whats_actually/)
- [ExperiencedDevs discussion of agent supervision and checks](https://www.reddit.com/r/ExperiencedDevs/comments/1tlclik/claude_autonomous_coding_discussion/)
- [Discussion of parallel-agent coordination costs](https://www.reddit.com/r/ClaudeCode/comments/1xeg8xs/anyone_else_transitioning_to_100_agentic_workflow/)
- [Android offline-first guidance](https://developer.android.com/topic/architecture/data-layer/offline-first)
- [Supabase database functions](https://supabase.com/docs/guides/database/functions)
- [Supabase testing guidance](https://supabase.com/docs/guides/local-development/testing/overview)
