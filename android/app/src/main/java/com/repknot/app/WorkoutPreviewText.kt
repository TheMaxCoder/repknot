package com.repknot.app

import com.repknot.domain.LoadUnit
import com.repknot.domain.PastSet
import com.repknot.domain.Prefill
import com.repknot.domain.Prescription
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

private const val DASH = "–"

data class PreviewValues(val reps: String, val rir: String, val load: String)

private fun previewNumber(value: Double): String =
    if (value % 1.0 == 0.0 && value >= Long.MIN_VALUE.toDouble() && value < Long.MAX_VALUE.toDouble()) value.toLong().toString() else value.toString()

private fun previewRange(min: Int?, max: Int?): String = when {
    min == null -> max?.toString() ?: DASH
    max == null || min == max -> min.toString()
    else -> "$min–$max"
}

private fun previewLoad(load: Double?, unit: LoadUnit): String = when (unit) {
    LoadUnit.BODYWEIGHT -> load?.let { "${previewNumber(it)} bodyweight" } ?: "bodyweight"
    LoadUnit.PLATES -> load?.let { "${previewNumber(it)}p" } ?: DASH
    else -> load?.let { "${previewNumber(it)} ${unit.name.lowercase(Locale.ROOT)}" } ?: DASH
}

fun previewPlan(p: Prescription) = PreviewValues(
    "${p.sets} × ${previewRange(p.repMin, p.repMax)}",
    previewRange(p.rirMin, p.rirMax), previewLoad(p.load, p.unit))

/**
 * Load column of a started workout's plan (the row's stored unit text). A substitute keeps the
 * slot's sets, reps and RIR, but the slot's load and unit belong to the exercise it replaced, so
 * none is shown (null).
 */
fun sessionPlanLoad(load: Double?, unit: String?, substituted: Boolean): String? = when {
    substituted -> null
    unit == "bodyweight" -> "bodyweight"
    unit == "plates" -> load?.let { "${previewNumber(it)}p" }
    else -> load?.let { listOfNotNull(previewNumber(it), unit).joinToString(" ") }
}

private fun previewAmount(set: PastSet): String = listOfNotNull(
    set.reps?.let(::previewNumber), set.durationS?.let { "$it s" }
).joinToString(" / ").ifEmpty { DASH }

fun previewLast(previous: Prefill): List<PreviewValues> {
    val sets = (previous as? Prefill.Previous)?.sets.orEmpty()
    if (sets.isEmpty()) return emptyList()
    val sameLoad = sets.all { it.load == sets.first().load && it.unit == sets.first().unit }
    return if (sameLoad) listOf(PreviewValues(
        sets.joinToString(", ", transform = ::previewAmount),
        sets.joinToString(", ") { it.rir?.toString() ?: DASH },
        previewLoad(sets.first().load, sets.first().unit)))
    else sets.map { PreviewValues(previewAmount(it), it.rir?.toString() ?: DASH, previewLoad(it.load, it.unit)) }
}

fun previewDate(date: LocalDate?, today: LocalDate): String {
    if (date == null) return DASH
    val days = ChronoUnit.DAYS.between(date, today)
    return when (days) {
        0L -> "today"
        in 1L..6L -> "${days}d ago"
        else -> date.format(DateTimeFormatter.ofPattern(if (date.year == today.year) "MMM d" else "MMM d, yyyy", Locale.ENGLISH))
    }
}
