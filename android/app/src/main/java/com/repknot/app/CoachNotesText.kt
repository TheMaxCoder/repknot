package com.repknot.app

import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

internal fun coachDate(date: LocalDate, today: LocalDate): String = date.format(
    DateTimeFormatter.ofPattern(if (date.year == today.year) "d MMM" else "d MMM yyyy", Locale.ENGLISH))

fun coachNoteDate(publishedAt: String, zone: ZoneId, today: LocalDate): String? =
    runCatching { coachDate(OffsetDateTime.parse(publishedAt).atZoneSameInstant(zone).toLocalDate(), today) }.getOrNull()

fun kindLabel(kind: String): String = when (kind) {
    "progress" -> "Go up"
    "hold" -> "Hold"
    "technique" -> "Technique"
    "recovery" -> "Recovery"
    else -> "Focus"
}
