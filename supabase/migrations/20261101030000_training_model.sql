-- Training data model (ADR 002): exercises, immutable program versions, sessions with
-- per-exercise prescription snapshots, extended set measures, import refs, check-ins.
--
-- Building phase: dev data is synthetic, so the session tables are dropped and recreated
-- in their new shape rather than migrated (owner-approved 2026-11-01). Sync semantics from
-- ADR 001 and the earlier guard/receipt migrations are kept unchanged; only the snapshot
-- shape changes from flat sets to exercises → sets.

drop function public.save_session(jsonb), public.push_session(jsonb, bigint, bigint),
  public.replace_session_sets(uuid, jsonb), public.session_snapshot(uuid);
drop table public.session_push_receipts, public.session_sets, public.workout_sessions;
-- Kept and reused: public.set_session_revision(), public.touch_parent_session().

create domain public.load_unit as text check (value in ('kg', 'lb', 'plates', 'bodyweight'));
-- kg/lb: weight. plates: machine stack pin number ("9p"), never converted. bodyweight: no load.

-- ── Exercises ────────────────────────────────────────────────────────────────
create table public.exercises (
  id           uuid primary key default gen_random_uuid(),
  user_id      uuid not null default auth.uid() references auth.users (id) on delete cascade,
  slug         text not null check (slug ~ '^[a-z0-9]+(-[a-z0-9]+)*$'),  -- stable import key
  name         text not null check (length(trim(name)) > 0),
  default_unit public.load_unit,
  notes        text,
  archived_at  timestamptz,
  created_at   timestamptz not null default now(),
  unique (user_id, slug),
  unique (id, user_id)
);

-- ── Programs: immutable versions, written only by publish_program ───────────
create table public.programs (
  id          uuid primary key default gen_random_uuid(),
  user_id     uuid not null default auth.uid() references auth.users (id) on delete cascade,
  key         text not null check (key ~ '^[a-z0-9]+(-[a-z0-9]+)*$'),  -- program family, e.g. ppl
  name        text not null check (length(trim(name)) > 0),
  version     integer not null check (version > 0),
  starts_on   date not null,
  rules       text,                                 -- one rule per line
  details     jsonb not null default '{}'::jsonb,   -- goal, review date, source, … (informational)
  supersedes  uuid,
  is_active   boolean not null default false,
  created_at  timestamptz not null default now(),
  unique (user_id, key, version),
  unique (id, user_id),
  foreign key (supersedes, user_id) references public.programs (id, user_id)
);
create unique index programs_one_active on public.programs (user_id) where is_active;

create table public.program_workouts (
  id                uuid primary key default gen_random_uuid(),
  program_id        uuid not null,
  user_id           uuid not null default auth.uid(),
  key               text not null check (key ~ '^[a-z0-9]+(-[a-z0-9]+)*$'),
  name              text not null check (length(trim(name)) > 0),
  sequence_position integer not null check (sequence_position >= 0),
  default_weekday   smallint check (default_weekday between 1 and 7),  -- ISO, 1 = Monday
  foreign key (program_id, user_id) references public.programs (id, user_id) on delete cascade,
  unique (program_id, key),
  unique (program_id, sequence_position),
  unique (id, user_id)
);

create table public.program_exercises (
  id          uuid primary key default gen_random_uuid(),
  workout_id  uuid not null,
  user_id     uuid not null default auth.uid(),
  position    integer not null check (position >= 0),
  exercise_id uuid not null,
  sets        integer not null check (sets > 0),
  rep_min     integer check (rep_min > 0),
  rep_max     integer check (rep_max >= rep_min),
  rir_min     integer check (rir_min >= 0),
  rir_max     integer check (rir_max >= rir_min),
  rest_s_min  integer check (rest_s_min > 0),
  rest_s_max  integer check (rest_s_max >= rest_s_min),
  load        numeric(7, 2) check (load >= 0),
  unit        public.load_unit not null,
  notes       text,
  check ((rep_min is null) = (rep_max is null)),    -- null range: "stop at ~2 RIR"
  check ((rir_min is null) = (rir_max is null)),
  check ((rest_s_min is null) = (rest_s_max is null)),
  check (unit <> 'bodyweight' or load is null),
  foreign key (workout_id, user_id) references public.program_workouts (id, user_id) on delete cascade,
  foreign key (exercise_id, user_id) references public.exercises (id, user_id),
  unique (workout_id, position)
);

