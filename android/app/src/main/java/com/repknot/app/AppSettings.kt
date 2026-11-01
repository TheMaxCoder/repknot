package com.repknot.app

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Device settings. Upload is off by default: workouts stay on the phone until it is enabled. */
class AppSettings(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val upload = MutableStateFlow(prefs.getBoolean(KEY_UPLOAD, false))
    val uploadEnabled: StateFlow<Boolean> = upload.asStateFlow()

    fun setUploadEnabled(enabled: Boolean) {
        check(prefs.edit().putBoolean(KEY_UPLOAD, enabled).commit())
        upload.value = enabled
    }

    private companion object { const val KEY_UPLOAD = "upload_enabled" }
}
