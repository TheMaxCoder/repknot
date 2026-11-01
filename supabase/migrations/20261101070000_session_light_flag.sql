-- Light / modified day (task 019 step 4): the owner marks a session as intentionally
-- light (deload, injury, short on time) so progress statuses ignore it. Additive only:
-- existing rows default to false. Clients that omit `light` keep the stored value.
alter table public.workout_sessions add column light boolean not null default false;

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
      source = 'android', client_revision = local_revision
      where id = v_id and user_id = auth.uid();
  else
    if base_revision is not null then
      return jsonb_build_object('status', 'conflict', 'session', null);
    end if;
    insert into public.workout_sessions (id, user_id, performed_on, title, notes, program_id,
      program_workout_key, light, source, client_revision)
    values (v_id, auth.uid(), (payload->>'performed_on')::date, payload->>'title', payload->>'notes',
      (payload->>'program_id')::uuid, payload->>'program_workout_key',
      coalesce((payload->>'light')::boolean, false), 'android', local_revision);
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
      source = 'chatgpt' where id = v_id;
  else
    if v_expected is not null then
      return jsonb_build_object('status', 'conflict', 'session', null);
    end if;
    insert into public.workout_sessions (id, performed_on, title, notes, program_id, program_workout_key, light, source)
    values (v_id, (payload->>'performed_on')::date, payload->>'title', payload->>'notes',
      (payload->>'program_id')::uuid, payload->>'program_workout_key',
      coalesce((payload->>'light')::boolean, false), 'chatgpt');
  end if;
  perform public.replace_session_exercises(v_id, payload->'exercises');
  return public.session_snapshot(v_id);
end $$;
