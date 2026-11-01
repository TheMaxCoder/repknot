package com.repknot.app

import android.content.Context

interface CurrentSession {
    fun get(): String?
    fun set(id: String?)
}

class PrefsCurrentSession(context: Context) : CurrentSession {
    private val prefs = context.getSharedPreferences("workout_ids", Context.MODE_PRIVATE)
    override fun get(): String? = prefs.getString("current_session", null)
    override fun set(id: String?) {
        check(prefs.edit().putString("current_session", id).commit())
    }
}