-- Versions never change; only programs.is_active may flip. Deletes (account removal
-- cascades) are not blocked here; clients have no delete grant.
create function public.guard_program_immutable() returns trigger
language plpgsql set search_path = '' as $$
begin
  if tg_table_name = 'programs' and (to_jsonb(new) - 'is_active') = (to_jsonb(old) - 'is_active') then
    return new;
  end if;
  raise exception 'program versions are immutable; publish a new version' using errcode = '55000';
end $$;
create trigger programs_immutable before update on public.programs
  for each row execute function public.guard_program_immutable();
create trigger program_workouts_immutable before update on public.program_workouts
  for each row execute function public.guard_program_immutable();
create trigger program_exercises_immutable before update on public.program_exercises
  for each row execute function public.guard_program_immutable();

-- ── Sessions → session exercises (prescription snapshot) → sets ─────────────
create table public.workout_sessions (
  id                  uuid primary key default gen_random_uuid(),  -- sync key; phone supplies its own
  number              bigint generated always as identity unique,  -- friendly "#42"; never a sync key
  user_id             uuid not null default auth.uid() references auth.users (id) on delete cascade,
  performed_on        date not null,                               -- not unique: several per day allowed
  title               text,
  notes               text,
  program_id          uuid,                                        -- null: ad-hoc or imported
  program_workout_key text,
  revision            bigint not null default 1,                   -- bumped by trigger on any change
  client_revision     bigint,                                      -- last phone local_revision applied
  source              text not null default 'unknown'
                      check (source in ('android', 'web', 'chatgpt', 'cli', 'import', 'unknown')),
  source_ref          text,                                        -- e.g. Airtable record id
  raw                 jsonb,                                       -- ambiguous source values + reason
  created_at          timestamptz not null default now(),
  updated_at          timestamptz not null default now(),
  check ((program_id is null) = (program_workout_key is null)),
  foreign key (program_id, user_id) references public.programs (id, user_id),
  foreign key (program_id, program_workout_key) references public.program_workouts (program_id, key),
  unique (id, user_id),
  unique (user_id, source_ref)                                     -- nulls distinct: only refs are unique
);
create index workout_sessions_user_date on public.workout_sessions (user_id, performed_on desc);

create table public.session_exercises (
  id              uuid primary key default gen_random_uuid(),
  session_id      uuid not null,
  user_id         uuid not null default auth.uid(),
  position        integer not null check (position >= 0),
  exercise_id     uuid not null,
  exercise_name   text not null check (length(trim(exercise_name)) > 0),  -- name at the time
  planned_sets    integer check (planned_sets > 0),
  planned_rep_min integer check (planned_rep_min > 0),
  planned_rep_max integer check (planned_rep_max >= planned_rep_min),
  planned_rir_min integer check (planned_rir_min >= 0),
  planned_rir_max integer check (planned_rir_max >= planned_rir_min),
  planned_load    numeric(7, 2) check (planned_load >= 0),
  planned_unit    public.load_unit,
  substituted_for uuid,                                        -- prescribed exercise replaced
  notes           text,
  source_ref      text,
  raw             jsonb,
  foreign key (session_id, user_id) references public.workout_sessions (id, user_id) on delete cascade,
  foreign key (exercise_id, user_id) references public.exercises (id, user_id),
  foreign key (substituted_for, user_id) references public.exercises (id, user_id),
  unique (session_id, position) deferrable initially deferred,
  unique (id, session_id, user_id),
  unique (user_id, source_ref)
);

create table public.session_sets (
  id                  uuid primary key default gen_random_uuid(),
  session_exercise_id uuid not null,
  session_id          uuid not null,
  user_id             uuid not null default auth.uid(),
  position            integer not null check (position >= 0),
  load                numeric(7, 2) check (load >= 0),
  unit                public.load_unit,
  reps                numeric(4, 1) check (reps > 0 and reps * 2 = trunc(reps * 2)),  -- 0.5 steps
  duration_s          integer check (duration_s > 0),
  rir                 integer check (rir >= 0),                 -- reported range: lower bound
  notes               text,
  source_ref          text,
  raw                 jsonb,
  -- Imported rows that keep their raw original may be incomplete; everything else is a
  -- real measurement: a unit, reps or a duration, and a load unless bodyweight.
  constraint session_sets_complete check (raw is not null or (
    unit is not null and (reps is not null or duration_s is not null)
    and (load is null) = (unit = 'bodyweight'))),
  foreign key (session_exercise_id, session_id, user_id)
    references public.session_exercises (id, session_id, user_id) on delete cascade,
  unique (session_exercise_id, position) deferrable initially deferred,
  unique (user_id, source_ref)
);

