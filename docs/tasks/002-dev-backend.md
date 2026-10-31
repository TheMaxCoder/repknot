# 002 — Dev backend and authenticated access

Status: ready
Updated: 2026-10-31

## Outcome and acceptance
A confirmed hosted dev project stores synthetic records. Android and a CLI using a
normal signed-in user can read/write their records. A second user cannot access them.

## Scope and plan
- Inspect available dev configuration; resolve sign-in method when necessary.
- Add minimum migrations, ownership policies, and synthetic seed matching task 001.
  Session schema follows [ADR 001](../decisions/001-sync-model.md) from the first
  migration: UUID ids, `performed_on`, `revision` + `client_revision` + `source`, and
  the trigger that bumps `revision` on any change to a session or its children.
- Verify authenticated Android and CLI writes/read-back and negative user access.
- Record dev credential handling and any intentional temporary security relaxation.

Applied/shared migrations are append-only. Admin credentials may support development
operations, but must not be shipped or used as the ordinary app/CLI identity. Confirm
dev target before operations; keep secrets outside Git. No production setup required.

## Decisions and discoveries
Dev project/access and sign-in are unresolved. Ask only for missing inputs when needed.

## Verification
Not started. Record actual commands/results here, including second-user checks.

## Handoff
Follow task 001. Establish dev access and sign-in, then implement minimum schema.
