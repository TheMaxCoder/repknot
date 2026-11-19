package com.repknot.app

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

internal val CHECK_IN_DAYS = setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY)
internal val CHECK_IN_TIME: LocalTime = LocalTime.of(8, 0)

internal fun checkInDue(now: LocalDateTime, checkedDates: Collection<LocalDate>): Boolean =
    now.dayOfWeek in CHECK_IN_DAYS && !now.toLocalTime().isBefore(CHECK_IN_TIME) && now.toLocalDate() !in checkedDates

/** The first reminder day at [CHECK_IN_TIME] strictly after [now]. */
internal fun nextCheckInReminder(now: LocalDateTime): LocalDateTime {
    var day = now.toLocalDate()
    while (true) {
        val at = day.atTime(CHECK_IN_TIME)
        if (day.dayOfWeek in CHECK_IN_DAYS && at.isAfter(now)) return at
        day = day.plusDays(1)
    }
}

/**
 * Morning check-in reminder (task 039): Mon/Wed/Fri 08:00, skipped when today's check-in exists.
 * One alarm at a time; each firing, boot, app update or clock change schedules the next one.
 */
object CheckInReminder {
    private const val CHANNEL = "checkin_reminder"
    private const val TAG = "checkin_reminder"
    private const val ID = 1
    private const val ACTION_FIRE = "com.repknot.app.CHECK_IN_REMINDER"
    const val EXTRA_OPEN_CHECK_IN = "open_check_in"

    fun schedule(context: Context) {
        val zone = ZoneId.systemDefault()
        val at = nextCheckInReminder(LocalDateTime.now(zone)).atZone(zone).toInstant().toEpochMilli()
        val alarms = context.getSystemService(AlarmManager::class.java)
        val intent = alarmIntent(context)
        if (Build.VERSION.SDK_INT < 31 || alarms.canScheduleExactAlarms())
            alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, intent)
        else alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, intent)
    }

    fun clear(context: Context) = context.getSystemService(NotificationManager::class.java).cancel(TAG, ID)

    private fun alarmIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context, 0, Intent(context, CheckInReminderReceiver::class.java).setAction(ACTION_FIRE),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    internal fun isFire(intent: Intent) = intent.action == ACTION_FIRE

    internal fun post(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        if (!manager.areNotificationsEnabled()) return
        manager.createNotificationChannel(NotificationChannel(CHANNEL,
            context.getString(R.string.checkin_channel_name), NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = context.getString(R.string.checkin_channel_description)
        })
        val open = PendingIntent.getActivity(context, 1,
            Intent(context, MainActivity::class.java).putExtra(EXTRA_OPEN_CHECK_IN, true)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        manager.notify(TAG, ID, Notification.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.checkin_reminder_title))
            .setContentText(context.getString(R.string.checkin_reminder_text))
            .setCategory(Notification.CATEGORY_REMINDER)
            .setContentIntent(open)
            .setAutoCancel(true)
            .build())
    }
}

class CheckInReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            // Boot, app update, clock or time-zone change: alarms are gone or due at a stale time.
            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED, Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED -> { CheckInReminder.schedule(context); return }
        }
        if (!CheckInReminder.isFire(intent)) return
        val app = context.applicationContext as RepknotApplication
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val today = LocalDate.now()
                // A late alarm (phone off at 08:00) still reminds on the same day, never on another.
                if (today.dayOfWeek in CHECK_IN_DAYS && app.database.checkIns().all().none { it.checkedOn == today.toString() })
                    CheckInReminder.post(context)
            } catch (e: Exception) {
                Log.w("RepknotReminder", "Check-in reminder failed", e)
            } finally {
                CheckInReminder.schedule(context)
                pending.finish()
            }
        }
    }
}
