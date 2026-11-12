# 034 — Coach notes on Home

Status: in progress
Starting commit: dc134d8
Date: 2026-11-12
Design: [ADR 005](../decisions/005-coach-notes.md)

## Outcome / acceptance

After a training review, the coach publishes a short brief with
`scripts/repknot publish-notes <file>`; on the next refresh the phone shows it on Home in a
flat, collapsible "Coach notes" card, works offline, and survives app restarts.

Lead (done before delegation, see Progress): server table + pgTAP test, CLI
`publish-notes`/`notes`, ADR. Worker step (below): Android data + Home UI.

### Worker step: Android

**Data (`android/data`)**
1. New file `CoachNotesStore.kt`:
   - Room entity `CoachNoteRow` (table `coach_notes`): `id: String` (PK), `publishedAt: String`
     (server ISO timestamp as received), `basedOn: String?`, `headline: String`, `items: String`
     (the server `items` JSON array, stored verbatim as text).
   - `@Serializable ServerCoachNote(id, @SerialName("published_at") publishedAt,
     @SerialName("based_on") basedOn: String? = null, headline, items: JsonArray)` and
     `internal const val COACH_NOTE_COLUMNS = "id,published_at,based_on,headline,items"`.
   - `CoachNoteDao`: `observeLatest(): Flow<CoachNoteRow?>` (the single cached row, newest
     `publishedAt` if somehow several) and a `@Transaction suspend fun replace(latest: CoachNoteRow?)`
     that deletes all rows then inserts `latest` if non-null.
   - `fun parseCoachNoteItems(json: String): List<CoachNoteItem>` using the existing `codec`
     (`SessionMapping.kt`, ignores unknown keys). Skip any element that is not an object or
     lacks a non-blank string `text`; `kind` defaults to `"focus"` when missing or not a string;
     `exercise_id` optional. Malformed JSON → empty list (never throw).
2. `WorkoutDatabase`: add `CoachNoteRow`, `abstract fun coachNotes(): CoachNoteDao`, version
   10, `MIGRATION_9_10` creating the table with exactly the SQL Room expects (copy from the
   generated `android/data/schemas/.../10.json`; keep it exported), add to `open()`.
   `MigrationTest`: add every existing full-database builder's `addMigrations(...)` list
   `MIGRATION_9_10`, and add `v9UpgradesToV10WithEmptyCoachNotes` following the existing
   pattern (create v9 with one session row, migrate+validate, open, assert session intact and
   `coachNotes().observeLatest().first()` is null). Instrumented: worker only compiles it.
3. `SessionSync`: optional constructor param `coachNotes: CoachNoteDao? = null` (after
   `checkIns`). In `refreshAndPush`, after the check-in cache replace, fetch
   `client.from("coach_notes").select(Columns.raw(COACH_NOTE_COLUMNS)) { order("published_at",
   Order.DESCENDING); limit(1) }.decodeList<ServerCoachNote>()` and `replace` with the first
   (or null → clears the cache). Wrap only this fetch+replace in the same try/catch shape as the
   uploads (rethrow `CancellationException`, otherwise record as `firstFailure` if none yet), so a
   coach-notes failure never prevents session/check-in uploads. Change nothing else in sync.
4. `RepknotApplication`: pass `database.coachNotes()` to `SessionSync`.

