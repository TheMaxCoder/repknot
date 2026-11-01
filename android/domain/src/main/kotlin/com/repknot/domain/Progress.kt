package com.repknot.domain

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit
import kotlin.math.ceil
import kotlin.math.max

data class Exposure(val sessionId: String, val date: LocalDate, val name: String,
    val unit: LoadUnit, val sets: List<PastSet>, val plannedSets: Int?, val light: Boolean) {
    val timed: Boolean get() = sets.all { it.reps == null && it.durationS != null }
    val topLoad: Double? get() = sets.mapNotNull { it.load }.maxOrNull()
    val topSets: List<PastSet> get() = sets.filter {
        it.load == topLoad && if (timed) it.durationS != null else it.reps != null
    }
}

enum class Comparison { BETTER, SAME, WORSE, NOT_COMPARABLE }

fun compare(x: Exposure, y: Exposure): Comparison {
    if (x.unit != y.unit || x.timed != y.timed || x.topLoad != y.topLoad) return Comparison.NOT_COMPARABLE
    val n = minOf(x.topSets.size, y.topSets.size)
    if (n == 0) return Comparison.NOT_COMPARABLE
    val xs = x.topSets.take(n)
    val ys = y.topSets.take(n)
    val effort = !x.timed && (xs + ys).all { it.rir != null }
    fun value(s: PastSet): Double = if (x.timed) s.durationS!!.toDouble()
        else s.reps!! + if (effort) s.rir!! else 0
    val base = ys.sumOf(::value)
    val delta = xs.sumOf(::value) - base
    return when {
        delta >= if (x.timed) 5.0 else 1.0 -> Comparison.BETTER
        delta <= -max(if (x.timed) 5.0 * n else n.toDouble(), 0.05 * base) -> Comparison.WORSE
        else -> Comparison.SAME
    }
}

private fun ordered(history: List<PastSession>): List<PastSession> = history.withIndex()
    .filter { it.value.finishedAtMillis != null }
    .sortedWith(compareBy({ it.value.finishedAtMillis }, { it.index })).map { it.value }

private fun exposure(session: PastSession, id: String, unit: LoadUnit): Exposure? {
    val matching = session.exercises.filter { it.exerciseId == id }.sortedBy { it.position }
    val sets = matching.flatMap { it.sets.sortedBy { s -> s.position } }.filter { it.unit == unit }
    if (sets.isEmpty()) return null
    return Exposure(session.id, Instant.ofEpochMilli(session.finishedAtMillis!!).atZone(ZoneOffset.UTC).toLocalDate(),
        matching.first().exerciseName, unit, sets, matching.first().plannedSets, session.light)
}

private fun lineage(history: List<PastSession>, id: String, unit: LoadUnit? = null): List<Exposure> {
    val sessions = ordered(history)
    val chosen = unit ?: sessions.asReversed().firstNotNullOfOrNull { session ->
        session.exercises.filter { it.exerciseId == id }.sortedBy { it.position }
            .firstNotNullOfOrNull { it.sets.minByOrNull { set -> set.position }?.unit }
    } ?: return emptyList()
    return sessions.mapNotNull { exposure(it, id, chosen) }
}

fun prescriptionFor(exerciseId: String, prescriptions: List<Prescription>): Prescription? =
    prescriptions.filter { it.exerciseId == exerciseId }.minByOrNull { it.position }

fun readyToMoveUp(e: Exposure, p: Prescription): Boolean {
    val repMax = p.repMax ?: return false
    val need = max(p.sets, 1)
    return !e.timed && e.topSets.size >= need && e.topSets.take(need).all {
        it.reps!! >= repMax && (p.rirMin == null || (it.rir != null && it.rir >= p.rirMin))
    }
}

enum class ProgressStatus { IMPROVING, MAINTAINING, POSSIBLE_DECLINE, NOT_ENOUGH_DATA }
sealed interface ProgressReason {
    data class BackAfterGap(val days: Long) : ProgressReason
    data class FewSessions(val count: Int) : ProgressReason
    data class MovedUp(val load: Double, val unit: LoadUnit, val on: LocalDate) : ProgressReason
    data class MoreReps(val load: Double?, val unit: LoadUnit, val on: LocalDate) : ProgressReason
    data class BelowUsual(val load: Double?, val unit: LoadUnit) : ProgressReason
    data class LoadDown(val from: Double, val to: Double, val unit: LoadUnit) : ProgressReason
    data class NoNewBestSince(val lastBest: LocalDate?, val sessionsSince: Int) : ProgressReason
}
data class ExerciseProgress(val exerciseId: String, val name: String, val unit: LoadUnit,
    val status: ProgressStatus, val reason: ProgressReason, val readyStreak: Int,
    val daysSinceLast: Long, val exposures: List<Exposure>, val bestSessionIds: Set<String>)

