-- Dated text context (ADR 002 "Import"): Airtable health-state and training-habit records.
-- Kept as a title plus the source's remaining fields; not interpreted by the app yet.
create table public.context_entries (
  id          uuid primary key default gen_random_uuid(),
  user_id     uuid not null default auth.uid() references auth.users (id) on delete cascade,
  kind        text not null check (kind in ('health_state', 'training_habit')),
  recorded_on date,                                     -- effective-from / event date
  title       text not null check (length(trim(title)) > 0),
  details     jsonb not null default '{}'::jsonb,       -- remaining source fields by name
  source      text not null default 'unknown'
              check (source in ('android', 'web', 'chatgpt', 'cli', 'import', 'unknown')),
  source_ref  text,
  raw         jsonb,
  created_at  timestamptz not null default now(),
  unique (user_id, source_ref)
);
create index context_entries_user_kind_date on public.context_entries (user_id, kind, recorded_on desc);

alter table public.context_entries enable row level security;
create policy "own rows: select" on public.context_entries for select to authenticated using ((select auth.uid()) = user_id);
create policy "own rows: insert" on public.context_entries for insert to authenticated with check ((select auth.uid()) = user_id);
create policy "own rows: update" on public.context_entries for update to authenticated
  using ((select auth.uid()) = user_id) with check ((select auth.uid()) = user_id);
create policy "own rows: delete" on public.context_entries for delete to authenticated using ((select auth.uid()) = user_id);
revoke all on public.context_entries from anon, authenticated;
grant select, insert, update, delete on public.context_entries to authenticated;
