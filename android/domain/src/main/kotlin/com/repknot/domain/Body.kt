package com.repknot.domain

import java.time.LocalDate
import kotlin.math.ceil

data class CheckIn(val date: LocalDate, val weightKg: Double?, val waistCm: Double?)
data class BodyTrend(val latest: Double, val latestOn: LocalDate, val perWeek: Double?)
enum class BodyVerdict { NOT_ENOUGH_DATA, GOAL_REACHED, ON_TRACK, STALLED, TOO_FAST }
data class BodySummary(
    val waist: BodyTrend?, val weight: BodyTrend?, val goalCm: Double,
    val toGoalCm: Double?, val goalDate: LocalDate?, val weightPctPerWeek: Double?,
    val verdict: BodyVerdict,
)

fun bodyTrend(points: List<Pair<LocalDate, Double>>, today: LocalDate): BodyTrend? {
    val eligible = points.filter { it.first <= today }
    val latest = eligible.maxByOrNull { it.first } ?: return null
    // maxByOrNull retains the first tie; the last measurement on that date wins.
    val last = eligible.last { it.first == latest.first }
    val window = eligible.filter { it.first >= today.minusDays(27) }
    val span = (window.maxOfOrNull { it.first.toEpochDay() } ?: 0) -
        (window.minOfOrNull { it.first.toEpochDay() } ?: 0)
    val rate = if (window.size >= 4 && span >= 14) {
        val xMean = window.map { it.first.toEpochDay().toDouble() }.average()
        val yMean = window.map { it.second }.average()
        val numerator = window.sumOf { (it.first.toEpochDay() - xMean) * (it.second - yMean) }
        val denominator = window.sumOf { val x = it.first.toEpochDay() - xMean; x * x }
        numerator / denominator * 7
    } else null
    return BodyTrend(last.second, last.first, rate)
}

fun bodySummary(
    checkIns: List<CheckIn>, today: LocalDate, goalCm: Double,
    declines: Int, recentDip: Boolean,
): BodySummary {
    val waist = bodyTrend(checkIns.mapNotNull { c -> c.waistCm?.let { c.date to it } }, today)
    val weight = bodyTrend(checkIns.mapNotNull { c -> c.weightKg?.let { c.date to it } }, today)
    val toGoal = waist?.latest?.minus(goalCm)
    val rate = waist?.perWeek
    val goalDate = if (waist != null && toGoal != null && toGoal > 0 && rate != null && rate <= -0.05) {
        val days = ceil(toGoal / -rate * 7)
        val limit = java.time.temporal.ChronoUnit.DAYS.between(waist.latestOn, today.plusDays(365))
        if (days <= limit) waist.latestOn.plusDays(days.toLong()) else null
    } else null
    val weightPct = weight?.perWeek?.let { it / weight.latest * 100 }
    val verdict = when {
        waist == null -> BodyVerdict.NOT_ENOUGH_DATA
        waist.latest <= goalCm -> BodyVerdict.GOAL_REACHED
        weightPct != null && weightPct < -1.0 && (declines >= 2 || recentDip) -> BodyVerdict.TOO_FAST
        rate == null -> BodyVerdict.NOT_ENOUGH_DATA
        rate <= -0.1 -> BodyVerdict.ON_TRACK
        else -> BodyVerdict.STALLED
    }
    return BodySummary(waist, weight, goalCm, toGoal, goalDate, weightPct, verdict)
}
