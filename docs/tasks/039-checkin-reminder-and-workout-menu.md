# 039 — Check-in reminder and workout menu

Status: in progress · starting commit `7a58b77`

## Outcome / acceptance
1. Morning check-in reminder: Mon/Wed/Fri at 08:00 local time the phone shows a notification
   asking for weight and navel waist (after the toilet, before eating).
   - Skipped when a check-in dated today already exists; an open reminder is removed as soon
     as one is saved for today.
   - Tapping it opens Progress → Body with the Add check-in dialog open.
   - Survives reboot, app updates and clock/time-zone changes (rescheduled each time).
   - Rest alerts no longer clear every app notification, only their own.
2. Workout logger: Add exercise and Session notes move from the footer into the workout ⋯
   menu, and that ⋯ moves from the footer to the session header (owner choice, 2026-11-16).
   The footer keeps the rest timer and Finish/upload actions.
3. Owner follow-up (2026-11-16): one ⋯ only. The exercise card's ⋯ is gone; its actions (order
   up/down, Substitute, Remove from workout) are the first section of the header sheet, under the
   exercise name, then Workout (Add exercise, Session notes, Light day), then Discard. Icons on
   every item; the header ⋯ is a vertical-dots icon. No divider line above Finish.

## Scope
- App module only: `CheckInReminder.kt` (schedule rule, alarm, receiver, notification),
  manifest (receiver, `RECEIVE_BOOT_COMPLETED`), `RestAlert` tag-scoped clearing,
  navigation hook for the notification tap, `WorkoutScreen` header/menu/footer.
- No Room or server change. No settings switch: the reminder can be turned off per channel
  in Android's notification settings.

Out of scope: configurable days/time.

## Plan / progress
- [x] 1. Reminder: pure next-time rule + JVM test, alarm/receiver/notification, tap → check-in
      dialog, rest-alert clearing scoped by tag.
- [x] 2. Workout menu move.
- [x] 3. `scripts/check`; commit; architecture + status.
- [x] 4. Follow-up: merge the exercise menu, sheet redesign, footer divider removed.
- [ ] 5. Device check (owner): see Handoff.

## Decisions / discoveries
- `RestAlert` cleared with `cancelAll()` ("rest alerts are the app's only notifications");
  that would have removed a pending reminder at every Log tap, so rest alerts are now posted
  with a tag and only tagged ones are cleared.
- The reminder receiver handles only its own alarm action and the four system actions
  (lint `UnsafeProtectedBroadcastReceiver`). A late alarm still reminds on the same day if it
  is a reminder day, never on another day.
- Reordering from the sheet keeps it open and shows the live position, so several moves take
  one visit. The per-set ⋯ (Delete) in logged-set rows is unchanged.
- The notification tap leaves a running workout's logger like system Back does (session kept).
- No emulator/AVD exists on this machine; the only attached device is the owner's phone, so
  no install or on-device check was done by the lead.

## Verification
- 2026-11-16 `scripts/check --offline`: BUILD SUCCESSFUL; `CheckInReminderTest` 8/8 pass
  (next-time rule: same morning, exactly 08:00, Tue→Wed, Fri→Mon, month end). Lint: the one new
  issue (unsafe protected-broadcast receiver) was fixed; 27 issues remain, none new.
- 2026-11-16 installed on the owner's phone at their request (`:app:installDebug`); without
  opening the app, the update broadcast scheduled the next reminder: `dumpsys alarm` shows an
  exact RTC_WAKEUP alarm for Wednesday 08:00. Notification, tap and menu still owner-checked.
- 2026-11-16 follow-up: `scripts/check --offline` BUILD SUCCESSFUL, lint 27 (none new; the
  now-unused `workout_options` string removed); installed on the phone; layout not inspected by
  the lead.

## Handoff
Status: code done, device check pending (owner). On the phone after
`cd android && ./gradlew :app:installDebug`:
1. Open the app once (schedules the alarm). Debug trigger without waiting for 08:00:
   `adb shell am broadcast -n com.repknot.app/.DebugRestAlertReceiver --ez checkin true`.
2. Tap the notification: Progress → Body opens with Add check-in; saving a check-in dated
   today removes an open reminder.
3. Log a set during a workout while the reminder is showing: the reminder stays.
4. Workout logger: the only ⋯ (header, next to the timer) opens the exercise section (order
   arrows, Substitute, Remove) and the Workout section (Add exercise, Session notes, Light day),
   then Discard; the footer shows only the rest line and Finish, with no divider above it.
5. Optional: `adb shell dumpsys alarm | grep -A3 repknot` shows the next alarm at 08:00 on
   the next Mon/Wed/Fri.
