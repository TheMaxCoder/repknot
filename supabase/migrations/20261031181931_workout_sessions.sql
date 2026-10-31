-- Workout sessions and their sets. Design: docs/decisions/001-sync-model.md.
-- Every table: RLS on, owner-only policies, no anon access.

create table public.workout_sessions (
  id              uuid primary key default gen_random_uuid(),  -- sync key; phone supplies its own
  number          bigint generated always as identity unique,  -- friendly "#42"; never a sync key
  user_id         uuid not null default auth.uid() references auth.users (id) on delete cascade,
  performed_on    date not null,                               -- not unique: several per day allowed
  title           text,
  notes           text,
  revision        bigint not null default 1,                   -- bumped by trigger on any change
  client_revision bigint,                                      -- last phone local_revision applied
  source          text not null default 'unknown'
                  check (source in ('android', 'web', 'chatgpt', 'cli', 'import', 'unknown')),
  created_at      timestamptz not null default now(),
  updated_at      timestamptz not null default now(),
  unique (id, user_id)                                         -- target for the sets' ownership FK
);

create index workout_sessions_user_date on public.workout_sessions (user_id, performed_on desc);

create table public.session_sets (
  id            uuid primary key default gen_random_uuid(),
  session_id    uuid not null,
  user_id       uuid not null default auth.uid(),
  position      integer not null check (position >= 0),
  exercise_name text not null check (length(trim(exercise_name)) > 0),
  load          numeric(7, 2) not null check (load >= 0),
  unit          text not null check (unit in ('kg', 'lb')),
  reps          integer not null check (reps > 0),
  rir           integer check (rir >= 0),
  notes         text,
  -- A set always belongs to a session of the same user.
  foreign key (session_id, user_id)
    references public.workout_sessions (id, user_id) on delete cascade,
  unique (session_id, position) deferrable initially deferred
);

-- Revision: set on insert, bumped on every update. Callers cannot choose or skip it.
create function public.set_session_revision() returns trigger
language plpgsql set search_path = '' as $$
begin
  if tg_op = 'INSERT' then
    new.revision := 1;
  else
    new.revision := old.revision + 1;
  end if;
  new.updated_at := now();
  return new;
end $$;

create trigger workout_sessions_revision
  before insert or update on public.workout_sessions
  for each row execute function public.set_session_revision();

-- Any change to a set touches its session, which bumps the session revision.
create function public.touch_parent_session() returns trigger
language plpgsql set search_path = '' as $$
begin
  if tg_op in ('UPDATE', 'DELETE') then
    update public.workout_sessions set updated_at = now() where id = old.session_id;
  end if;
  if tg_op = 'INSERT' or (tg_op = 'UPDATE' and new.session_id is distinct from old.session_id) then
    update public.workout_sessions set updated_at = now() where id = new.session_id;
  end if;
  return null;
end $$;

create trigger session_sets_touch_session
  after insert or update or delete on public.session_sets
  for each row execute function public.touch_parent_session();

alter table public.workout_sessions enable row level security;
alter table public.session_sets enable row level security;

create policy "own sessions: select" on public.workout_sessions
  for select to authenticated using ((select auth.uid()) = user_id);
create policy "own sessions: insert" on public.workout_sessions
  for insert to authenticated with check ((select auth.uid()) = user_id);
create policy "own sessions: update" on public.workout_sessions
  for update to authenticated using ((select auth.uid()) = user_id)
  with check ((select auth.uid()) = user_id);
create policy "own sessions: delete" on public.workout_sessions
  for delete to authenticated using ((select auth.uid()) = user_id);

create policy "own sets: select" on public.session_sets
  for select to authenticated using ((select auth.uid()) = user_id);
create policy "own sets: insert" on public.session_sets
  for insert to authenticated with check ((select auth.uid()) = user_id);
create policy "own sets: update" on public.session_sets
  for update to authenticated using ((select auth.uid()) = user_id)
  with check ((select auth.uid()) = user_id);
create policy "own sets: delete" on public.session_sets
  for delete to authenticated using ((select auth.uid()) = user_id);

-- Replace Supabase's broad default grants with exactly what clients need.
revoke all on public.workout_sessions, public.session_sets from anon, authenticated;
grant select, insert, update, delete on public.workout_sessions, public.session_sets to authenticated;
