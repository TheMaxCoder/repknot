package com.repknot.app

import com.repknot.data.CheckInRow
import com.repknot.domain.BodySummary
import com.repknot.domain.BodyVerdict
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.abs

private val bodyDateFormat = DateTimeFormatter.ofPattern("MMM d", Locale.ENGLISH)
private fun date(value: LocalDate) = value.format(bodyDateFormat)
private fun number(value: Double) = String.format(Locale.ENGLISH, "%.1f", value).removeSuffix(".0")
private fun signed(value: Double) = (if (value < 0) "−" else if (value > 0) "+" else "") + number(abs(value))

fun waistLine(s: BodySummary): String {
    val waist = s.waist ?: return "No waist measurement yet"
    val remaining = s.toGoalCm ?: return "Waist ${number(waist.latest)} cm"
    return "Waist ${number(waist.latest)} cm · " + if (remaining <= 0) "goal reached (${number(s.goalCm)} cm)"
        else "${number(remaining)} cm to go"
}
fun rateLine(s: BodySummary): String {
    val waist = s.waist ?: return ""
    val rate = waist.perWeek ?: return "Not enough check-ins for a trend (needs 4 over 2+ weeks)"
    return "${signed(rate)} cm/week over 4 weeks" + (s.goalDate?.let { " · ${number(s.goalCm)} cm around ${date(it)}" } ?: "")
}
fun weightLine(s: BodySummary): String {
    val weight = s.weight ?: return ""
    return "Weight ${number(weight.latest)} kg" + (weight.perWeek?.let { rate ->
        " · ${signed(rate)} kg/week" + (s.weightPctPerWeek?.let { " (${signed(it)} %/week)" } ?: "")
    } ?: "")
}
fun lastCheckInLine(latest: LocalDate, today: LocalDate): String {
    val days = ChronoUnit.DAYS.between(latest, today)
    return "Last check-in ${date(latest)}" + if (days > 7) " · $days days ago — time for a check-in" else ""
}
fun verdictLine(s: BodySummary, declines: Int, recentDip: Boolean): String = when (s.verdict) {
    BodyVerdict.ON_TRACK -> if (declines == 0 && !recentDip) "On track — waist down, strength holding."
        else "On track — waist down; keep an eye on the exercises below."
    BodyVerdict.STALLED -> "Waist hasn't moved in 4 weeks — consider tightening the deficit."
    BodyVerdict.TOO_FAST -> "Losing over 1 % of bodyweight a week while strength slips — consider eating a bit more."
    BodyVerdict.GOAL_REACHED -> "Goal reached — time to set the next phase."
    BodyVerdict.NOT_ENOUGH_DATA -> ""
}
fun checkInLine(row: CheckInRow): String = listOfNotNull(
    date(LocalDate.parse(row.checkedOn)), row.weightKg?.let { "${number(it)} kg" },
    row.waistCm?.let { "${number(it)} cm" }, if (row.pending) "not uploaded" else null,
).joinToString(" · ")
