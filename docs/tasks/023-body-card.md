# 023 — Body card: waist goal, trends, cut check, check-in entry

Status: in progress
Updated: 2026-11-01
Starting commit: 4bc409f
Parent: [019](019-progress-milestone.md) step 5 · Delegated: yes (see
[delegation](../delegation/README.md))

## Outcome and acceptance
The owner is cutting toward a configurable waist goal. The Progress screen gets a Body card: waist
now and cm to go, weekly waist and weight trend, projected goal date, and one verdict that
pairs the cut with strength (e.g. losing weight fast while exercises decline). Check-ins
can be added on the phone. The data layer exists (lead, a632ef7): `CheckInRow`,
`CheckInDao` (`observeAll`, `all`, `add`), `WorkoutDatabase.checkIns()`; sync uploads
pending rows when upload is on. Accepted when the items below behave as specified, JVM
tests cover the pure functions, and `scripts/check --offline` passes. The lead runs the
device check.

## Scope
In: new `android/domain/src/main/kotlin/com/repknot/domain/Body.kt` + `BodyTest.kt`;
app: `AppSettings.kt`, `ProgressViewModel.kt`, new `BodyText.kt` + `BodyTextTest.kt`,
`ui/ProgressScreen.kt`, `MainActivity.kt` (factory args, routes, settings field),
`strings.xml`, this file.
Out of scope (do not modify): `data` (all of it), sync, `RepknotApplication.kt`
(use `app.database.checkIns()` and `app::requestRefresh` from `MainActivity`),
`WorkoutViewModel.kt`, `Progress.kt`, new libraries. Do not read `private-data/`.

## Domain (`Body.kt`, pure; implement exactly)
```kotlin
data class CheckIn(val date: LocalDate, val weightKg: Double?, val waistCm: Double?)
data class BodyTrend(val latest: Double, val latestOn: LocalDate, val perWeek: Double?)
enum class BodyVerdict { NOT_ENOUGH_DATA, GOAL_REACHED, ON_TRACK, STALLED, TOO_FAST }
data class BodySummary(val waist: BodyTrend?, val weight: BodyTrend?, val goalCm: Double,
    val toGoalCm: Double?, val goalDate: LocalDate?, val weightPctPerWeek: Double?,
    val verdict: BodyVerdict)
fun bodyTrend(points: List<Pair<LocalDate, Double>>, today: LocalDate): BodyTrend?
fun bodySummary(checkIns: List<CheckIn>, today: LocalDate, goalCm: Double,
    declines: Int, recentDip: Boolean): BodySummary
```
- `bodyTrend`: ignore points after `today`; null if none left. `latest`/`latestOn` = the
  point with the latest date (same date: the last in input order). Window = points with
  `date ≥ today − 27 days`. `perWeek` = least-squares slope of value over `date.toEpochDay()`
  × 7, only when the window has ≥ 4 points and its dates span ≥ 14 days; else null.
- `bodySummary`: `waist = bodyTrend(waist points)`, `weight = bodyTrend(weight points)`
  (null values skipped). `toGoalCm = waist?.latest − goalCm`. `goalDate` = `waist.latestOn +
  ceil(toGoalCm / −perWeek × 7)` days when `toGoalCm > 0` and `perWeek ≤ −0.05`; null when
  that is more than 365 days away or otherwise. `weightPctPerWeek = weight.perWeek /
  weight.latest × 100` (null if either is missing). Verdict, first match:
  1. `waist == null` → NOT_ENOUGH_DATA
  2. `waist.latest ≤ goalCm` → GOAL_REACHED
  3. `weightPctPerWeek < −1.0` and (`declines ≥ 2` or `recentDip`) → TOO_FAST
  4. `waist.perWeek == null` → NOT_ENOUGH_DATA
  5. `waist.perWeek ≤ −0.1` → ON_TRACK
  6. otherwise STALLED

## App
1. **Goal setting.** `AppSettings.waistGoalCm: StateFlow<Double>` (prefs key
   `waist_goal_cm`, default 80.0) and `setWaistGoalCm(Double)`. Settings screen, section
   "Workouts" → add below the upload description: an `OutlinedTextField` "Waist goal (cm)"
   (decimal keyboard; comma accepted) saved when it parses to 40–200; otherwise not saved
   and shows supporting text "40–200 cm".
