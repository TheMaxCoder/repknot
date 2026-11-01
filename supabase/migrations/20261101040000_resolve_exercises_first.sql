-- Resolve every exercise reference before inserting. Resolving inside a join let the
-- planner skip resolve_exercise when the joined table had no visible rows, silently
-- dropping exercises with unknown or foreign slugs instead of raising.
create or replace function public.replace_session_exercises(p_id uuid, p_exercises jsonb) returns void
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
  -- Fix ids and resolve references once, up front; an aggregate evaluates every element.
  select jsonb_agg(x.value || jsonb_build_object(
           'id', coalesce(nullif(x.value->>'id', ''), gen_random_uuid()::text),
           'position', x.ordinality - 1,
           'exercise_id', public.resolve_exercise(x.value->>'exercise'),
           'substituted_for_id', public.resolve_exercise(x.value->>'substituted_for'))
         order by x.ordinality)
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
    i->>'planned_unit', (i->>'substituted_for_id')::uuid, i->>'notes', i->>'source_ref', i->'raw'
  from jsonb_array_elements(coalesce(v_items, '[]'::jsonb)) as i
  join public.exercises x on x.id = (i->>'exercise_id')::uuid and x.user_id = auth.uid();
  if (select count(*) from public.session_exercises where session_id = p_id and user_id = auth.uid())
     <> jsonb_array_length(coalesce(v_items, '[]'::jsonb)) then
    raise exception 'exercise rows did not match the payload' using errcode = '22023';
  end if;

  insert into public.session_sets (id, session_exercise_id, session_id, user_id, position, load, unit,
    reps, duration_s, rir, notes, source_ref, raw)
  select coalesce(nullif(s.value->>'id', '')::uuid, gen_random_uuid()), (i->>'id')::uuid, p_id, auth.uid(),
    (s.ordinality - 1)::integer, (s.value->>'load')::numeric, s.value->>'unit', (s.value->>'reps')::numeric,
    (s.value->>'duration_s')::integer, (s.value->>'rir')::integer, s.value->>'notes',
    s.value->>'source_ref', s.value->'raw'
  from jsonb_array_elements(coalesce(v_items, '[]'::jsonb)) as i
  cross join lateral jsonb_array_elements(i->'sets') with ordinality as s(value, ordinality);
end $$;
