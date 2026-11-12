-- Coach notes (task 034, ADR 005): short briefs written after a training review (by a coach
-- tool through the CLI) and shown read-only on the phone's Home. Append-only: a newer brief
-- supersedes the previous one; history is kept for continuity.
create table public.coach_notes (
  id           uuid primary key default gen_random_uuid(),
  user_id      uuid not null default auth.uid() references auth.users (id) on delete cascade,
  published_at timestamptz not null default now(),
  based_on     date,                                   -- latest workout date the review considered
  headline     text not null check (length(trim(headline)) between 1 and 120),
  -- [{ "kind": progress|hold|technique|recovery|focus, "text": "...", "exercise_id": uuid? }]
  items        jsonb not null check (
                 jsonb_typeof(items) = 'array'
                 and jsonb_array_length(items) between 1 and 8
                 and not jsonb_path_exists(items, '$[*] ? (!(@.kind == "progress" || @.kind == "hold" || @.kind == "technique" || @.kind == "recovery" || @.kind == "focus"))')
                 and not jsonb_path_exists(items, '$[*] ? (!(@.text like_regex "^\\S.{0,199}$" flag "s"))')
               ),
  source       text not null default 'unknown'
               check (source in ('android', 'web', 'chatgpt', 'cli', 'import', 'unknown')),
  created_at   timestamptz not null default now()
);
create index coach_notes_user_published on public.coach_notes (user_id, published_at desc);

alter table public.coach_notes enable row level security;
create policy "own rows: select" on public.coach_notes for select to authenticated using ((select auth.uid()) = user_id);
create policy "own rows: insert" on public.coach_notes for insert to authenticated with check ((select auth.uid()) = user_id);
create policy "own rows: delete" on public.coach_notes for delete to authenticated using ((select auth.uid()) = user_id);
revoke all on public.coach_notes from anon, authenticated;
grant select, insert, delete on public.coach_notes to authenticated;
