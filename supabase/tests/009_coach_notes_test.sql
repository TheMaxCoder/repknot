-- Coach notes: ownership, append-only grants, item validation. Rolled back.
begin;
create extension if not exists pgtap with schema extensions;
set local search_path = public, extensions;
create temp table tap (n serial, line text);
grant all on tap to public;
grant all on sequence tap_n_seq to public;
insert into tap(line) select plan(11);
insert into auth.users(id,email) values
  ('00000000-0000-4000-a000-00000000001a','notes-a@test.invalid'),
  ('00000000-0000-4000-a000-00000000001b','notes-b@test.invalid');
set local role authenticated;
set local request.jwt.claims = '{"sub":"00000000-0000-4000-a000-00000000001a","role":"authenticated"}';
insert into tap(line) select lives_ok($$ insert into coach_notes(headline,based_on,items,source) values ('Synthetic brief','2026-01-05',
  '[{"kind":"progress","text":"Add a plate next time","exercise_id":"00000000-0000-4000-a000-0000000000e1"},{"kind":"focus","text":"Line one\nline two"}]','cli') $$,
  'owner can publish a brief');
insert into tap(line) select is((select count(*) from coach_notes), 1::bigint, 'owner sees the brief');
insert into tap(line) select throws_ok($$ insert into coach_notes(headline,items) values ('x','[]') $$, '23514', null, 'empty items rejected');
insert into tap(line) select throws_ok($$ insert into coach_notes(headline,items) values ('x','[{"kind":"diary","text":"x"}]') $$, '23514', null, 'unknown kind rejected');
insert into tap(line) select throws_ok($$ insert into coach_notes(headline,items) values ('x','[{"kind":"hold"}]') $$, '23514', null, 'missing text rejected');
insert into tap(line) select throws_ok($$ insert into coach_notes(headline,items) values ('x', jsonb_build_array(jsonb_build_object('kind','hold','text',repeat('a',201)))) $$,
  '23514', null, 'over-long text rejected');
insert into tap(line) select throws_ok($$ insert into coach_notes(headline,items) values ('  ','[{"kind":"hold","text":"x"}]') $$, '23514', null, 'blank headline rejected');
insert into tap(line) select throws_ok($$ update coach_notes set headline = 'changed' $$, '42501', null, 'briefs are append-only');
set local request.jwt.claims = '{"sub":"00000000-0000-4000-a000-00000000001b","role":"authenticated"}';
insert into tap(line) select is((select count(*) from coach_notes), 0::bigint, 'other user sees no briefs');
insert into tap(line) select throws_ok($$ insert into coach_notes(user_id,headline,items) values ('00000000-0000-4000-a000-00000000001a','x','[{"kind":"hold","text":"x"}]') $$,
  '42501', null, 'cannot publish for another user');
set local role anon;
insert into tap(line) select throws_ok($$ select count(*) from coach_notes $$, '42501', null, 'anonymous clients have no access');
insert into tap(line) select * from finish();
select string_agg(line, E'\n' order by n) as tap from tap;
rollback;
