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
    private val waistGoal = MutableStateFlow(prefs.getString(KEY_WAIST_GOAL, null)?.toDoubleOrNull() ?: 80.0)
    val waistGoalCm: StateFlow<Double> = waistGoal.asStateFlow()

    fun setWaistGoalCm(value: Double) {
        check(prefs.edit().putString(KEY_WAIST_GOAL, value.toString()).commit())
        waistGoal.value = value
    }

    fun setUploadEnabled(enabled: Boolean) {
        check(prefs.edit().putBoolean(KEY_UPLOAD, enabled).commit())
        upload.value = enabled
    }

    private companion object {
        const val KEY_UPLOAD = "upload_enabled"
        const val KEY_WAIST_GOAL = "waist_goal_cm"
    }
}
