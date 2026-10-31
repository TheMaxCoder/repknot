package com.repknot.app

import com.repknot.domain.LoadUnit
import com.repknot.domain.SetValues

/**
 * Parses text fields into set values, or returns null if any field is invalid.
 * Accepts a comma decimal separator because some keyboards/locales only offer ",".
 */
fun parseSetInput(load: String, unit: LoadUnit, reps: String, rir: String): SetValues? {
    val loadValue = load.trim().replace(',', '.').toDoubleOrNull() ?: return null
    val repsValue = reps.trim().toIntOrNull() ?: return null
    val rirText = rir.trim()
    val rirValue = if (rirText.isEmpty()) null else rirText.toIntOrNull() ?: return null
    return try {
        SetValues(loadValue, unit, repsValue, rirValue)
    } catch (e: IllegalArgumentException) {
        null
    }
}
