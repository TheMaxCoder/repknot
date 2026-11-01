-- Light / modified day flag: default, push, keep-on-omit, connector save. Rolled back.
begin;
create extension if not exists pgtap with schema extensions;
set local search_path = public, extensions;
create temp table tap (n serial, line text);
grant all on tap to public;
grant all on sequence tap_n_seq to public;
insert into tap(line) select plan(7);
insert into auth.users(id,email) values ('00000000-0000-4000-a000-00000000007a','light-a@test.invalid');
set local role authenticated;
set local request.jwt.claims = '{"sub":"00000000-0000-4000-a000-00000000007a","role":"authenticated"}';

insert into tap(line) select lives_ok($$ insert into workout_sessions(id, performed_on, title)
  values ('70000000-0000-4000-a000-000000000001', '2026-11-02', 'Direct') $$, 'insert without light');
insert into tap(line) select is((select light from workout_sessions where id = '70000000-0000-4000-a000-000000000001'),
  false, 'light defaults to false');

insert into tap(line) select is(public.push_session(jsonb_build_object('id', '70000000-0000-4000-a000-000000000002',
  'performed_on', '2026-11-02', 'title', 'Light push', 'light', true, 'exercises', '[]'::jsonb), 1, null)->'session'->>'light',
  'true', 'push stores light = true');
insert into tap(line) select is(public.push_session(jsonb_build_object('id', '70000000-0000-4000-a000-000000000002',
  'performed_on', '2026-11-02', 'title', 'Light push', 'exercises', '[]'::jsonb), 2,
  (select revision from workout_sessions where id = '70000000-0000-4000-a000-000000000002'))->'session'->>'light',
  'true', 'push without light keeps the stored value');
insert into tap(line) select is(public.push_session(jsonb_build_object('id', '70000000-0000-4000-a000-000000000002',
  'performed_on', '2026-11-02', 'title', 'Light push', 'light', false, 'exercises', '[]'::jsonb), 3,
  (select revision from workout_sessions where id = '70000000-0000-4000-a000-000000000002'))->'session'->>'light',
  'false', 'push can clear light');

insert into tap(line) select is(public.save_session(jsonb_build_object('performed_on', '2026-11-03',
  'title', 'Connector', 'light', true, 'exercises', '[]'::jsonb))->>'light', 'true', 'connector save stores light');
insert into tap(line) select is(public.save_session(jsonb_build_object('ref', '70000000-0000-4000-a000-000000000001',
  'expected_revision', (select revision from workout_sessions where id = '70000000-0000-4000-a000-000000000001'),
  'performed_on', '2026-11-02', 'title', 'Direct', 'exercises', '[]'::jsonb))->>'light', 'false',
  'connector save without light keeps the stored value');

insert into tap(line) select * from finish();
select string_agg(line, E'\n' order by n) as tap from tap;
rollback;