private fun best(x: Exposure, earlier: List<Exposure>, p: Prescription?): ProgressReason? {
    if (earlier.isEmpty()) return null
    val loads = earlier.mapNotNull { it.topLoad }
    if (x.topLoad != null && loads.isNotEmpty() && x.topLoad!! > loads.max() &&
        x.topSets.mapNotNull { it.reps }.let { it.isNotEmpty() && it.average() >= (p?.repMin ?: 1) })
        return ProgressReason.MovedUp(x.topLoad!!, x.unit, x.date)
    val comparable = earlier.filter { compare(x, it) != Comparison.NOT_COMPARABLE }
    if (x.topLoad == loads.maxOrNull() && comparable.isNotEmpty() &&
        comparable.all { compare(x, it) == Comparison.BETTER })
        return ProgressReason.MoreReps(x.topLoad, x.unit, x.date)
    return null
}

fun progress(history: List<PastSession>, prescriptions: List<Prescription>, today: LocalDate): List<ExerciseProgress> {
    val ids = ordered(history).flatMap { it.exercises }.map { it.exerciseId }.distinct()
    return ids.mapNotNull { id ->
        val all = lineage(history, id)
        if (all.isEmpty()) return@mapNotNull null
        val normal = all.filterNot { it.light }
        val p = prescriptionFor(id, prescriptions)
        val bests = normal.mapIndexedNotNull { i, e -> best(e, normal.take(i), p)?.let { i to it } }
        val latestBest = bests.lastOrNull()
        val gap = (max(1, normal.size - 2) until normal.size).map { i ->
            ChronoUnit.DAYS.between(normal[i - 1].date, normal[i].date)
        }.lastOrNull { it >= 14 }
        val a = normal.getOrNull(normal.size - 2)
        val b = normal.lastOrNull()
        val before = normal.dropLast(minOf(2, normal.size))
        val top = before.takeLast(3).mapNotNull { it.topLoad }.maxOrNull()
        val base = if (a != null && b != null && a.topLoad == b.topLoad)
            before.filter { it.topLoad == a.topLoad && compare(a, it) != Comparison.NOT_COMPARABLE &&
                compare(b, it) != Comparison.NOT_COMPARABLE }.takeLast(3) else emptyList()
        val k = ceil((base.size + 1) / 2.0).toInt()
        val result = when {
            gap != null -> ProgressStatus.NOT_ENOUGH_DATA to ProgressReason.BackAfterGap(gap)
            normal.size < 3 -> ProgressStatus.NOT_ENOUGH_DATA to ProgressReason.FewSessions(normal.size)
            top != null && a!!.topLoad != null && b!!.topLoad != null && a.topLoad!! < top && b.topLoad!! < top ->
                ProgressStatus.POSSIBLE_DECLINE to ProgressReason.LoadDown(top, b.topLoad!!, b.unit)
            base.size >= 2 && base.count { compare(a!!, it) == Comparison.WORSE } >= k &&
                base.count { compare(b!!, it) == Comparison.WORSE } >= k ->
                ProgressStatus.POSSIBLE_DECLINE to ProgressReason.BelowUsual(b!!.topLoad, b.unit)
            latestBest != null && latestBest.first >= normal.size - 3 &&
                !(normal.size - 1 - latestBest.first >= 2 && ChronoUnit.DAYS.between(normal[latestBest.first].date, today) > 14) ->
                ProgressStatus.IMPROVING to latestBest.second
            else -> ProgressStatus.MAINTAINING to ProgressReason.NoNewBestSince(
                latestBest?.let { normal[it.first].date }, latestBest?.let { normal.size - 1 - it.first } ?: normal.size)
        }
        val streak = if (p == null || b == null) 0 else normal.asReversed().takeWhile {
            it.topLoad == b.topLoad && readyToMoveUp(it, p)
        }.size
        ExerciseProgress(id, all.last().name.ifBlank { id }, all.last().unit, result.first, result.second, streak,
            ChronoUnit.DAYS.between(all.last().date, today), all, bests.map { normal[it.first].sessionId }.toSet())
    }
}

