# Repknot — Product Requirements (PRD)

**Status:** Draft v0.1 · **Scope:** Personal-use product · **Source of truth:** Supabase

## 1. Purpose

Build a low-friction workout companion that makes logging sets almost effortless and preserves enough reliable data to understand performance, recovery, and progress. Android is the primary in-gym app; a companion web app and authorized AI tools use the same data.

**Primary outcome:** Open the app, see the right workout with previous performance already visible, record what happened with minimal taps, and find it later from any supported client.

## 2. Product principles

- **Log reality, not assumptions.** Previous numbers are suggestions, never automatically marked completed.
- **Preserve context.** Store actual sets, RIR, exercise/session notes, and changes to the planned workout.
- **Separate plan from history.** Editing a session does not silently change the training program.
- **One source of truth.** Supabase holds canonical data; Android can keep local drafts while offline.
- **Ship, use, refine.** Optimize from real sessions rather than designing every future feature upfront.
- **Dark-only Android UI.** Keep the app dark regardless of the system appearance setting; no light theme or theme toggle.

## 3. V0 — First usable release

**Android workout logging**

1. Show today's suggested workout from the selected program, advancing through a workout rotation rather than relying only on weekdays. Allow manual selection of a different workout.
2. Display exercises in order with prescribed sets and rep targets. Prefill each set's weight and reps from the last relevant performance of the same exercise variation, clearly labeled as previous values.
3. Log each set's actual weight/load unit, reps, and optional RIR. Make completion, correction, adding/removing sets, and changing load quick.
4. Add, remove, reorder, or substitute exercises within today's session without changing the underlying program.
5. Capture optional notes per exercise and per session, including useful context such as pain, technique, fatigue, or unusual conditions.
6. Save an in-progress workout locally if connectivity drops; sync reliably to Supabase when online, with clear sync status and no duplicate sessions or sets.
7. Record simple body check-ins, initially weight and waist.

**Data foundation**

8. Import the populated Airtable Health base into Supabase: workouts, exercises, exercise logs, check-ins, health-state entries, and training-habit observations. Preserve source IDs, dates, raw notes, and ambiguous records rather than guessing or silently dropping them. Keep Airtable unchanged until migration is verified.
9. Support versioned training programs. Import the current active program and the November PPL proposal as distinct versions; select one as active.
10. Protect personal data with authentication, least-privilege access, and row-level security. Do not embed privileged database credentials in clients.

**Initial companion web slice (after the Android logging loop works)**

11. Display the active program, latest sessions, individual set history, notes, and weight/waist trends in a compact, readable view. Use the same canonical Supabase records; no separate web database.

## 4. AI and external access

**Product requirement:** Android, web, coding CLIs, ChatGPT, and Dots should ultimately be able to read and write appropriate Supabase data with explicit authorization.

- **First:** Validate secure read/write from a coding CLI or other authorized development client.
- **Next:** Establish narrowly scoped actions (for example `get_today_workout`, `log_set`, `get_progress`, `log_meal`) through an integration supported by each AI client.
- **Constraint:** Do not assume a ChatGPT web subscription supplies OpenAI API usage or guarantees direct Supabase/Dots connectivity. Verify each integration separately before treating it as shipped.

## 5. After V0 (intentionally deferred)

- Fast food logging assisted by ChatGPT, with reusable foods, editable nutrition estimates, and structured daily totals.
- Richer Android/web analysis: trends by exercise and muscle, volume, fatigue, recovery, progression, and program adherence.
- AI-generated observations and suggestions based on recorded evidence, subject to user approval before changing programs or logs.
- Rest timers, notifications, wearables, automated set detection, and other convenience features.
- More comprehensive body measurements and other health integrations.

## 6. Acceptance criteria for the first milestone

- Today's workout appears from the active program, and a different workout can be selected.
- Prior weights/reps are visible without being falsely logged as completed.
- A session with edited loads/reps, RIR, exercise changes, and notes can be completed on Android.
- A network interruption does not erase the draft; reconnection creates one consistent record in Supabase.
- The saved session can be read back accurately, including each set and its comments/context.
- Historical Airtable import is reconciled against source counts and spot-checked for correctness; uncertain source values remain identifiable.

## 7. Open decisions

- Confirm which imported program version is active on day one.
- Decide the simplest account sign-in for a single-user product.
- Validate ChatGPT/Dots integration options and permissions before committing to specific AI workflows.

**Out of scope for V0:** Social features, multi-user coaching, automated exercise detection, and autonomous program changes.