create trigger workout_sessions_revision
  before insert or update on public.workout_sessions
  for each row execute function public.set_session_revision();
create trigger session_exercises_touch_session
  after insert or update or delete on public.session_exercises
  for each row execute function public.touch_parent_session();
create trigger session_sets_touch_session
  after insert or update or delete on public.session_sets
  for each row execute function public.touch_parent_session();

-- Exact server revision acknowledged per phone snapshot (unchanged from earlier migrations).
create table public.session_push_receipts (
  session_id      uuid not null,
  user_id         uuid not null default auth.uid(),
  client_revision bigint not null,
  server_revision bigint not null,
  primary key (session_id, client_revision),
  foreign key (session_id, user_id) references public.workout_sessions (id, user_id) on delete cascade
);

-- ── Check-ins ────────────────────────────────────────────────────────────────
create table public.checkins (
  id             uuid primary key default gen_random_uuid(),
  user_id        uuid not null default auth.uid() references auth.users (id) on delete cascade,
  checked_on     date not null,
  weight_kg      numeric(5, 2) check (weight_kg > 0),
  waist_cm       numeric(5, 1) check (waist_cm > 0),
  chest_cm       numeric(5, 1) check (chest_cm > 0),
  arms_cm        numeric(5, 1) check (arms_cm > 0),
  arms_flexed_cm numeric(5, 1) check (arms_flexed_cm > 0),
  legs_cm        numeric(5, 1) check (legs_cm > 0),
  height_cm      numeric(5, 1) check (height_cm > 0),
  notes          text,
  source         text not null default 'unknown'
                 check (source in ('android', 'web', 'chatgpt', 'cli', 'import', 'unknown')),
  source_ref     text,
  raw            jsonb,
  created_at     timestamptz not null default now(),
  unique (user_id, source_ref)
);

-- ── Access: RLS everywhere, owner-only; programs and receipts read-only ─────
do $$
declare t text;
begin
  foreach t in array array['exercises', 'workout_sessions', 'session_exercises', 'session_sets', 'checkins'] loop
    execute format('alter table public.%I enable row level security', t);
    execute format('create policy "own rows: select" on public.%I for select to authenticated using ((select auth.uid()) = user_id)', t);
    execute format('create policy "own rows: insert" on public.%I for insert to authenticated with check ((select auth.uid()) = user_id)', t);
    execute format('create policy "own rows: update" on public.%I for update to authenticated using ((select auth.uid()) = user_id) with check ((select auth.uid()) = user_id)', t);
    execute format('create policy "own rows: delete" on public.%I for delete to authenticated using ((select auth.uid()) = user_id)', t);
    execute format('revoke all on public.%I from anon, authenticated', t);
    execute format('grant select, insert, update, delete on public.%I to authenticated', t);
  end loop;
  foreach t in array array['programs', 'program_workouts', 'program_exercises', 'session_push_receipts'] loop
    execute format('alter table public.%I enable row level security', t);
    execute format('create policy "own rows: select" on public.%I for select to authenticated using ((select auth.uid()) = user_id)', t);
    execute format('revoke all on public.%I from anon, authenticated', t);
    execute format('grant select on public.%I to authenticated', t);
  end loop;
end $$;

-- ── Functions ────────────────────────────────────────────────────────────────
-- Helpers are invoker functions but are also called from security-definer functions,
-- so every lookup is explicitly scoped to auth.uid().

-- An exercise reference is a uuid or a slug of the caller's own exercise.
create function public.resolve_exercise(p_ref text) returns uuid
language plpgsql stable security invoker set search_path = '' as $$
declare v_id uuid;
begin
  if p_ref is null or p_ref = '' then
    return null;
  end if;
  if p_ref ~* '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$' then
    select id into v_id from public.exercises where id = p_ref::uuid and user_id = auth.uid();
  else
    select id into v_id from public.exercises where slug = p_ref and user_id = auth.uid();
  end if;
  if v_id is null then
    raise exception 'unknown exercise: %', p_ref using errcode = '22023';
  end if;
  return v_id;
end $$;

