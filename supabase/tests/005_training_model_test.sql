-- Training data model (ADR 002): programs, exercises, nested sessions, measures, import refs.
-- Runs inside a transaction that is rolled back; safe against the dev project.
begin;
create extension if not exists pgtap with schema extensions;
set local search_path = public, extensions;
create temp table tap (n serial, line text);
grant all on tap to public;
grant all on sequence tap_n_seq to public;
create temp table results (key text, value jsonb);
grant all on results to public;
insert into tap(line) select plan(45);
insert into auth.users(id,email) values
  ('00000000-0000-4000-a000-00000000000a','model-a@test.invalid'),
  ('00000000-0000-4000-a000-00000000000b','model-b@test.invalid');

create temp table program_v1 as select '{
  "format": 1, "program": "ppl", "name": "PPL", "version": 1, "starts_on": "2026-11-02",
  "goal": "waist", "rules": ["Rule one", "Rule two"],
  "sequence": ["pull-1", "push-1"],
  "default_days": {"mon": "push-1", "tue": "pull-1", "sun": null},
  "workouts": [
    {"key": "push-1", "name": "Push 1", "exercises": [
      {"exercise": "db-press", "sets": 3, "reps": [8, 12], "rir": [1, 2], "load": 25, "unit": "kg", "rest_s": [120, 180], "notes": "n"},
      {"exercise": "pec-deck", "sets": 3, "reps": [12, 20], "rir": [1, 2], "load": 12, "unit": "plates", "rest_s": [90, 120]}]},
    {"key": "pull-1", "name": "Pull 1", "exercises": [
      {"exercise": "pull-up", "sets": 2, "reps": null, "rir": [2, 2], "load": null, "unit": "bodyweight", "rest_s": [120, 180]}]}
  ]}'::jsonb as p;
grant all on program_v1 to public;

-- ── User A: exercises and program publish ───────────────────────────────────
set local role authenticated;
set local request.jwt.claims = '{"sub":"00000000-0000-4000-a000-00000000000a","role":"authenticated"}';
insert into exercises(slug,name,default_unit) values
  ('db-press','DB press','kg'), ('pec-deck','Pec deck','plates'), ('pull-up','Pull-up','bodyweight'), ('row','Row','plates');
insert into tap(line) select throws_ok($$ insert into exercises(slug,name) values ('db-press','Again') $$,'23505',null,'exercise slug unique per user');
insert into tap(line) select throws_ok($$ insert into exercises(slug,name) values ('Bad Slug','x') $$,'23514',null,'slug format enforced');


insert into results select 'v1', public.publish_program((select p from program_v1));
insert into tap(line) select is((select count(*) from programs where is_active), 1::bigint, 'published version is active');
insert into tap(line) select is((select rules from programs), E'Rule one\nRule two', 'rules stored one per line');
insert into tap(line) select is((select details->>'goal' from programs), 'waist', 'extra fields kept in details');
insert into tap(line) select is((select key from program_workouts where sequence_position = 0), 'pull-1', 'sequence order comes from sequence array');
insert into tap(line) select is((select default_weekday from program_workouts where key = 'push-1'), 1::smallint, 'default weekday mapped (ISO Monday = 1)');
insert into tap(line) select is((select count(*) from program_exercises), 3::bigint, 'all prescriptions inserted');
insert into tap(line) select is((select position from program_exercises pe join exercises x on x.id = pe.exercise_id where x.slug = 'pec-deck'), 1, 'prescription position follows workout order');
insert into tap(line) select ok((select rep_min is null and load is null from program_exercises pe join exercises x on x.id = pe.exercise_id where x.slug = 'pull-up'), 'open rep range and bodyweight load stay null');

