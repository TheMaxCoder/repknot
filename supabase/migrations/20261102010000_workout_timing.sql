-- Workout timing (task 027): when a session was started/finished and when each set was
-- first logged, recorded by the phone without extra taps. Additive and nullable: existing
-- rows and imports stay null. Writers that omit a timestamp keep the stored value.
alter table public.workout_sessions add column started_at timestamptz, add column finished_at timestamptz;
alter table public.session_sets add column logged_at timestamptz;

-- Phone push (ADR 001): unchanged conflict/replay/receipt logic, nested payload.
create or replace function public.push_session(payload jsonb, local_revision bigint, base_revision bigint) returns jsonb
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
      light = coalesce((payload->>'light')::boolean, current_row.light),
      started_at = coalesce((payload->>'started_at')::timestamptz, current_row.started_at),
      finished_at = coalesce((payload->>'finished_at')::timestamptz, current_row.finished_at),
      source = 'android', client_revision = local_revision
      where id = v_id and user_id = auth.uid();
  else
    if base_revision is not null then
      return jsonb_build_object('status', 'conflict', 'session', null);
    end if;
    insert into public.workout_sessions (id, user_id, performed_on, title, notes, program_id,
      program_workout_key, light, started_at, finished_at, source, client_revision)
    values (v_id, auth.uid(), (payload->>'performed_on')::date, payload->>'title', payload->>'notes',
      (payload->>'program_id')::uuid, payload->>'program_workout_key',
      coalesce((payload->>'light')::boolean, false), (payload->>'started_at')::timestamptz,
      (payload->>'finished_at')::timestamptz, 'android', local_revision);
  end if;
  perform public.replace_session_exercises(v_id, payload->'exercises');
  insert into public.session_push_receipts (session_id, user_id, client_revision, server_revision)
    select v_id, auth.uid(), local_revision, revision from public.workout_sessions
    where id = v_id and user_id = auth.uid()
    on conflict (session_id, client_revision) do update set server_revision = excluded.server_revision;
  return jsonb_build_object('status', 'applied', 'session', public.session_snapshot(v_id));
end $$;

-- Connector save: updates must be based on a revision the caller actually read.
create or replace function public.save_session(payload jsonb) returns jsonb
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
      light = coalesce((payload->>'light')::boolean, current_row.light),
      started_at = coalesce((payload->>'started_at')::timestamptz, current_row.started_at),
      finished_at = coalesce((payload->>'finished_at')::timestamptz, current_row.finished_at),
      source = 'chatgpt' where id = v_id;
  else
    if v_expected is not null then
      return jsonb_build_object('status', 'conflict', 'session', null);
    end if;
    insert into public.workout_sessions (id, performed_on, title, notes, program_id, program_workout_key, light,
      started_at, finished_at, source)
    values (v_id, (payload->>'performed_on')::date, payload->>'title', payload->>'notes',
      (payload->>'program_id')::uuid, payload->>'program_workout_key',
      coalesce((payload->>'light')::boolean, false), (payload->>'started_at')::timestamptz,
      (payload->>'finished_at')::timestamptz, 'chatgpt');
  end if;
  perform public.replace_session_exercises(v_id, payload->'exercises');
  return public.session_snapshot(v_id);
end $$;

-- Exercises and sets: unchanged apart from logged_at.
create or replace function public.replace_session_exercises(p_id uuid, p_exercises jsonb) returns void
language plpgsql security invoker set search_path = '' as $$
declare v_items jsonb; v_logged jsonb;
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
  -- Fix ids and resolve references once, up front; an aggregate evaluates every element.
  select jsonb_agg(x.value || jsonb_build_object(
           'id', coalesce(nullif(x.value->>'id', ''), gen_random_uuid()::text),
           'position', x.ordinality - 1,
           'exercise_id', public.resolve_exercise(x.value->>'exercise'),
           'substituted_for_id', public.resolve_exercise(x.value->>'substituted_for'))
         order by x.ordinality)
    into v_items
    from jsonb_array_elements(p_exercises) with ordinality as x(value, ordinality);

  -- Writers that omit logged_at (connector, CLI) keep the stored value for the same set id.
  select coalesce(jsonb_object_agg(id::text, logged_at), '{}'::jsonb) into v_logged
    from public.session_sets where session_id = p_id and user_id = auth.uid() and logged_at is not null;
  delete from public.session_exercises where session_id = p_id and user_id = auth.uid();
  insert into public.session_exercises (id, session_id, user_id, position, exercise_id, exercise_name,
    planned_sets, planned_rep_min, planned_rep_max, planned_rir_min, planned_rir_max, planned_load,
    planned_unit, substituted_for, notes, source_ref, raw)
  select (i->>'id')::uuid, p_id, auth.uid(), (i->>'position')::integer, x.id,
    coalesce(nullif(trim(i->>'exercise_name'), ''), x.name),
    (i->>'planned_sets')::integer, (i->>'planned_rep_min')::integer, (i->>'planned_rep_max')::integer,
    (i->>'planned_rir_min')::integer, (i->>'planned_rir_max')::integer, (i->>'planned_load')::numeric,
    i->>'planned_unit', (i->>'substituted_for_id')::uuid, i->>'notes', i->>'source_ref', i->'raw'
  from jsonb_array_elements(coalesce(v_items, '[]'::jsonb)) as i
  join public.exercises x on x.id = (i->>'exercise_id')::uuid and x.user_id = auth.uid();
  if (select count(*) from public.session_exercises where session_id = p_id and user_id = auth.uid())
     <> jsonb_array_length(coalesce(v_items, '[]'::jsonb)) then
    raise exception 'exercise rows did not match the payload' using errcode = '22023';
  end if;

  insert into public.session_sets (id, session_exercise_id, session_id, user_id, position, load, unit,
    reps, duration_s, rir, notes, source_ref, raw, logged_at)
  select coalesce(nullif(s.value->>'id', '')::uuid, gen_random_uuid()), (i->>'id')::uuid, p_id, auth.uid(),
    (s.ordinality - 1)::integer, (s.value->>'load')::numeric, s.value->>'unit', (s.value->>'reps')::numeric,
    (s.value->>'duration_s')::integer, (s.value->>'rir')::integer, s.value->>'notes',
    s.value->>'source_ref', s.value->'raw',
    coalesce((s.value->>'logged_at')::timestamptz, (v_logged->>(s.value->>'id'))::timestamptz)
  from jsonb_array_elements(coalesce(v_items, '[]'::jsonb)) as i
  cross join lateral jsonb_array_elements(i->'sets') with ordinality as s(value, ordinality);
end $$;