create function public.session_snapshot(p_id uuid) returns jsonb
language sql stable security invoker set search_path = '' as $$
  select to_jsonb(s) || jsonb_build_object('exercises', coalesce((
      select jsonb_agg(to_jsonb(e) || jsonb_build_object('sets', coalesce((
          select jsonb_agg(to_jsonb(t) order by t.position)
          from public.session_sets t where t.session_exercise_id = e.id), '[]'::jsonb))
        order by e.position)
      from public.session_exercises e where e.session_id = s.id), '[]'::jsonb))
  from public.workout_sessions s where s.id = p_id and s.user_id = auth.uid()
$$;

-- Replaces a session's exercises and sets with the payload, in payload order.
-- Exercise item: exercise (slug|uuid, required), optional id, exercise_name (defaults to the
-- exercise's name), planned_*, substituted_for (slug|uuid), notes, source_ref, raw, sets[].
-- Set item: optional id, load, unit, reps, duration_s, rir, notes, source_ref, raw.
create function public.replace_session_exercises(p_id uuid, p_exercises jsonb) returns void
language plpgsql security invoker set search_path = '' as $$
declare v_items jsonb;
begin
  if jsonb_typeof(p_exercises) is distinct from 'array' then
    raise exception 'exercises must be an array' using errcode = '22023';
  end if;
  if exists (select 1 from jsonb_array_elements(p_exercises) x
             where jsonb_typeof(x) is distinct from 'object'
                or coalesce(x->>'exercise', '') = ''
                or jsonb_typeof(x->'sets') is distinct from 'array') then
    raise exception 'each exercise needs an exercise reference and a sets array' using errcode = '22023';
  end if;
  -- Fix ids up front so the exercise and set inserts agree on them.
  select jsonb_agg(x.value || jsonb_build_object(
           'id', coalesce(nullif(x.value->>'id', ''), gen_random_uuid()::text),
           'position', x.ordinality - 1) order by x.ordinality)
    into v_items
    from jsonb_array_elements(p_exercises) with ordinality as x(value, ordinality);

  delete from public.session_exercises where session_id = p_id and user_id = auth.uid();
  insert into public.session_exercises (id, session_id, user_id, position, exercise_id, exercise_name,
    planned_sets, planned_rep_min, planned_rep_max, planned_rir_min, planned_rir_max, planned_load,
    planned_unit, substituted_for, notes, source_ref, raw)
  select (i->>'id')::uuid, p_id, auth.uid(), (i->>'position')::integer, x.id,
    coalesce(nullif(trim(i->>'exercise_name'), ''), x.name),
    (i->>'planned_sets')::integer, (i->>'planned_rep_min')::integer, (i->>'planned_rep_max')::integer,
    (i->>'planned_rir_min')::integer, (i->>'planned_rir_max')::integer, (i->>'planned_load')::numeric,
    i->>'planned_unit', public.resolve_exercise(i->>'substituted_for'), i->>'notes', i->>'source_ref', i->'raw'
  from jsonb_array_elements(coalesce(v_items, '[]'::jsonb)) as i
  cross join lateral (select public.resolve_exercise(i->>'exercise') as id) r
  join public.exercises x on x.id = r.id;

  insert into public.session_sets (id, session_exercise_id, session_id, user_id, position, load, unit,
    reps, duration_s, rir, notes, source_ref, raw)
  select coalesce(nullif(s.value->>'id', '')::uuid, gen_random_uuid()), (i->>'id')::uuid, p_id, auth.uid(),
    (s.ordinality - 1)::integer, (s.value->>'load')::numeric, s.value->>'unit', (s.value->>'reps')::numeric,
    (s.value->>'duration_s')::integer, (s.value->>'rir')::integer, s.value->>'notes',
    s.value->>'source_ref', s.value->'raw'
  from jsonb_array_elements(coalesce(v_items, '[]'::jsonb)) as i
  cross join lateral jsonb_array_elements(i->'sets') with ordinality as s(value, ordinality);
end $$;

-- Phone push (ADR 001): unchanged conflict/replay/receipt logic, nested payload.
create function public.push_session(payload jsonb, local_revision bigint, base_revision bigint) returns jsonb
language plpgsql security definer set search_path = '' as $$
declare
  v_id uuid := (payload->>'id')::uuid;
  current_row public.workout_sessions%rowtype;
  applied_revision bigint;
begin
  if auth.uid() is null or v_id is null or local_revision is null or local_revision < 1 then
    raise exception 'authenticated user, id and positive local revision required' using errcode = '22023';
  end if;
  -- Serialize competing first inserts too, including an absent row.
  perform pg_advisory_xact_lock(hashtextextended(v_id::text, 0));
  select * into current_row from public.workout_sessions where id = v_id and user_id = auth.uid() for update;
  if found then
    if current_row.client_revision >= local_revision then
      select server_revision into applied_revision from public.session_push_receipts
        where session_id = v_id and user_id = auth.uid() and client_revision = local_revision;
      if applied_revision is null or applied_revision <> current_row.revision then
        return jsonb_build_object('status', 'conflict', 'session', public.session_snapshot(v_id));
      end if;
      return jsonb_build_object('status', 'replay', 'session', public.session_snapshot(v_id));
    end if;
    if current_row.revision is distinct from base_revision then
      return jsonb_build_object('status', 'conflict', 'session', public.session_snapshot(v_id));
    end if;
    update public.workout_sessions set performed_on = (payload->>'performed_on')::date,
      title = payload->>'title', notes = payload->>'notes',
      program_id = (payload->>'program_id')::uuid, program_workout_key = payload->>'program_workout_key',
      source = 'android', client_revision = local_revision
      where id = v_id and user_id = auth.uid();
  else
    if base_revision is not null then
      return jsonb_build_object('status', 'conflict', 'session', null);
    end if;
    insert into public.workout_sessions (id, user_id, performed_on, title, notes, program_id,
      program_workout_key, source, client_revision)
    values (v_id, auth.uid(), (payload->>'performed_on')::date, payload->>'title', payload->>'notes',
      (payload->>'program_id')::uuid, payload->>'program_workout_key', 'android', local_revision);
  end if;
  perform public.replace_session_exercises(v_id, payload->'exercises');
  insert into public.session_push_receipts (session_id, user_id, client_revision, server_revision)
    select v_id, auth.uid(), local_revision, revision from public.workout_sessions
    where id = v_id and user_id = auth.uid()
    on conflict (session_id, client_revision) do update set server_revision = excluded.server_revision;
  return jsonb_build_object('status', 'applied', 'session', public.session_snapshot(v_id));
end $$;

-- Connector save: updates must be based on a revision the caller actually read.
create function public.save_session(payload jsonb) returns jsonb
language plpgsql security invoker set search_path = '' as $$
declare
  v_id uuid := coalesce(nullif(payload->>'ref', '')::uuid, gen_random_uuid());
  v_expected bigint := nullif(payload->>'expected_revision', '')::bigint;
  current_row public.workout_sessions%rowtype;
begin
  if auth.uid() is null then
    raise exception 'authenticated user required' using errcode = '42501';
  end if;
  perform pg_advisory_xact_lock(hashtextextended(v_id::text, 0));
  select * into current_row from public.workout_sessions where id = v_id for update;
  if found then
    if v_expected is null or v_expected is distinct from current_row.revision then
      return jsonb_build_object('status', 'conflict', 'session', public.session_snapshot(v_id));
    end if;
    update public.workout_sessions set performed_on = (payload->>'performed_on')::date,
      title = payload->>'title', notes = payload->>'notes',
      program_id = (payload->>'program_id')::uuid, program_workout_key = payload->>'program_workout_key',
      source = 'chatgpt' where id = v_id;
  else
    if v_expected is not null then
      return jsonb_build_object('status', 'conflict', 'session', null);
    end if;
    insert into public.workout_sessions (id, performed_on, title, notes, program_id, program_workout_key, source)
    values (v_id, (payload->>'performed_on')::date, payload->>'title', payload->>'notes',
      (payload->>'program_id')::uuid, payload->>'program_workout_key', 'chatgpt');
  end if;
  perform public.replace_session_exercises(v_id, payload->'exercises');
  return public.session_snapshot(v_id);
end $$;

-- Publishes one complete program version from the repo's program JSON (format 1, e.g.
-- private-data/programs/*.json). Exercises are referenced by the caller's slugs.
-- Definer: clients cannot write program tables directly; every row is the caller's.
create function public.publish_program(payload jsonb, activate boolean default true) returns jsonb
language plpgsql security definer set search_path = '' as $$
declare
  v_uid uuid := auth.uid();
  v_id uuid := gen_random_uuid();
  v_key text := payload->>'program';
  v_version integer := (payload->>'version')::integer;
  v_workouts jsonb := payload->'workouts';
  v_sequence jsonb := payload->'sequence';
  v_previous uuid;
  v_missing text;
begin
  if v_uid is null then
    raise exception 'authenticated user required' using errcode = '42501';
  end if;
  if payload->>'format' is distinct from '1' then
    raise exception 'unsupported program format' using errcode = '22023';
  end if;
  if jsonb_typeof(v_workouts) is distinct from 'array' or jsonb_typeof(v_sequence) is distinct from 'array'
     or exists (select 1 from jsonb_array_elements(v_workouts) w
                where jsonb_typeof(w->'exercises') is distinct from 'array') then
    raise exception 'workouts (each with an exercises array) and sequence arrays required' using errcode = '22023';
  end if;
  if (select array_agg(k order by k) from jsonb_array_elements_text(v_sequence) k)
     is distinct from (select array_agg(w->>'key' order by w->>'key') from jsonb_array_elements(v_workouts) w) then
    raise exception 'sequence must list every workout key exactly once' using errcode = '22023';
  end if;
  select string_agg(distinct e->>'exercise', ', ') into v_missing
    from jsonb_array_elements(v_workouts) w, jsonb_array_elements(w->'exercises') e
    where not exists (select 1 from public.exercises x where x.user_id = v_uid and x.slug = e->>'exercise');
  if v_missing is not null then
    raise exception 'unknown exercises: %', v_missing using errcode = '22023';
  end if;

  perform pg_advisory_xact_lock(hashtextextended('program:' || v_uid::text, 0));
  select id into v_previous from public.programs
    where user_id = v_uid and key = v_key order by version desc limit 1;
  if activate then
    update public.programs set is_active = false where user_id = v_uid and is_active;
  end if;
  insert into public.programs (id, user_id, key, name, version, starts_on, rules, details, supersedes, is_active)
  values (v_id, v_uid, v_key, payload->>'name', v_version, (payload->>'starts_on')::date,
    (select string_agg(r, E'\n' order by o) from jsonb_array_elements_text(payload->'rules') with ordinality as x(r, o)),
    payload - array['format', 'program', 'name', 'version', 'starts_on', 'rules', 'sequence', 'default_days', 'workouts'],
    v_previous, activate);

  insert into public.program_workouts (program_id, user_id, key, name, sequence_position, default_weekday)
  select v_id, v_uid, w->>'key', w->>'name', (s.o - 1)::integer,
    (select array_position(array['mon', 'tue', 'wed', 'thu', 'fri', 'sat', 'sun'], d.key)
       from jsonb_each_text(coalesce(payload->'default_days', '{}'::jsonb)) d
       where d.value = w->>'key' order by 1 limit 1)
  from jsonb_array_elements(v_workouts) w
  join jsonb_array_elements_text(v_sequence) with ordinality as s(k, o) on s.k = w->>'key';

  insert into public.program_exercises (workout_id, user_id, position, exercise_id, sets, rep_min, rep_max,
    rir_min, rir_max, rest_s_min, rest_s_max, load, unit, notes)
  select pw.id, v_uid, (e.o - 1)::integer, x.id, (e.v->>'sets')::integer,
    (e.v->'reps'->>0)::integer, (e.v->'reps'->>1)::integer,
    (e.v->'rir'->>0)::integer, (e.v->'rir'->>1)::integer,
    (e.v->'rest_s'->>0)::integer, (e.v->'rest_s'->>1)::integer,
    (e.v->>'load')::numeric, e.v->>'unit', e.v->>'notes'
  from jsonb_array_elements(v_workouts) w
  join public.program_workouts pw on pw.program_id = v_id and pw.key = w->>'key'
  cross join lateral jsonb_array_elements(w->'exercises') with ordinality as e(v, o)
  join public.exercises x on x.user_id = v_uid and x.slug = e.v->>'exercise';

  return jsonb_build_object('id', v_id, 'key', v_key, 'version', v_version, 'is_active', activate,
    'supersedes', v_previous);
end $$;

revoke all on function public.resolve_exercise(text), public.session_snapshot(uuid),
  public.replace_session_exercises(uuid, jsonb), public.push_session(jsonb, bigint, bigint),
  public.save_session(jsonb), public.publish_program(jsonb, boolean),
  public.guard_program_immutable() from public, anon;
grant execute on function public.resolve_exercise(text), public.session_snapshot(uuid),
  public.replace_session_exercises(uuid, jsonb), public.push_session(jsonb, bigint, bigint),
  public.save_session(jsonb), public.publish_program(jsonb, boolean) to authenticated;
