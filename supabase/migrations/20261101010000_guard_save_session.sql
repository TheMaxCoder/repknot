-- Updates must be based on a revision the caller actually read. The same lock used
-- by push_session serializes the check and replacement, including first inserts.
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
      title = payload->>'title', notes = payload->>'notes', source = 'chatgpt' where id = v_id;
  else
    if v_expected is not null then
      return jsonb_build_object('status', 'conflict', 'session', null);
    end if;
    insert into public.workout_sessions(id, performed_on, title, notes, source)
    values (v_id, (payload->>'performed_on')::date, payload->>'title', payload->>'notes', 'chatgpt');
  end if;
  perform public.replace_session_sets(v_id, payload->'sets');
  return public.session_snapshot(v_id);
end $$;
