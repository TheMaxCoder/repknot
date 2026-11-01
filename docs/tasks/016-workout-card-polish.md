# 016 — Workout card polish: last-time summary, RIR stepper, compact actions

Status: in progress
Updated: 2026-11-01
Starting commit: 9766ddd
Delegated: yes (see [delegation](../delegation/README.md)). Owner feedback after using the
screen on the phone (2026-11-01).

## Outcome and acceptance
1. No duplicated start load. 2. Each exercise shows when it was last done and what was
done (sets, reps, RIR). 3. RIR is a compact stepper defaulting to 1. 4. Card actions and
notes stop taking space. Accepted when the items below behave as specified, JVM tests
cover the pure functions, and `scripts/check --offline` passes. The lead runs the device
check.

## Scope
In: `android/app/src/main/java/com/repknot/app/ui/WorkoutScreen.kt`,
`android/app/src/main/java/com/repknot/app/WorkoutViewModel.kt` (only the two `previousOn`
fields and their computation in `sessionState`/`previewState`), new
`android/app/src/main/java/com/repknot/app/PreviousSummary.kt` + test, `strings.xml`,
existing app JVM tests, this file.
Out of scope (do not modify): `data`, `domain`, sync, other ViewModel behavior, new
libraries. Reading anything is fine.

## Behavior (implement exactly)
1. **Start load line removed** everywhere (session cards and preview). `Prefill.StartLoad`
   shows nothing — the prescription line already carries the planned load.
2. **Last-time summary.** Add `previousOn: LocalDate?` to `ExerciseUi` and
   `PreviewExerciseUi`: the date of the session named by `Prefill.Previous.sessionId`, from
   that `PastSession.finishedAtMillis` (epoch millis of the date at UTC midnight — convert
   with `Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()`); null for
   `StartLoad`. New pure functions in `PreviousSummary.kt`:
   - `fun lastLabel(date: LocalDate, today: LocalDate): String` → `"Last · today"`,
     `"Last · yesterday"`, `"Last · N days ago"` for 2–6 days, otherwise
     `"Last · MMM d"` (English month, e.g. `Last · Oct 25`).
   - `fun previousSummary(sets: List<PastSet>): String` →
     `"<n> set(s) · <amounts>"` + `" · RIR <list>"` when any set has RIR.
     Amounts: if every set has the same unit and load, the load once then reps joined by
     `", "` (`25 kg × 10, 10, 9`; `9p × 12, 11`; `bodyweight × 8, 6`); otherwise each set
     in full joined by `", "` (`25 kg × 10, 27.5 kg × 8`). Numbers drop `.0` (`8.5` stays).
     A set with `durationS` and no reps shows `40 s` instead of reps. RIR list joins each
     set's RIR with `", "`, `–` for a missing one (`RIR 2, –, 1`).
   Render under the prescription line, both onSurfaceVariant: line 1 `lastLabel(previousOn,
   today)`, line 2 `previousSummary(previous.sets)`. "Today" comes from
   `LocalDate.now()` in the composable. Replaces the old "Previous: …" hint in both
   session cards and preview cards. Keep the small "Suggested from previous" hint above
   the input unchanged.
3. **RIR stepper.** Replace the RIR text field with a compact row: label `RIR`, an
   `OutlinedIconButton`-sized `−` button, the value, a `+` button (use `TextButton`s or
   `FilledTonalIconButton` with text "−"/"+" — no icon library). Value is an Int, default
   **1**, range 0–10 (`−` disabled at 0, `+` at 10); it resets to 1 when the input resets
   (same keys as the other fields); when editing a set it starts from that set's RIR (1 if
   null), and the upper bound becomes max(10, that RIR) so an existing value is never clamped
   (clarified 2026-11-01). It is passed to `logSet` as text. Remove "(optional)" (string `label_rir` → "RIR").
   Load and Reps fields keep their current behavior; the three controls fit one row on a
   ~390 dp-wide phone (load and reps fields share the remaining width).
4. **Compact card.**
   - Header row: exercise name (weight 1f) and a `⋯` `TextButton` opening a `DropdownMenu`
     with **Move up**, **Move down** (disabled at the ends), **Substitute**, **Remove**
     (same actions/confirm as today). Remove the action row below **Log set**.
   - Exercise notes collapse: a `TextButton` **Notes** (or **Notes · <first 30 chars>…**
     when notes exist) toggles the field + **Save notes**; collapsed by default; collapsing
     does not save.
   - Session notes: same collapsible pattern under **Add exercise**.

## Tests
`PreviousSummaryTest`: `lastLabel` (0, 1, 2, 6, 7+ days); `previousSummary` (same-load
reps list, mixed loads, plates `p`, bodyweight, duration-only set, half reps, RIR present /
absent / partially missing, singular "1 set"). `WorkoutStateTest`: `previousOn` set for
`Previous` and null for `StartLoad` (session and preview).

## Plan / progress
1. [x] `previousOn` + `PreviousSummary.kt` + tests.
2. [x] Card UI items 1–4.
3. [x] `scripts/check --offline`; commit(s) with task 016 in the subject.

## Decisions and discoveries
- Existing sets may have RIR above 10. Owner clarified the edit upper bound is
  max(10, the existing RIR); do not clamp.

## Verification
- `scripts/check --offline` — passed after step 1 (106 tasks, 19 executed, 87 up-to-date).
- `scripts/check --offline` — passed after UI changes (106 tasks, 25 executed, 81 up-to-date). Device check remains pending.

## Handoff
- Implementation and automated checks complete; no unfinished code changes. Next: lead runs the device check, including ~390 dp layout, menu, notes, and RIR editing above 10. Keep task in progress pending that check.

## Lead review (2026-11-01)
Accepted after one clarification (RIR range vs existing values; correct stop per worker
rule 1). Diff read against items 1–4: start-load line removed; `Last · …` + summary from the
tested helpers; `⋯` menu with move/substitute/remove; collapsible exercise and session
notes; RIR stepper default 1, upper bound max(10, current). `scripts/check --offline`
passed (PreviousSummaryTest 2/2, WorkoutStateTest 4/4).
Pending: device check — the phone disconnected from adb during install. Check the ~390 dp
row fit, the menu, notes toggle, and the stepper on the owner's open Pull 1 session.
