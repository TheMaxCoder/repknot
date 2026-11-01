-- Context entries: ownership, kinds, idempotent import refs. Rolled back.
begin;
create extension if not exists pgtap with schema extensions;
set local search_path = public, extensions;
create temp table tap (n serial, line text);
grant all on tap to public;
grant all on sequence tap_n_seq to public;
insert into tap(line) select plan(8);
insert into auth.users(id,email) values
  ('00000000-0000-4000-a000-00000000000a','context-a@test.invalid'),
  ('00000000-0000-4000-a000-00000000000b','context-b@test.invalid');
set local role authenticated;
set local request.jwt.claims = '{"sub":"00000000-0000-4000-a000-00000000000a","role":"authenticated"}';
insert into tap(line) select lives_ok($$ insert into context_entries(kind,recorded_on,title,details,source,source_ref)
  values ('health_state','2026-09-23','Synthetic constraint','{"type":"Constraint","status":"Active"}','import','recH1') $$, 'owner can add a context entry');
insert into tap(line) select throws_ok($$ insert into context_entries(kind,title) values ('diary','x') $$, '23514', null, 'unknown kind rejected');
insert into tap(line) select throws_ok($$ insert into context_entries(kind,title,source_ref) values ('health_state','again','recH1') $$, '23505', null, 'source ref unique per user');
insert into tap(line) select lives_ok($$ insert into context_entries(kind,title,source_ref) values ('health_state','again','recH1') on conflict (user_id, source_ref) do nothing $$, 're-import is a no-op');
set local request.jwt.claims = '{"sub":"00000000-0000-4000-a000-00000000000b","role":"authenticated"}';
insert into tap(line) select is((select count(*) from context_entries), 0::bigint, 'other user sees no context entries');
insert into tap(line) select lives_ok($$ insert into context_entries(kind,title,source_ref) values ('training_habit','mine','recH1') $$, 'source refs scoped per user');
insert into tap(line) select is((select count(*) from pg_proc p join pg_namespace n on n.oid = p.pronamespace
  where n.nspname = 'public' and p.prorettype = 'trigger'::regtype
    and (has_function_privilege('anon', p.oid, 'execute') or has_function_privilege('authenticated', p.oid, 'execute'))),
  0::bigint, 'clients cannot execute trigger functions');
set local role anon;
insert into tap(line) select throws_ok($$ select count(*) from context_entries $$, '42501', null, 'anonymous clients have no access');
insert into tap(line) select * from finish();
select string_agg(line, E'\n' order by n) as tap from tap;
rollback;
