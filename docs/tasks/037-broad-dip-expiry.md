# 037 — Broad dips: resolve, expire, escalate on patterns

Status: blocked
Starting commit: 13f1c8a
Date: 2026-11-12

## Outcome / acceptance

Today every broad dip (`sessionDips`, task 019) shows as a red card for 28 days, even after
the next sessions are back to normal. Owner-approved behavior instead:

- A dip **resolves** when the exercises that dipped are back to usual; a resolved dip is
  no longer shown on Progress (unless part of a pattern, below).
- An unresolved dip shows a **neutral** card for at most **10 days**.
- The card turns **red** only when:
  1. **Persisting** — the dipped exercises are still below usual in their latest sessions;
  2. **Pattern** — 2 or more dips within the last 8 normal (non-light) finished sessions;
  3. **Fast loss** — an unresolved dip within 10 days while bodyweight falls faster than 1 % a week.
- Dips older than 21 days are never shown on Progress. Every dip stays visible as a small
  neutral "Broad dip" tag on that workout in History (list row and workout detail).

### Domain (`android/domain/.../Progress.kt`, pure; reuse `ordered`, `lineage`, `exposure`, `compare`)

`sessionDips` keeps its current detection rule. Add:

```kotlin
enum class DipState { RESOLVED, PERSISTING, PENDING }
data class DipStatus(val dip: SessionDip, val state: DipState, val stillBelow: List<String>, val evaluated: Int)
fun dipStatuses(history: List<PastSession>): List<DipStatus>   // one per sessionDips result, same order
enum class DipAlertKind { PATTERN, PERSISTING, FAST_LOSS, NOTICE }
data class DipAlert(val kind: DipAlertKind, val dips: List<DipStatus>)  // dips: the ones the alert is about, oldest first
fun dipAlert(history: List<PastSession>, today: LocalDate, weightPctPerWeek: Double?): DipAlert?
```

**Status of one dip D** (session S, exercises `D.below`):
- For each exercise X in `D.below`: *baseline* = the exposure S was compared to in `sessionDips`
  (X's last earlier comparable non-light exposure, same unit as `sessionDips` used). *Latest* =
  X's newest non-light exposure **after** S (same unit; ordered as `ordered`). If none → X is not
  evaluated. Otherwise X is evaluated, and *still below* when `compare(latest, baseline) == WORSE`,
  or when not comparable **only because of load** (same unit and same `timed`) and
  `latest.topLoad < baseline.topLoad` (both non-null). Any unit or timed mismatch → evaluated,
  not below, regardless of load (mismatch takes precedence over the load-down fallback). Not
  comparable with a higher or equal top load → evaluated, not below.
- `evaluated` = number of evaluated exercises; `stillBelow` = ids still below (D.below order).
- State: `RESOLVED` if `evaluated * 2 >= D.below.size` and `stillBelow.size * 2 < evaluated`;
  `PERSISTING` if `stillBelow.size >= 2` and `stillBelow.size * 2 >= evaluated`; otherwise `PENDING`.

**Alert** (at most one; first matching rule wins). Let `recent21` = statuses with
`today - 21 days <= dip.date <= today`; `last8` = ids of the last 8 non-light finished sessions in
`ordered` order.
1. `PATTERN`: `recent21` dips whose session id is in `last8` number ≥ 2 → those dips.
2. `PERSISTING`: newest `recent21` dip with state `PERSISTING` → that dip.
3. `FAST_LOSS`: `weightPctPerWeek != null && weightPctPerWeek < -1.0` and the newest dip with
   `dip.date >= today - 10 days` and state ≠ `RESOLVED` exists → that dip.
4. `NOTICE`: newest dip with `dip.date >= today - 10 days` and state `PENDING` → that dip.
5. Otherwise `null`.
Light sessions are never dips and never count as follow-ups (existing behavior).

### App
- `ProgressViewModel`: replace `dips: List<SessionDip>` with `dipAlert: DipAlert?`, computed with the
  body summary's `weightPctPerWeek` (compute the summary's weight rate first; see below). Redefine
  `recentDip = dipAlert != null` (any shown dip card). Because `bodySummary` takes `recentDip`, compute the weight trend
  first: call `bodySummary(..., recentDip = false)` to read `weightPctPerWeek`, compute `dipAlert`,
  then call `bodySummary` again with the real `recentDip` (cheap; keeps `Body.kt` unchanged).
