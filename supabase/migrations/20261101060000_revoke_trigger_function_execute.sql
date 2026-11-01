-- Access review before real health data (ADR 003): trigger functions are only invoked by
-- their triggers. Postgres refuses direct calls anyway, but clients need no EXECUTE.
revoke execute on function public.set_session_revision(), public.touch_parent_session(),
  public.guard_program_immutable() from public, anon, authenticated;