insert into tap(line) select throws_ok($$ select public.publish_program((select p from program_v1)) $$, '23505', null, 'same version cannot be published twice');
insert into tap(line) select throws_ok($$ select public.publish_program(jsonb_set((select p from program_v1), '{version}', '9') #- '{sequence,0}') $$, '22023', null, 'sequence must cover every workout');
insert into tap(line) select throws_ok($$ select public.publish_program(jsonb_set((select p from program_v1), '{workouts,1,exercises,0,exercise}', '"nope"')) $$, '22023', null, 'unknown exercise slug rejected');

insert into results select 'v2', public.publish_program(jsonb_set((select p from program_v1), '{version}', '2'));
insert into tap(line) select is((select version from programs where is_active), 2, 'publishing a new version activates it');
insert into tap(line) select is((select count(*) from programs where is_active), 1::bigint, 'only one active program');
insert into tap(line) select is((select value->>'supersedes' from results where key = 'v2'), (select value->>'id' from results where key = 'v1'), 'new version supersedes the previous one');
insert into results select 'v3', public.publish_program(jsonb_set((select p from program_v1), '{version}', '3'), false);
insert into tap(line) select is((select version from programs where is_active), 2, 'inactive publish leaves the active version');

-- Clients cannot write program tables directly.
insert into tap(line) select throws_ok($$ insert into programs(key,name,version,starts_on) values ('x','x',1,'2026-11-02') $$, '42501', null, 'client cannot insert programs');
insert into tap(line) select throws_ok($$ update program_exercises set sets = 9 $$, '42501', null, 'client cannot edit prescriptions');
insert into tap(line) select throws_ok($$ delete from programs $$, '42501', null, 'client cannot delete programs');

-- ── Nested sessions ──────────────────────────────────────────────────────────
insert into results select 'push', public.push_session(jsonb_build_object(
  'id', '50000000-0000-4000-a000-000000000001', 'performed_on', '2026-11-02', 'title', 'Push 1',
  'program_id', (select value->>'id' from results where key = 'v2'), 'program_workout_key', 'push-1',
  'exercises', jsonb_build_array(
    jsonb_build_object('exercise', 'db-press', 'planned_sets', 3, 'planned_rep_min', 8, 'planned_rep_max', 12,
      'planned_load', 25, 'planned_unit', 'kg', 'sets', jsonb_build_array(
        jsonb_build_object('load', 25, 'unit', 'kg', 'reps', 10, 'rir', 2),
        jsonb_build_object('load', 25, 'unit', 'kg', 'reps', 8.5, 'rir', 1))),
    jsonb_build_object('exercise', 'row', 'substituted_for', 'pec-deck', 'notes', 'deck taken',
      'sets', jsonb_build_array(jsonb_build_object('load', 9, 'unit', 'plates', 'reps', 12))),
    jsonb_build_object('exercise', 'pull-up', 'sets', jsonb_build_array(
      jsonb_build_object('unit', 'bodyweight', 'reps', 5.5))))), 1, null);
insert into tap(line) select is((select value->>'status' from results where key = 'push'), 'applied', 'nested push applied');
insert into tap(line) select is((select jsonb_array_length(value->'session'->'exercises') from results where key = 'push'), 3, 'snapshot returns exercises');
insert into tap(line) select is((select value->'session'->'exercises'->0->'sets'->1->>'reps' from results where key = 'push'), '8.5', 'half reps kept');
insert into tap(line) select is((select value->'session'->'exercises'->0->>'exercise_name' from results where key = 'push'), 'DB press', 'exercise name snapshot defaults to the exercise name');
insert into tap(line) select is((select x.slug from session_exercises se join exercises x on x.id = se.substituted_for), 'pec-deck', 'substitution recorded by slug');
insert into tap(line) select is((select program_workout_key from workout_sessions), 'push-1', 'session points at its program workout');
insert into tap(line) select throws_ok($$ select public.save_session(jsonb_build_object('performed_on', '2026-11-02',
  'program_id', (select value->>'id' from results where key = 'v2'), 'program_workout_key', 'legs-9', 'exercises', '[]'::jsonb)) $$,
  '23503', null, 'program workout key must exist in that program');
insert into tap(line) select throws_ok($$ select public.save_session('{"performed_on":"2026-11-02","exercises":[{"sets":[]}]}') $$,
  '22023', null, 'exercise reference required');

-- ── Set measures ─────────────────────────────────────────────────────────────
insert into tap(line) select throws_ok($$ insert into session_sets(session_exercise_id,session_id,position,load,unit,reps) select se_id,session_id,9,25,'kg',5.25 from (select se.id as se_id, se.session_id from session_exercises se join exercises x on x.id = se.exercise_id where x.slug = 'db-press') ids $$, '23514', null, 'reps only in half steps');
insert into tap(line) select throws_ok($$ insert into session_sets(session_exercise_id,session_id,position,load,unit,reps) select se_id,session_id,9,10,'bodyweight',5 from (select se.id as se_id, se.session_id from session_exercises se join exercises x on x.id = se.exercise_id where x.slug = 'db-press') ids $$, '23514', null, 'bodyweight sets have no load');
insert into tap(line) select throws_ok($$ insert into session_sets(session_exercise_id,session_id,position,unit,reps) select se_id,session_id,9,'kg',5 from (select se.id as se_id, se.session_id from session_exercises se join exercises x on x.id = se.exercise_id where x.slug = 'db-press') ids $$, '23514', null, 'weighted sets need a load');
insert into tap(line) select throws_ok($$ insert into session_sets(session_exercise_id,session_id,position,load,unit) select se_id,session_id,9,25,'kg' from (select se.id as se_id, se.session_id from session_exercises se join exercises x on x.id = se.exercise_id where x.slug = 'db-press') ids $$, '23514', null, 'a set needs reps or a duration');
insert into tap(line) select lives_ok($$ insert into session_sets(session_exercise_id,session_id,position,load,unit,duration_s) select se_id,session_id,9,24,'kg',60 from (select se.id as se_id, se.session_id from session_exercises se join exercises x on x.id = se.exercise_id where x.slug = 'db-press') ids $$, 'timed set without reps is valid');
insert into tap(line) select lives_ok($$ insert into session_sets(session_exercise_id,session_id,position,raw) select se_id,session_id,10,'{"load":"2 carries","review":"load not recorded"}' from (select se.id as se_id, se.session_id from session_exercises se join exercises x on x.id = se.exercise_id where x.slug = 'db-press') ids $$, 'incomplete imported set kept with raw');

-- ── Import refs ──────────────────────────────────────────────────────────────
insert into workout_sessions(performed_on,source,source_ref) values ('2026-09-04','import','recA');
insert into tap(line) select throws_ok($$ insert into workout_sessions(performed_on,source,source_ref) values ('2026-09-04','import','recA') $$, '23505', null, 'source ref unique per user');
insert into tap(line) select lives_ok($$ insert into workout_sessions(performed_on,source,source_ref) values ('2026-09-04','import','recA') on conflict (user_id, source_ref) do nothing $$, 're-import is a no-op');
insert into tap(line) select is((select count(*) from workout_sessions where source_ref = 'recA'), 1::bigint, 're-import creates no duplicate');
insert into checkins(checked_on,weight_kg,source,source_ref) values ('2026-10-01',80.5,'import','recC');

-- ── User B: isolation ────────────────────────────────────────────────────────
set local request.jwt.claims = '{"sub":"00000000-0000-4000-a000-00000000000b","role":"authenticated"}';
insert into tap(line) select is((select count(*) from exercises) + (select count(*) from programs) + (select count(*) from program_workouts)
  + (select count(*) from program_exercises) + (select count(*) from session_exercises) + (select count(*) from checkins), 0::bigint,
  'other user sees no exercises, programs, session exercises or check-ins');
insert into tap(line) select throws_ok($$ select public.publish_program((select p from program_v1)) $$, '22023', null, 'other user cannot publish with owner exercises');
insert into tap(line) select throws_ok($$ select public.save_session('{"performed_on":"2026-11-02","exercises":[{"exercise":"db-press","sets":[]}]}') $$, '22023', null, 'other user cannot reference owner exercises by slug');
insert into tap(line) select lives_ok($$ insert into workout_sessions(performed_on,source,source_ref) values ('2026-09-04','import','recA') $$, 'source refs are scoped per user');
insert into tap(line) select throws_ok($$ select public.save_session(jsonb_build_object('performed_on', '2026-11-02',
  'program_id', (select value->>'id' from results where key = 'v2'), 'program_workout_key', 'push-1', 'exercises', '[]'::jsonb)) $$,
  '23503', null, 'other user cannot attach a session to owner program');

-- ── Immutability below the grant layer, anonymous access ────────────────────
reset role;
insert into tap(line) select throws_ok($$ update program_exercises set sets = 9 $$, '55000', null, 'prescriptions immutable even for privileged writers');
insert into tap(line) select throws_ok($$ update programs set name = 'renamed' $$, '55000', null, 'program versions immutable');
set local role anon;
insert into tap(line) select throws_ok($$ select count(*) from programs $$, '42501', null, 'anonymous clients have no program access');

insert into tap(line) select * from finish();
select string_agg(line, E'\n' order by n) as tap from tap;
rollback;