- `ProgressScreen`: one card instead of the dip list. Red (`errorContainer`) for PATTERN /
  PERSISTING / FAST_LOSS; neutral (`surfaceContainerHigh`, onSurface text) for NOTICE.
- `ProgressText.kt`: replace `dipText` with `dipAlertText(alert, names)`; dates via the existing
  `date(...)`; names joined with ", " (unknown id → the id). Exact copy:
  - PATTERN: `"%d broad dips in your last 8 workouts (%s) — recovery may not be keeping up with the cut. Check sleep and food; consider eating a bit more or a lighter session."` (dates joined ", ")
  - PERSISTING: `"Still below usual since the dip on %s (%s) — recovery isn't catching up. Check sleep and food; consider a lighter session."` (stillBelow names)
  - FAST_LOSS: `"Broad dip on %s while losing over 1 % of bodyweight a week — consider eating a bit more."`
  - NOTICE: `"Off day on %s — %d of %d exercises below usual (%s). Usually nothing; this clears when those exercises are back to normal."` (below.size, evaluable, below names)
- History: `HistoryUiState` gains `dipSessionIds: Set<String>` from `sessionDips(sessions.history())`
  (same history source as Progress). `HistoryScreen` workout rows and `WorkoutHistoryScreen` header
  show a small neutral label "Broad dip" (labelSmall, onSurfaceVariant) for those ids.

## Scope / out of scope

Allowed: `android/domain/.../Progress.kt` (+ tests), `android/app/src/main/java/com/repknot/app/
{ProgressViewModel,ProgressText,HistoryViewModel}.kt`, `ui/{ProgressScreen,HistoryScreen}.kt`, app
strings, app/domain JVM tests, this task's Decisions/Verification/Handoff.
Out of scope (do not modify): `sessionDips` detection rule, `progress()` statuses, `Body.kt`,
`BodyText.kt` copy, Home, data/sync/Room, `supabase/`, `scripts/`, build files, other docs, worker
rules. Do not read `.env`, credentials, `private-data/`, `work/`. No adb/install/DB commands.
Synthetic values only in tests.

## Plan / progress

- [x] Worker: inspect existing domain and app wiring; stop for clarification before implementation.
- [ ] Worker: domain + JVM tests; app wiring/UI/text; `scripts/check --offline`; commit (domain and
  app may be one commit or two, each verified).
- [ ] Lead: review, rerun checks, verify on synthetic data (emulator, test2) where practical; owner
  checks the real Progress page.

## Verification responsibilities

Worker (JVM, synthetic sessions): dip status — resolved by recovery, persisting (2 still below),
pending (no follow-up yet), load-up after the dip counts as recovered, load-down counts as below,
light follow-ups ignored; alert — each rule in priority order, 21-day and 10-day boundaries
(inclusive), pattern counted by last 8 non-light sessions (a 9th-back dip does not count), fast loss
needs a non-resolved dip, null when everything is resolved and old; `dipAlertText` for every kind
including unknown names. History ids come from `sessionDips` (one test).
Lead: diff review; independent checks; visual check on synthetic data.

## Decisions / discoveries

- Blocked before implementation: the lower-load fallback and the instruction that timed mismatches are not below overlap when a follow-up changes both load and timed/reps mode. This affects whether an alert can become persisting; see Handoff.
- The old 28-day list and red-for-every-dip behavior is replaced (owner, 2026-11-12).
- Rule 3 uses the raw weight rate, not `BodyVerdict.TOO_FAST`, because that verdict already
  depends on `recentDip` (would be circular).
- Thresholds (10 days, 21 days, 8 sessions, "≥ 2 still below") are judgment for a 4–6 sessions/week
  schedule, not research values; revisit after a few weeks of use.

## Verification

- Inspected the task, project guidance, `Progress.kt`, and the existing Progress and History app wiring; no implementation or build checks run.
- `git diff --check`, `git diff --cached --check`, and `scripts/privacy-check --staged` passed for the handoff; the privacy check warned that short passwords are not scanned.
- `git status --short` was clean on entry; `git log -1 --oneline` showed the task planning commit.

## Handoff

Blocked on requirement clarification: if a follow-up has a lower non-null top load than the baseline but changes between timed and reps-based sets, should it count as still below? The load-down fallback says a not-comparable lower-load exposure is below, while the following sentence says timed mismatches are evaluated but not below.

Next action: clarify precedence, then implement the domain and app plan and run `scripts/check --offline`. No implementation changes are unfinished.
