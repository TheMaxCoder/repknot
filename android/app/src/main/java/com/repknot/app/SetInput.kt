package com.repknot.app

import com.repknot.domain.LoadUnit

data class SetInput(val load: Double?, val unit: LoadUnit, val reps: Double, val rir: Int?)

fun parseSetInput(load: String, unit: LoadUnit, reps: String, rir: String): SetInput? {
    val text = load.trim()
    val loadValue = if (unit == LoadUnit.BODYWEIGHT) {
        if (text.isNotEmpty()) return null
        null
    } else {
        val value = text.replace(',', '.').toDoubleOrNull() ?: return null
        if (!value.isFinite() || value < 0) return null
        value
    }
    val repsValue = reps.trim().replace(',', '.').toDoubleOrNull() ?: return null
    if (!repsValue.isFinite() || repsValue <= 0 || repsValue % 0.5 != 0.0) return null
    val rirText = rir.trim()
    val rirValue = if (rirText.isEmpty()) null else rirText.toIntOrNull() ?: return null
    if (rirValue != null && rirValue < 0) return null
    return SetInput(loadValue, unit, repsValue, rirValue)
}
