-- Runs inside a transaction that is rolled back; safe against the dev project.
-- Run with: scripts/db test
-- The Management API returns only the last result, so every TAP line is collected in
-- the temp table `tap` and returned by the final query.
begin;
create extension if not exists pgtap with schema extensions;
set local search_path = public, extensions;
create temp table tap (n serial, line text);
grant all on tap to public;
grant all on sequence tap_n_seq to public;

insert into tap (line) select plan(22);

-- Two throwaway users (rolled back with the transaction).
insert into auth.users (id, email) values
  ('00000000-0000-4000-a000-00000000000a', 'owner@test.invalid'),
  ('00000000-0000-4000-a000-00000000000b', 'other@test.invalid');

-- ── Act as user A ────────────────────────────────────────────────────────────
set local role authenticated;
set local request.jwt.claims = '{"sub":"00000000-0000-4000-a000-00000000000a","role":"authenticated"}';

insert into tap (line) select lives_ok($$
  insert into workout_sessions (id, performed_on, title, source)
  values ('10000000-0000-4000-a000-000000000001', '2026-10-31', 'Push A', 'cli')
$$, 'owner can create a session with a client-supplied id');

insert into tap (line) select is((select user_id from workout_sessions where id = '10000000-0000-4000-a000-000000000001'),
  '00000000-0000-4000-a000-00000000000a'::uuid, 'user_id defaults to the signed-in user');
insert into tap (line) select isnt((select number from workout_sessions where id = '10000000-0000-4000-a000-000000000001'),
  null, 'server assigns a friendly number');
insert into tap (line) select is((select revision from workout_sessions where id = '10000000-0000-4000-a000-000000000001'),
  1::bigint, 'new session starts at revision 1');

insert into tap (line) select lives_ok($$
  insert into workout_sessions (performed_on, title) values ('2026-10-31', 'Second session same day')
$$, 'id is generated when omitted, and two sessions may share a date');

insert into tap (line) select throws_ok($$
  insert into workout_sessions (performed_on, number) values ('2026-10-31', 999)
$$, '428C9', null, 'clients cannot choose the friendly number');

insert into tap (line) select lives_ok($$
  insert into session_sets (session_id, position, exercise_name, load, unit, reps, rir)
  values ('10000000-0000-4000-a000-000000000001', 0, 'Dumbbell bench press', 32.5, 'kg', 7, 3)
$$, 'owner can add a set');
insert into tap (line) select is((select revision from workout_sessions where id = '10000000-0000-4000-a000-000000000001'),
  2::bigint, 'adding a set bumps the session revision');

update session_sets set reps = 8 where session_id = '10000000-0000-4000-a000-000000000001';
insert into tap (line) select is((select revision from workout_sessions where id = '10000000-0000-4000-a000-000000000001'),
  3::bigint, 'editing a set bumps the session revision');

update workout_sessions set revision = 100 where id = '10000000-0000-4000-a000-000000000001';
insert into tap (line) select is((select revision from workout_sessions where id = '10000000-0000-4000-a000-000000000001'),
  4::bigint, 'a writer cannot set the revision directly');

insert into tap (line) select throws_ok($$
  insert into session_sets (session_id, position, exercise_name, load, unit, reps)
  values ('10000000-0000-4000-a000-000000000001', 1, 'Row', 40, 'kg', 0)
$$, '23514', null, 'reps must be positive');

-- ── Act as user B ────────────────────────────────────────────────────────────
set local request.jwt.claims = '{"sub":"00000000-0000-4000-a000-00000000000b","role":"authenticated"}';

insert into tap (line) select is((select count(*) from workout_sessions), 0::bigint, 'other user sees no sessions');
insert into tap (line) select is((select count(*) from session_sets), 0::bigint, 'other user sees no sets');

with u as (update workout_sessions set title = 'hijacked' returning 1)
insert into tap (line) select is(count(*), 0::bigint, 'other user cannot update owner sessions') from u;
with d as (delete from session_sets returning 1)
insert into tap (line) select is(count(*), 0::bigint, 'other user cannot delete owner sets') from d;

insert into tap (line) select throws_ok($$
  insert into session_sets (session_id, position, exercise_name, load, unit, reps)
  values ('10000000-0000-4000-a000-000000000001', 5, 'Sneaky', 1, 'kg', 1)
$$, '23503', null, 'other user cannot add sets to owner session');

insert into tap (line) select throws_ok($$
  insert into workout_sessions (performed_on, user_id)
  values ('2026-10-31', '00000000-0000-4000-a000-00000000000a')
$$, '42501', null, 'other user cannot create sessions owned by someone else');

-- ── Anonymous (publishable key, not signed in) ──────────────────────────────
set local role anon;
insert into tap (line) select throws_ok($$ select count(*) from workout_sessions $$, '42501', null,
  'anonymous clients have no access to sessions');
insert into tap (line) select throws_ok($$ select count(*) from session_sets $$, '42501', null,
  'anonymous clients have no access to sets');

-- ── Back to user A ───────────────────────────────────────────────────────────
set local role authenticated;
set local request.jwt.claims = '{"sub":"00000000-0000-4000-a000-00000000000a","role":"authenticated"}';

insert into tap (line) select is((select title from workout_sessions where id = '10000000-0000-4000-a000-000000000001'),
  'Push A', 'owner data unchanged by other user');

delete from workout_sessions where id = '10000000-0000-4000-a000-000000000001';
insert into tap (line) select is((select count(*) from session_sets where session_id = '10000000-0000-4000-a000-000000000001'),
  0::bigint, 'deleting a session deletes its sets');

-- ── Schema guard ─────────────────────────────────────────────────────────────
reset role;
insert into tap (line) select is((select count(*) from pg_tables where schemaname = 'public' and not rowsecurity),
  0::bigint, 'every public table has RLS enabled');

insert into tap (line) select * from finish();
select string_agg(line, E'\n' order by n) as tap from tap;
rollback;
