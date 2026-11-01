package com.repknot.app

import com.repknot.domain.*
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val dateFormat = DateTimeFormatter.ofPattern("MMM d", Locale.ENGLISH)
private fun date(value: LocalDate) = value.format(dateFormat)
private fun number(value: Double) = if (value % 1.0 == 0.0) value.toLong().toString() else value.toString()
private fun load(value: Double?, unit: LoadUnit): String? = when {
    value == null || unit == LoadUnit.BODYWEIGHT -> null
    unit == LoadUnit.PLATES -> "${number(value)}p"
    else -> "${number(value)} ${unit.name.lowercase(Locale.ROOT)}"
}

fun groupLabel(p: ExerciseProgress): String = when {
    p.daysSinceLast > 14 -> "Not trained recently"
    p.status == ProgressStatus.POSSIBLE_DECLINE -> "Needs a look"
    p.readyStreak >= 2 -> "Ready to move up"
    p.status == ProgressStatus.IMPROVING -> "Improving"
    p.status == ProgressStatus.MAINTAINING -> "Holding steady"
    else -> "Not enough data yet"
}

fun reasonText(r: ProgressReason): String = when (r) {
    is ProgressReason.BackAfterGap -> "Back after ${r.days} days — judged again after 2 sessions"
    is ProgressReason.FewSessions -> "${r.count} ${if (r.count == 1) "session" else "sessions"} so far — needs 3"
    is ProgressReason.MovedUp -> "Moved up${load(r.load, r.unit)?.let { " to $it" }.orEmpty()} on ${date(r.on)}"
    is ProgressReason.MoreReps -> "More reps${load(r.load, r.unit)?.let { " at $it" }.orEmpty()} on ${date(r.on)}"
    is ProgressReason.BelowUsual -> "Below your usual${load(r.load, r.unit)?.let { " at $it" }.orEmpty()} for 2 sessions"
    is ProgressReason.LoadDown -> {
        val from = load(r.from, r.unit)
        val to = load(r.to, r.unit)
        if (from != null && to != null) "Load down from $from to $to for 2 sessions"
        else "Load down for 2 sessions"
    }
    is ProgressReason.NoNewBestSince -> r.lastBest?.let {
        "Holding steady — last best ${date(it)}, ${r.sessionsSince} ${if (r.sessionsSince == 1) "session" else "sessions"} since"
    } ?: "Holding steady — no best yet"
}

fun flagText(p: ExerciseProgress): List<String> = buildList {
    if (p.readyStreak >= 2) add("Top of the range ${p.readyStreak} sessions in a row — move up, or change tempo or pause if the stack is maxed")
    if (p.daysSinceLast > 14) add("Not trained for ${p.daysSinceLast} days")
}

fun exposureLine(e: Exposure): String = "${date(e.date)} · ${previousSummary(e.sets)}" + if (e.light) " · light" else ""

fun dipText(d: SessionDip, names: Map<String, String>): String =
    "Broad dip on ${date(d.date)} — ${d.below.size} of ${d.evaluable} exercises below usual (${d.below.joinToString(", ") { names[it] ?: it }}). Check sleep, food and recovery."
