-- Atomic session snapshots. Functions run as the caller, so table RLS still applies.
create function public.session_snapshot(p_id uuid) returns jsonb
language sql stable security invoker set search_path = '' as $$
  select to_jsonb(s) || jsonb_build_object('sets', coalesce(
    (select jsonb_agg(to_jsonb(t) order by t.position) from public.session_sets t where t.session_id = s.id), '[]'::jsonb))
  from public.workout_sessions s where s.id = p_id
$$;

create function public.replace_session_sets(p_id uuid, p_sets jsonb) returns void
language plpgsql security invoker set search_path = '' as $$
begin
  if jsonb_typeof(p_sets) is distinct from 'array' then
    raise exception 'sets must be an array' using errcode = '22023';
  end if;
  delete from public.session_sets where session_id = p_id;
  insert into public.session_sets (id, session_id, position, exercise_name, load, unit, reps, rir, notes)
  select coalesce(nullif(x.value->>'id', '')::uuid, gen_random_uuid()), p_id,
    (x.ordinality - 1)::integer, x.value->>'exercise_name', (x.value->>'load')::numeric,
    x.value->>'unit', (x.value->>'reps')::integer, (x.value->>'rir')::integer, x.value->>'notes'
  from jsonb_array_elements(p_sets) with ordinality as x(value, ordinality);
end $$;

create function public.push_session(payload jsonb, local_revision bigint, base_revision bigint) returns jsonb
language plpgsql security invoker set search_path = '' as $$
declare
  v_id uuid := (payload->>'id')::uuid;
  current_row public.workout_sessions%rowtype;
begin
  if auth.uid() is null or v_id is null or local_revision is null or local_revision < 1 then
    raise exception 'authenticated user, id and positive local revision required' using errcode = '22023';
  end if;
  -- Serialize competing first inserts too, including an absent row.
  perform pg_advisory_xact_lock(hashtextextended(v_id::text, 0));
  select * into current_row from public.workout_sessions where id = v_id for update;
  if found then
    if current_row.client_revision >= local_revision then
      return jsonb_build_object('status', 'replay', 'session', public.session_snapshot(v_id));
    end if;
    if current_row.revision is distinct from base_revision then
      return jsonb_build_object('status', 'conflict', 'session', public.session_snapshot(v_id));
    end if;
    update public.workout_sessions set performed_on = (payload->>'performed_on')::date,
      title = payload->>'title', notes = payload->>'notes', source = 'android',
      client_revision = local_revision where id = v_id;
  else
    if base_revision is not null then
      return jsonb_build_object('status', 'conflict', 'session', null);
    end if;
    insert into public.workout_sessions(id, performed_on, title, notes, source, client_revision)
    values (v_id, (payload->>'performed_on')::date, payload->>'title', payload->>'notes', 'android', local_revision);
  end if;
  perform public.replace_session_sets(v_id, payload->'sets');
  return jsonb_build_object('status', 'applied', 'session', public.session_snapshot(v_id));
end $$;

create function public.save_session(payload jsonb) returns jsonb
language plpgsql security invoker set search_path = '' as $$
declare
  v_id uuid := coalesce(nullif(payload->>'ref', '')::uuid, gen_random_uuid());
  v_exists boolean;
begin
  if auth.uid() is null then
    raise exception 'authenticated user required' using errcode = '42501';
  end if;
  perform pg_advisory_xact_lock(hashtextextended(v_id::text, 0));
  select true into v_exists from public.workout_sessions where id = v_id for update;
  if v_exists then
    update public.workout_sessions set performed_on = (payload->>'performed_on')::date,
      title = payload->>'title', notes = payload->>'notes', source = 'chatgpt' where id = v_id;
  else
    insert into public.workout_sessions(id, performed_on, title, notes, source)
    values (v_id, (payload->>'performed_on')::date, payload->>'title', payload->>'notes', 'chatgpt');
  end if;
  perform public.replace_session_sets(v_id, payload->'sets');
  return public.session_snapshot(v_id);
end $$;

revoke all on function public.session_snapshot(uuid), public.replace_session_sets(uuid, jsonb),
  public.push_session(jsonb, bigint, bigint), public.save_session(jsonb) from public, anon;
grant execute on function public.session_snapshot(uuid), public.replace_session_sets(uuid, jsonb),
  public.push_session(jsonb, bigint, bigint), public.save_session(jsonb) to authenticated;
