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
- Send a complete snapshot: `performed_on` (ISO date), `title`, optional `notes`, and
  `sets` as an array with `exercise_name`, `load`, `unit` (`kg` or `lb`), `reps`,
  optional `rir` and optional `notes`. Saving replaces the session's set list.
