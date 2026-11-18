package com.repknot.domain

import java.time.LocalDate
import java.time.temporal.IsoFields
import kotlin.math.ceil

data class CheckIn(val date: LocalDate, val weightKg: Double?, val waistCm: Double?)
/**
 * [perWeek]: the trend over up to [TREND_WEEKS] weeks, covering [weeks] of them; check-ins are
 * averaged per week first, so a busy week counts once. [recentPerWeek]: the last 4 weeks only,
 * which reacts fast (a stall, or losing too quickly).
 */
data class BodyTrend(
    val latest: Double, val latestOn: LocalDate, val perWeek: Double?,
    val weeks: Int = 0, val recentPerWeek: Double? = null,
)
const val TREND_WEEKS = 12
enum class BodyVerdict { NOT_ENOUGH_DATA, GOAL_REACHED, ON_TRACK, STALLED, TOO_FAST }
data class BodySummary(
    val waist: BodyTrend?, val weight: BodyTrend?, val goalCm: Double,
    val toGoalCm: Double?, val goalDate: LocalDate?, val weightPctPerWeek: Double?,
    val verdict: BodyVerdict,
    /** Over the last 4 weeks; the too-fast checks use this so they react quickly. */
    val recentWeightPctPerWeek: Double? = null,
)

fun bodyTrend(points: List<Pair<LocalDate, Double>>, today: LocalDate): BodyTrend? {
    val eligible = points.filter { it.first <= today }
    val latest = eligible.maxByOrNull { it.first } ?: return null
    // maxByOrNull retains the first tie; the last measurement on that date wins.
    val last = eligible.last { it.first == latest.first }
    val recent = eligible.filter { it.first >= today.minusDays(27) }
    val recentRate = if (recent.size >= 4 && span(recent) >= 14) leastSquaresPerWeek(recent) else null
    val window = eligible.filter { it.first >= today.minusDays(TREND_WEEKS * 7L - 1) }
    // One point per calendar week (mean day, mean value), then the median of all pairwise slopes
    // (Theil–Sen): a single bloated or lean week cannot swing the trend.
    val weekly = window.groupBy { it.first.get(IsoFields.WEEK_BASED_YEAR) to it.first.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR) }
        .values.map { week -> week.map { it.first.toEpochDay().toDouble() }.average() to week.map { it.second }.average() }
        .sortedBy { it.first }
    val rate = if (weekly.size >= 3 && weekly.last().first - weekly.first().first >= 14) {
        val slopes = weekly.flatMapIndexed { i, a -> weekly.drop(i + 1).map { b -> (b.second - a.second) / (b.first - a.first) * 7 } }
        slopes.sorted().let { if (it.size % 2 == 1) it[it.size / 2] else (it[it.size / 2 - 1] + it[it.size / 2]) / 2 }
    } else null
    val weeks = if (rate == null) 0 else ceil((latest.first.toEpochDay() - window.minOf { it.first.toEpochDay() } + 1) / 7.0).toInt()
    return BodyTrend(last.second, last.first, rate, weeks, recentRate)
}

private fun span(points: List<Pair<LocalDate, Double>>): Long =
    points.maxOf { it.first.toEpochDay() } - points.minOf { it.first.toEpochDay() }

private fun leastSquaresPerWeek(points: List<Pair<LocalDate, Double>>): Double {
    val xMean = points.map { it.first.toEpochDay().toDouble() }.average()
    val yMean = points.map { it.second }.average()
    val numerator = points.sumOf { (it.first.toEpochDay() - xMean) * (it.second - yMean) }
    val denominator = points.sumOf { val x = it.first.toEpochDay() - xMean; x * x }
    return numerator / denominator * 7
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
    val recentWeightPct = weight?.recentPerWeek?.let { it / weight.latest * 100 }
    val verdict = when {
        waist == null -> BodyVerdict.NOT_ENOUGH_DATA
        waist.latest <= goalCm -> BodyVerdict.GOAL_REACHED
        recentWeightPct != null && recentWeightPct < -1.0 && (declines >= 2 || recentDip) -> BodyVerdict.TOO_FAST
        rate == null -> BodyVerdict.NOT_ENOUGH_DATA
        rate <= -0.1 -> BodyVerdict.ON_TRACK
        else -> BodyVerdict.STALLED
    }
    return BodySummary(waist, weight, goalCm, toGoal, goalDate, weightPct, verdict, recentWeightPct)
}
