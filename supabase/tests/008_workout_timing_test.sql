-- Workout timing (task 027): push stores timestamps, omission keeps them, Retry-style
-- re-push keeps logged_at, connector save keeps them. Rolled back.
begin;
create extension if not exists pgtap with schema extensions;
set local search_path = public, extensions;
create temp table tap (n serial, line text);
grant all on tap to public;
grant all on sequence tap_n_seq to public;
insert into tap(line) select plan(10);
insert into auth.users(id,email) values ('00000000-0000-4000-a000-00000000008a','timing-a@test.invalid');
set local role authenticated;
set local request.jwt.claims = '{"sub":"00000000-0000-4000-a000-00000000008a","role":"authenticated"}';
insert into exercises(slug,name,default_unit) values ('timing-press','Timing press','kg');

-- Phone push with timestamps on the session and its sets.
insert into tap(line) select is((public.push_session(jsonb_build_object(
  'id', '80000000-0000-4000-a000-000000000001', 'performed_on', '2026-11-02', 'title', 'Timed',
  'started_at', '2026-11-02T10:00:00Z', 'finished_at', '2026-11-02T11:05:00Z',
  'exercises', jsonb_build_array(jsonb_build_object('exercise', 'timing-press', 'sets', jsonb_build_array(
    jsonb_build_object('id', '80000000-0000-4000-a000-0000000000a1', 'load', 20, 'unit', 'kg', 'reps', 10,
      'logged_at', '2026-11-02T10:05:00Z'),
    jsonb_build_object('id', '80000000-0000-4000-a000-0000000000a2', 'load', 20, 'unit', 'kg', 'reps', 9))))), 1, null))->>'status',
  'applied', 'push with timestamps applied');
insert into tap(line) select is((select started_at from workout_sessions where id = '80000000-0000-4000-a000-000000000001'),
  '2026-11-02T10:00:00Z'::timestamptz, 'started_at stored');
insert into tap(line) select is((select finished_at from workout_sessions where id = '80000000-0000-4000-a000-000000000001'),
  '2026-11-02T11:05:00Z'::timestamptz, 'finished_at stored');
insert into tap(line) select is((select logged_at from session_sets where id = '80000000-0000-4000-a000-0000000000a1'),
  '2026-11-02T10:05:00Z'::timestamptz, 'logged_at stored');
insert into tap(line) select ok((select logged_at is null from session_sets where id = '80000000-0000-4000-a000-0000000000a2'),
  'set without logged_at stays null');
insert into tap(line) select is(public.session_snapshot('80000000-0000-4000-a000-000000000001')
  ->'exercises'->0->'sets'->0->>'logged_at' is not null, true, 'snapshot returns logged_at');

-- Push that omits every timestamp (older client) keeps the stored values.
insert into tap(line) select is((public.push_session(jsonb_build_object(
  'id', '80000000-0000-4000-a000-000000000001', 'performed_on', '2026-11-02', 'title', 'Timed',
  'exercises', jsonb_build_array(jsonb_build_object('exercise', 'timing-press', 'sets', jsonb_build_array(
    jsonb_build_object('id', '80000000-0000-4000-a000-0000000000a1', 'load', 20, 'unit', 'kg', 'reps', 11))))), 2,
  (select revision from workout_sessions where id = '80000000-0000-4000-a000-000000000001')))->>'status',
  'applied', 'push without timestamps applied');
insert into tap(line) select ok((select started_at = '2026-11-02T10:00:00Z' and finished_at = '2026-11-02T11:05:00Z'
  from workout_sessions where id = '80000000-0000-4000-a000-000000000001'), 'omitted session timestamps kept');
insert into tap(line) select is((select logged_at from session_sets where id = '80000000-0000-4000-a000-0000000000a1'),
  '2026-11-02T10:05:00Z'::timestamptz, 'omitted logged_at kept for the same set id');

-- Connector save without timestamps keeps them too.
insert into tap(line) select ok((select (s->>'started_at')::timestamptz = '2026-11-02T10:00:00Z'
    and (s->'exercises'->0->'sets'->0->>'logged_at')::timestamptz = '2026-11-02T10:05:00Z'
  from (select public.save_session(jsonb_build_object('ref', '80000000-0000-4000-a000-000000000001',
    'expected_revision', (select revision from workout_sessions where id = '80000000-0000-4000-a000-000000000001'),
    'performed_on', '2026-11-02', 'title', 'Timed (connector)',
    'exercises', jsonb_build_array(jsonb_build_object('exercise', 'timing-press', 'sets', jsonb_build_array(
      jsonb_build_object('id', '80000000-0000-4000-a000-0000000000a1', 'load', 20, 'unit', 'kg', 'reps', 11)))))) as s) x),
  'connector save keeps session and set timestamps');

insert into tap(line) select * from finish();
select string_agg(line, E'\n' order by n) as tap from tap;
rollback;