**Domain (`android/domain`, pure Kotlin)** — new `CoachNotes.kt`:
- `data class CoachNoteItem(val kind: String, val text: String, val exerciseId: String?)`
  (data's parser returns these) and
  `data class CoachNoteGroups(val forWorkout: List<CoachNoteItem>, val general: List<CoachNoteItem>, val other: List<CoachNoteItem>)`.
- `fun groupCoachNotes(items: List<CoachNoteItem>, workoutExerciseIds: Set<String>): CoachNoteGroups`:
  `forWorkout` = exerciseId in the set; `general` = exerciseId null; `other` = the rest. Each
  group keeps input order.

**App (`android/app`)**
5. `AppSettings`: `seenCoachNoteId: StateFlow<String?>` + `markCoachNoteSeen(id)` (same
   prefs/commit pattern as the waist goal).
6. `CoachNotesViewModel` (+ `Factory`, created once in `MainActivity` like the others) exposing
   `StateFlow<CoachNotesUiState>`: `note: CoachNoteUi?` (`id`, `publishedAt`, `basedOn`,
   `headline`, parsed `items`), `exerciseNames: Map<String, String>` (from
   `ReferenceDao.getExercises()`, re-read on each new cached note), `unseen: Boolean`
   (note != null && id != seenCoachNoteId). `fun markSeen()`.
7. `CoachNotesText.kt` (pure, JVM-tested): `coachNoteDate(publishedAt: String, zone: ZoneId,
   today: LocalDate): String?` → `"12 Nov"` for the current year, `"12 Nov 2025"` otherwise
   (English, `d MMM` / `d MMM yyyy`), from `OffsetDateTime.parse` converted to `zone`; parse
   failure → null (date omitted). `kindLabel(kind)` → "Go up" / "Hold" / "Technique" /
   "Recovery" / "Focus", any other value → "Focus". Use string resources where the screen
   shows text; these helpers may return resource ids instead of strings if you prefer.
8. Home card (`ui/HomeScreen.kt`, new `ui/CoachNotesCard.kt`), placed after the
   "Choose another workout" button and before the "From Progress" heading; hidden entirely when
   there is no note. Flat tonal `Surface` (surfaceContainer, 8 dp corners, no elevation) like
   the other Home cards.
   - Header (whole row clickable, role Button, ≥ 48 dp, state description expanded/collapsed):
     36 dp circle (primaryContainer) with a notes icon; column with a label row
     `"Coach notes · 12 Nov"` (labelMedium, primary; omit `· date` when null) followed by a small
     "New" pill (primary container colors, labelSmall) when `unseen`; the headline below
     (titleMedium, wraps). Trailing chevron rotating 0→180° (animated) when expanded.
   - Collapsed summary line under the header (bodySmall, onSurfaceVariant):
     `"N notes"` (plurals) plus `" · M for <selected workout name>"` when M > 0.
   - Expanded body with `AnimatedVisibility` (expandVertically + fadeIn / shrink + fadeOut):
     groups from `groupCoachNotes(items, selected workout's exercise ids)` in order
     forWorkout → general → other. Group headings (labelMedium, onSurfaceVariant):
     "For <workout name>", "General", "Other workouts" — shown only when at least two groups are
     non-empty. Each item: Row with a 32 dp circle holding the kind icon, then a column with the
     exercise name (labelLarge) when `exerciseId` has a known name, and the text (bodyMedium,
     wraps, never truncated). Spacing 12–16 dp between items; thin divider between groups.
     Kind colors (container / icon): progress = primaryContainer/onPrimaryContainer;
     technique = secondaryContainer/onSecondaryContainer; recovery = tertiaryContainer/
     onTertiaryContainer; hold = surfaceContainerHighest/onSurfaceVariant; focus and unknown =
     surfaceContainerHighest/primary. Icon contentDescription = kind label.
     If `basedOn` is non-null, a final caption "Based on workouts through 11 Nov" (bodySmall,
     onSurfaceVariant; same date style, from the `YYYY-MM-DD` value; omit if unparsable).
   - Expanded state: `rememberSaveable(note.id)`, default collapsed. Expanding calls `markSeen()`.
   - Icons: new small local vector drawables (24 dp viewport, Material Symbols outline style,
     single path, `?attr`-free fill `#FF000000` tinted by `Icon`): `ic_coach_notes` (lightbulb or
     sticky note), `ic_expand_more` (chevron), `ic_note_progress` (arrow up), `ic_note_hold`
     (equals / pause), `ic_note_technique` (target), `ic_note_recovery` (healing/plus),
     `ic_note_focus` (flag or dot). No icon dependencies.
   - Do not change the existing workout card, chooser, or Progress summary.

**Tests (JVM)**: `groupCoachNotes` (empty, mixed, order kept, id in set vs not);
`parseCoachNoteItems` (valid, unknown keys, missing kind → focus, missing/blank text skipped,
non-object elements skipped, malformed JSON → empty); `coachNoteDate` (same year, other year,
zone shift across midnight, unparsable → null); `kindLabel` (all five + unknown).

## Scope / out of scope

Worker may modify: `android/data/src/main/.../{CoachNotesStore,WorkoutDatabase,SessionSync}.kt`,
`android/data/schemas/` (generated 10.json), `android/data/src/androidTest/.../MigrationTest.kt`,
data JVM tests, new `android/domain/.../CoachNotes.kt` + test, `android/app/src/main/.../{AppSettings,
RepknotApplication,MainActivity,AppNavigation}.kt` (wiring only), new `CoachNotesViewModel.kt`,
`CoachNotesText.kt`, `ui/CoachNotesCard.kt`, `ui/HomeScreen.kt` (insert the card + params),
app resources (strings/plurals/drawables), app JVM tests, and this task file's Decisions/Verification.

Out of scope (do not modify): `supabase/`, `scripts/`, sync/push/conflict logic beyond step 3,
other screens, build files and dependencies, `docs/` other than this task, worker rules. Do not
read `.env`, credentials, `private-data/`, or `work/`. No adb, install, or database commands.
Use only synthetic values in tests. Lead owns status/architecture docs.

## Plan / progress

- [x] Lead: migration `20261112010000_coach_notes.sql` + `supabase/tests/009_coach_notes_test.sql`,
  CLI `publish-notes`/`notes`, `scripts/db --yes` passthrough, ADR 005.
- [x] Lead: owner consent (2026-11-12), then `scripts/db push --yes` and `scripts/db test --yes`.
- [ ] Worker: Android step above; `scripts/check --offline`; commit.
- [ ] Lead: review diff, re-run checks, publish a real brief, install and check Home on the phone,
  run `MigrationTest` on the phone, coach workspace publish wrapper + instructions, docs.

## Decisions / discoveries

- Supabase CLI 3.1 labels the project production and refuses hosted mutations (`db push`, and
  `db query` used by `scripts/db test`) in agent mode without `--yes`. Added an opt-in `--yes`
  passthrough to `scripts/db`; the lead passes it only after the owner approves a run.
- Server items are stored as JSON and parsed leniently on the phone so a newer kind or field
  never breaks an older app.

## Verification

- CLI: `deno check` on a copy of `scripts/repknot` passed.
- DB (owner approved the `--yes` run): `scripts/db push --yes` applied
  `20261112010000_coach_notes.sql`; `scripts/db test --yes` — all files ok, 0 `not ok`
  (009: 11/11). CLI: invalid file and unknown slug rejected before any write; `notes` reads
  "(no coach notes)".

## Handoff

Next action: worker Android step.