fun attentionOrder(items: List<ExerciseProgress>): List<ExerciseProgress> = items.sortedWith(
    compareBy<ExerciseProgress> {
        when {
            it.daysSinceLast > 14 -> 5
            it.status == ProgressStatus.POSSIBLE_DECLINE -> 0
            it.readyStreak >= 2 -> 1
            it.status == ProgressStatus.IMPROVING -> 2
            it.status == ProgressStatus.MAINTAINING -> 3
            else -> 4
        }
    }.thenByDescending { it.exposures.lastOrNull()?.date }.thenBy { it.name })

sealed interface TodayTarget {
    data class MoveUp(val from: Double?, val to: Double?, val unit: LoadUnit, val repMin: Int?) : TodayTarget
    data class Repeat(val load: Double?, val unit: LoadUnit, val lastReps: List<Double>,
        val rirMin: Int?, val rirMax: Int?) : TodayTarget
}
fun todayTarget(p: Prescription, history: List<PastSession>): TodayTarget? {
    val all = lineage(history, p.exerciseId, p.unit)
    val latest = all.lastOrNull { !it.light } ?: return null
    if (latest.timed || p.repMax == null) return null
    if (readyToMoveUp(latest, p)) {
        val next = latest.topLoad?.let { load -> all.flatMap { it.sets }.mapNotNull { it.load }.filter { it > load }.minOrNull() }
        return TodayTarget.MoveUp(latest.topLoad, next, p.unit, p.repMin)
    }
    return TodayTarget.Repeat(latest.topLoad, p.unit, latest.topSets.mapNotNull { it.reps }, p.rirMin, p.rirMax)
}

enum class SetDelta { UP, SAME, DOWN, NONE }
fun setDelta(logged: PastSet, index: Int, exerciseId: String, history: List<PastSession>): SetDelta {
    val prev = lineage(history, exerciseId, logged.unit).lastOrNull { !it.light }?.sets?.getOrNull(index)
        ?: return SetDelta.NONE
    val reps = logged.reps != null && prev.reps != null
    val timed = logged.reps == null && prev.reps == null && logged.durationS != null && prev.durationS != null
    if (!reps && !timed || reps && (logged.rir == null || prev.rir == null)) return SetDelta.NONE
    if (logged.load != prev.load) return if (logged.load != null && prev.load != null && logged.load > prev.load)
        SetDelta.UP else SetDelta.NONE
    val d = if (reps) logged.reps!! + logged.rir!! - prev.reps!! - prev.rir!!
        else (logged.durationS!! - prev.durationS!!).toDouble()
    val threshold = if (timed) 5 else 1
    return when { d >= threshold -> SetDelta.UP; d <= -threshold -> SetDelta.DOWN; else -> SetDelta.SAME }
}

data class SessionDip(val sessionId: String, val date: LocalDate, val evaluable: Int, val below: List<String>)
fun sessionDips(history: List<PastSession>): List<SessionDip> {
    val sessions = ordered(history)
    val ids = sessions.flatMap { it.exercises }.map { it.exerciseId }.distinct()
    val units = ids.mapNotNull { id -> lineage(history, id).lastOrNull()?.let { id to it.unit } }.toMap()
    val previous = mutableMapOf<String, MutableList<Exposure>>()
    return sessions.filterNot { it.light }.mapNotNull { session ->
        var evaluable = 0
        val below = mutableListOf<String>()
        session.exercises.map { it.exerciseId }.distinct().forEach { id ->
            val e = units[id]?.let { exposure(session, id, it) } ?: return@forEach
            val past = previous.getOrPut(id) { mutableListOf() }
            val prev = past.lastOrNull { compare(e, it) != Comparison.NOT_COMPARABLE }
            if (prev != null) { evaluable++; if (compare(e, prev) == Comparison.WORSE) below.add(id) }
            past.add(e)
        }
        if (below.size >= 3 && 2 * below.size >= evaluable)
            SessionDip(session.id, Instant.ofEpochMilli(session.finishedAtMillis!!).atZone(ZoneOffset.UTC).toLocalDate(), evaluable, below)
        else null
    }
}