2. **ViewModel.** `ProgressViewModel(sessions, reference, checkIns: CheckInDao,
   waistGoal: StateFlow<Double>, onCheckInAdded: () -> Unit, today)`. State gains `body:
   BodySummary?` and `checkIns: List<CheckInRow>` (newest first). Recompute on
   `observeAllSessions()`, `checkIns.observeAll()` and `waistGoal` (combine).
   `declines` = items with POSSIBLE_DECLINE and `daysSinceLast ≤ 14`; `recentDip` = any
   unfiltered `sessionDips` date within 14 days before today. `body` is null when there
   are no check-ins. `addCheckIn(date: String, weight: String, waist: String, notes:
   String): Boolean` — parse (comma → dot; blank → null; must be > 0 and < 1000), date
   `LocalDate.parse`, require at least one of weight/waist; on success `checkIns.add(CheckInRow(UUID,
   date, weightKg, waistCm, notes = notes.trim().ifEmpty { null }))` then
   `onCheckInAdded()`; return false on invalid input without writing.
3. **Text (`BodyText.kt`, pure, English).** Numbers: one decimal, drop `.0` (`91.5`,
   `80`); signed rates use `−` (U+2212) for negative and `+` for positive; dates `MMM d`.
   - `waistLine(s)`: `Waist 91.5 cm · 11.5 cm to go`; if `toGoalCm ≤ 0`: `Waist 79.5 cm ·
     goal reached (80 cm)`; `waist == null`: `No waist measurement yet`.
   - `rateLine(s)`: perWeek known: `−0.6 cm/week over 4 weeks` + ` · 80 cm around Dec 20`
     when `goalDate != null`; perWeek null: `Not enough check-ins for a trend (needs 4 over
     2+ weeks)`; `waist == null`: empty string (not shown).
   - `weightLine(s)`: `Weight 74.6 kg · −0.1 kg/week (−0.2 %/week)`; perWeek null:
     `Weight 74.6 kg`; `weight == null`: empty string (not shown).
   - `lastCheckInLine(latest: LocalDate, today)`: `Last check-in Oct 31`; more than 7 days
     ago: `Last check-in Oct 31 · 9 days ago — time for a check-in`.
   - `verdictLine(s, declines, recentDip)`: ON_TRACK → `On track — waist down, strength
     holding.` when `declines == 0 && !recentDip`, else `On track — waist down; keep an eye
     on the exercises below.`; STALLED → `Waist hasn't moved in 4 weeks — consider
     tightening the deficit.`; TOO_FAST → `Losing over 1 % of bodyweight a week while
     strength slips — consider eating a bit more.`; GOAL_REACHED → `Goal reached — time to
     set the next phase.`; NOT_ENOUGH_DATA → empty string (not shown).
   - `checkInLine(row: CheckInRow)`: `Oct 31 · 74.6 kg · 91.5 cm` (missing values omitted)
     + ` · not uploaded` when `pending`.
   - Any combination not listed: the most conservative readable text; record it.
4. **Progress screen.** Above the dip cards: a Body card (clickable → route `"body"`)
   with title "Body" (titleMedium), `waistLine` (titleLarge), `rateLine`, `weightLine`,
   `lastCheckInLine` (bodySmall, onSurfaceVariant), `verdictLine` (bodyMedium; error color
   for TOO_FAST and STALLED, primary for ON_TRACK and GOAL_REACHED), and an **Add check-in**
   text button. Empty strings are not rendered. When `body == null`: the card shows "No
   check-ins yet" and the button.
5. **Add check-in dialog** (from the card and from the body screen): fields Date
   (prefilled `today`, `yyyy-MM-dd`), Weight (kg), Waist (cm), Notes; **Save** calls
   `addCheckIn` and closes on true, otherwise shows "Enter a date and a weight or waist";
   **Cancel**.
6. **Body screen** (route `"body"`, Back/system back → progress): title "Body", the same
   lines as the card, **Add check-in**, then every check-in newest first as `checkInLine`.

## Tests
`BodyTest`: `bodyTrend` (future points ignored, same-date latest, < 4 points → null rate,
span < 14 days → null rate, exact slope on a straight line, window excludes points older
than 27 days); `bodySummary` verdict rules 1–6 in order (incl. TOO_FAST needing both fast
loss and declines/dip), `goalDate` (computed, null when rising, null beyond 365 days),
`toGoalCm`, `weightPctPerWeek`. `BodyTextTest`: every branch in item 3. ViewModel
`addCheckIn` parsing is a pure helper you may extract and test (recommended).

## Plan / progress
1. [x] `Body.kt` + `BodyTest`.
2. [ ] Settings goal, ViewModel, `BodyText` + tests.
3. [ ] Card, dialog, body screen.
4. [ ] `scripts/check --offline`; commit(s) with task 023 in the subject.

## Decisions and discoveries

- Projection cutoff is measured from today, as “more than 365 days away” describes the date.

## Verification

- `cd android && ./gradlew :domain:test --offline` passed (21 tests). An initial test used a non-exact floating-point rate and expected 30 rather than the specified `ceil` result; changed its fixture to an exactly representable slope.

## Handoff

Next: settings, ViewModel and text (step 2); then UI and offline full check. Device check belongs to lead.
