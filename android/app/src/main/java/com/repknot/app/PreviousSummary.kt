package com.repknot.app

import com.repknot.domain.LoadUnit
import com.repknot.domain.PastSet
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

fun lastLabel(date: LocalDate, today: LocalDate): String {
    val days = ChronoUnit.DAYS.between(date, today)
    val label = when (days) {
        0L -> "today"
        1L -> "yesterday"
        in 2L..6L -> "$days days ago"
        else -> date.format(DateTimeFormatter.ofPattern("MMM d", Locale.ENGLISH))
    }
    return "Last · $label"
}

private fun number(value: Double): String = if (value % 1.0 == 0.0) value.toLong().toString() else value.toString()
private fun load(set: PastSet): String? = when (set.unit) {
    LoadUnit.BODYWEIGHT -> "bodyweight"
    LoadUnit.PLATES -> set.load?.let { "${number(it)}p" }
    else -> set.load?.let { "${number(it)} ${set.unit.name.lowercase(Locale.ROOT)}" }
}
private fun amount(set: PastSet): String? = set.reps?.let(::number) ?: set.durationS?.let { "$it s" }

fun previousSummary(sets: List<PastSet>): String {
    val sameLoad = sets.isNotEmpty() && sets.all { it.unit == sets.first().unit && it.load == sets.first().load }
    val amounts = if (sameLoad) {
        listOfNotNull(load(sets.first()), sets.mapNotNull(::amount).joinToString(", ").ifEmpty { null }).joinToString(" × ")
    } else {
        sets.joinToString(", ") { listOfNotNull(load(it), amount(it)).joinToString(" × ") }
    }
    return buildString {
        append("${sets.size} ${if (sets.size == 1) "set" else "sets"}")
        if (amounts.isNotEmpty()) append(" · $amounts")
        if (sets.any { it.rir != null }) append(" · RIR ${sets.joinToString(", ") { it.rir?.toString() ?: "–" }}")
    }
}
