# Worker notes

Appended to the worker's system prompt by `scripts/delegate`. Each rule exists
because a delegated task failed the same way twice. Maximum 12 rules: to add one
when full, merge or replace an existing rule. Change only per the triage in
`docs/delegation/README.md`, in its own commit.

## Rules

1. Small gaps: when an ambiguity cannot change data, sync, security, or user-visible
   product behavior a reviewer would care about (a nullable default, a helper's name, a
   formatting detail), pick the most conservative reasonable option, write it under the
   task's "Decisions and discoveries", and keep going. Stop and ask only for gaps that
   do affect those things. (Task 011: stopped twice; the second was a null default.)
