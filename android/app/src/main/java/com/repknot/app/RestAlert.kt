package com.repknot.app

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.provider.Settings
import android.os.Build

/**
 * End-of-rest alert (task 027). One exact alarm at a time: each Log replaces it, leaving the
 * workout cancels it. The notification sounds and vibrates so that a mirrored watch
 * (the paired smartwatch) treats it as an alert; the sound can be changed in system channel settings.
 */
object RestAlert {
    // Channel settings are fixed once created. v1 ("rest_timer", silent) and v2 ("rest_alert",
    // vibration only) both reached the watch notification list but the watch never buzzed,
    // so v3 also plays the default notification sound (it ducks music briefly).
    private const val CHANNEL = "rest_alert_sound"
    private val OLD_CHANNELS = listOf("rest_timer", "rest_alert")
    private const val NOTIFICATION_ID = 27
    private const val EXTRA_TEXT = "text"

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

    private fun clearNotification(context: Context) =
        context.getSystemService(NotificationManager::class.java).cancel(NOTIFICATION_ID)

    // Same request code and component every time, so a new schedule replaces the previous one.
    private fun alarmIntent(context: Context, text: String?): PendingIntent = PendingIntent.getBroadcast(
        context, 0, Intent(context, RestAlertReceiver::class.java).putExtra(EXTRA_TEXT, text),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    internal fun post(context: Context, text: String) {
        val manager = context.getSystemService(NotificationManager::class.java)
        if (!manager.areNotificationsEnabled()) return
        OLD_CHANNELS.forEach(manager::deleteNotificationChannel)
        manager.createNotificationChannel(NotificationChannel(CHANNEL,
            context.getString(R.string.rest_channel_name), NotificationManager.IMPORTANCE_HIGH).apply {
            description = context.getString(R.string.rest_channel_description)
            setSound(Settings.System.DEFAULT_NOTIFICATION_URI, AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_NOTIFICATION).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 300, 150, 300)
        })
        val open = PendingIntent.getActivity(context, 0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        manager.notify(NOTIFICATION_ID, Notification.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.rest_done))
            .setContentText(text)
            .setCategory(Notification.CATEGORY_REMINDER)
            .setContentIntent(open)
            .setAutoCancel(true)
            .build())
    }

    internal fun textFrom(intent: Intent): String = intent.getStringExtra(EXTRA_TEXT).orEmpty()
}

class RestAlertReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) = RestAlert.post(context, RestAlert.textFrom(intent))
}
