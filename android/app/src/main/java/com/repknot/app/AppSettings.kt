package com.repknot.app

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Device settings. Uploading is per workout / check-in (task 028), not a setting. */
class AppSettings(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val waistGoal = MutableStateFlow(prefs.getString(KEY_WAIST_GOAL, null)?.toDoubleOrNull() ?: 80.0)
    val waistGoalCm: StateFlow<Double> = waistGoal.asStateFlow()
    private val overrides = MutableStateFlow(prefs.all.mapNotNull { (key, value) ->
        if (key.startsWith(KEY_REST_PREFIX) && value is Int) key.removePrefix(KEY_REST_PREFIX) to value else null
    }.toMap())
    val restOverrides: StateFlow<Map<String, Int>> = overrides.asStateFlow()

    fun setRestOverride(exerciseId: String, seconds: Int) {
        check(prefs.edit().putInt(KEY_REST_PREFIX + exerciseId, seconds).commit())
        overrides.value = overrides.value + (exerciseId to seconds)
    }

    fun setWaistGoalCm(value: Double) {
        check(prefs.edit().putString(KEY_WAIST_GOAL, value.toString()).commit())
        waistGoal.value = value
    }

    private companion object {
        const val KEY_REST_PREFIX = "rest_target_s."
        const val KEY_WAIST_GOAL = "waist_goal_cm"
    }
}
