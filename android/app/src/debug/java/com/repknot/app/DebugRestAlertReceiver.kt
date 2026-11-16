package com.repknot.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Debug-only: `adb shell am broadcast -n com.repknot.app/.DebugRestAlertReceiver --ei id 5
 * --es text "Probe"` posts one rest alert through the real channel, with an optional fixed id;
 * `--ez clear true` removes the app's notifications instead; `--ez checkin true` posts the
 * check-in reminder (task 039) without waiting for its alarm.
 */
class DebugRestAlertReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.getBooleanExtra("clear", false)) {
            context.getSystemService(android.app.NotificationManager::class.java).cancelAll(); return
        }
        if (intent.getBooleanExtra("checkin", false)) { CheckInReminder.post(context); return }
        val id = if (intent.hasExtra("id")) intent.getIntExtra("id", 0) else null
        RestAlert.post(context, intent.getStringExtra("text") ?: "Debug probe", id)
    }
}
