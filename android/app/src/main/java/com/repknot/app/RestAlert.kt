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

/**
 * End-of-rest alert (task 027). One exact alarm at a time: each Log replaces it, leaving the
 * workout cancels it. High importance with vibration and no sound, so music keeps playing and
 * a mirrored smartwatch gets an alerting notification.
 */
object RestAlert {
    // Channel settings are fixed once created, and a deleted id comes back with its old
    // settings, so each change needs a new id. History: rest_timer (silent), rest_alert
    // (vibration), rest_alert_sound (sound + vibration; owner asked to drop the sound again).
    private const val CHANNEL = "rest_alert_quiet"
    private val OLD_CHANNELS = listOf("rest_timer", "rest_alert", "rest_alert_sound")
    private const val EXTRA_TEXT = "text"
    private const val TAG = "rest_alert"

    fun schedule(context: Context, atMillis: Long, text: String) {
        clearNotification(context)
        val alarms = context.getSystemService(AlarmManager::class.java)
        val intent = alarmIntent(context, text)
        if (Build.VERSION.SDK_INT < 31 || alarms.canScheduleExactAlarms())
            alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, intent)
        else alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, intent)
    }

    fun cancel(context: Context) {
        context.getSystemService(AlarmManager::class.java).cancel(alarmIntent(context, null))
        clearNotification(context)
    }

    // Only rest alerts: the check-in reminder (task 039) must survive a Log tap.
    private fun clearNotification(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.activeNotifications.filter { it.tag == TAG }.forEach { manager.cancel(it.tag, it.id) }
    }

    // Same request code and component every time, so a new schedule replaces the previous one.
    private fun alarmIntent(context: Context, text: String?): PendingIntent = PendingIntent.getBroadcast(
        context, 0, Intent(context, RestAlertReceiver::class.java).putExtra(EXTRA_TEXT, text),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    internal fun post(context: Context, text: String, id: Int? = null) {
        val manager = context.getSystemService(NotificationManager::class.java)
        if (!manager.areNotificationsEnabled()) return
        OLD_CHANNELS.forEach(manager::deleteNotificationChannel)
        manager.createNotificationChannel(NotificationChannel(CHANNEL,
            context.getString(R.string.rest_channel_name), NotificationManager.IMPORTANCE_HIGH).apply {
            description = context.getString(R.string.rest_channel_description)
            setSound(null, null)
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 300, 150, 300)
        })
        val open = PendingIntent.getActivity(context, 0,
            // Bring back the existing screen instead of stacking a second one.
            Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        // A new id per alert: reusing one id made each alert an update of the previous
        // notification, which a mirrored watch may show without buzzing.
        val now = System.currentTimeMillis()
        manager.notify(TAG, id ?: (now / 1000).toInt(), Notification.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            // Unique text per alert in case the watch de-duplicates identical notifications.
            .setContentTitle(context.getString(R.string.rest_done_at,
                java.text.DateFormat.getTimeInstance(java.text.DateFormat.SHORT).format(java.util.Date(now))))
            .setContentText(text)
            .setCategory(Notification.CATEGORY_REMINDER)
            .setWhen(now).setShowWhen(true)
            // Hypothesis (owner saw it with WhatsApp): the watch may skip the buzz while an earlier
            // notification from the app is still present, so alerts expire and the next Log clears them.
            .setTimeoutAfter(90_000)
            .setContentIntent(open)
            .setAutoCancel(true)
            .build())
    }

    internal fun textFrom(intent: Intent): String = intent.getStringExtra(EXTRA_TEXT).orEmpty()
}

class RestAlertReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) = RestAlert.post(context, RestAlert.textFrom(intent))
}
