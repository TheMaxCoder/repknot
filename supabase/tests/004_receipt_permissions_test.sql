begin;
create extension if not exists pgtap with schema extensions;
set local search_path = public, extensions;
create temp table tap (n serial, line text);
grant all on tap to public;
grant all on sequence tap_n_seq to public;
insert into tap(line) select plan(7);
insert into auth.users(id,email) values
  ('00000000-0000-4000-a000-00000000000a','receipt-a@test.invalid'),
  ('00000000-0000-4000-a000-00000000000b','receipt-b@test.invalid');
set local role authenticated;
set local request.jwt.claims = '{"sub":"00000000-0000-4000-a000-00000000000a","role":"authenticated"}';
insert into tap(line) select is(public.push_session('{"id":"40000000-0000-4000-a000-000000000001","performed_on":"2026-10-31","exercises":[]}',1,null)->>'status','applied','authenticated push writes receipt');
insert into tap(line) select is((select count(*) from session_push_receipts),1::bigint,'owner can inspect receipt');
insert into tap(line) select throws_ok($$ insert into session_push_receipts(session_id,client_revision,server_revision) values ('40000000-0000-4000-a000-000000000001',2,100) $$,'42501',null,'owner cannot forge receipt');
insert into tap(line) select throws_ok($$ update session_push_receipts set server_revision=100 $$,'42501',null,'owner cannot alter receipt');
insert into tap(line) select is(public.push_session('{"id":"40000000-0000-4000-a000-000000000001"}',1,null)->>'status','replay','legitimate retry remains idempotent');
set local request.jwt.claims = '{"sub":"00000000-0000-4000-a000-00000000000b","role":"authenticated"}';
insert into tap(line) select is((select count(*) from session_push_receipts),0::bigint,'other user cannot inspect receipts');
insert into tap(line) select throws_ok($$ select public.push_session('{"id":"40000000-0000-4000-a000-000000000001","performed_on":"2026-10-31","exercises":[]}',1,null) $$,'23505',null,'definer cannot overwrite another user session');
insert into tap(line) select * from finish();
select string_agg(line, E'\n' order by n) as tap from tap;
rollback;
