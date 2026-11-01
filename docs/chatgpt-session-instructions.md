# Saving a workout through ChatGPT

This is a draft for a future verified integration. Connector access and authorization
have not been verified; do not use it with personal health data until they are.
`save_session` requires an authenticated Supabase user (`auth.uid()`); a SQL connector
running with owner/developer credentials and no user JWT cannot call it as that user.
A suitable authenticated integration remains a separate task.

Supabase documents that its [MCP server](https://supabase.com/docs/guides/ai-tools/mcp)
runs with developer permissions and supports project scoping and read-only mode. This
is not evidence that this ChatGPT account has a compatible scoped write connection.

- Save workouts only through `save_session(payload)`.
- Reuse the `ref` UUID in shared workout text. Never invent an ID; omit `ref` if
  no reference was provided.
- Read the current session before updating it. Confirm the intended changes with the user.
- Send a complete snapshot: `performed_on` (ISO date), `title`, optional `notes`,
  optional `program_id` + `program_workout_key` (both or neither), and `exercises` in
  order. Each exercise has `exercise` (the user's exercise slug, e.g. `lateral-raise`;
  unknown slugs are rejected), optional `notes`, optional `substituted_for` (slug of
  the prescribed exercise it replaced), and `sets` in order. Each set has `unit`
  (`kg`, `lb`, `plates` = machine pin number, or `bodyweight`), `load` (omit for
  bodyweight), `reps` (0.5 steps) and/or `duration_s`, optional `rir` (lower bound of
  a reported range) and optional `notes`. Saving replaces the session's exercises and sets.
- When updating an existing session, include its last-read `revision` as
  `expected_revision`. If the function returns `status: conflict`, re-read the
  session and ask the user how to resolve it; do not retry with a newer revision
  without reviewing the intervening changes. A conflict with `session: null` means
  it was deleted. Omit `expected_revision` only when creating a new session.
