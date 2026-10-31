-- Preserve the exact server revision acknowledged by each phone snapshot. A replay
-- after any subsequent write must conflict, not silently adopt that newer base.
create table public.session_push_receipts (
  session_id uuid not null,
  user_id uuid not null default auth.uid(),
  client_revision bigint not null,
  server_revision bigint not null,
  primary key (session_id, client_revision),
  foreign key (session_id, user_id) references public.workout_sessions(id, user_id) on delete cascade
);
alter table public.session_push_receipts enable row level security;
create policy "own push receipts" on public.session_push_receipts for all to authenticated
  using ((select auth.uid()) = user_id) with check ((select auth.uid()) = user_id);
revoke all on public.session_push_receipts from anon, authenticated;
grant select, insert, update on public.session_push_receipts to authenticated;

create or replace function public.push_session(payload jsonb, local_revision bigint, base_revision bigint) returns jsonb
language plpgsql security invoker set search_path = '' as $$
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
  select * into current_row from public.workout_sessions where id = v_id for update;
  if found then
    if current_row.client_revision >= local_revision then
      select server_revision into applied_revision from public.session_push_receipts
        where session_id = v_id and client_revision = local_revision;
      if applied_revision is null or applied_revision <> current_row.revision then
        return jsonb_build_object('status', 'conflict', 'session', public.session_snapshot(v_id));
      end if;
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
  insert into public.session_push_receipts (session_id, user_id, client_revision, server_revision)
    select v_id, auth.uid(), local_revision, revision from public.workout_sessions where id = v_id
    on conflict (session_id, client_revision) do update set server_revision = excluded.server_revision;
  return jsonb_build_object('status', 'applied', 'session', public.session_snapshot(v_id));
end $$;

