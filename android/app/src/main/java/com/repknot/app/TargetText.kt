package com.repknot.app

import com.repknot.domain.LoadUnit
import com.repknot.domain.TodayTarget
import java.util.Locale

private fun number(value: Double): String = if (value % 1.0 == 0.0) value.toLong().toString() else value.toString()
private fun load(value: Double, unit: LoadUnit): String = when (unit) {
    LoadUnit.PLATES -> "${number(value)}p"
    LoadUnit.BODYWEIGHT -> "bodyweight"
    else -> "${number(value)} ${unit.name.lowercase(Locale.ROOT)}"
}

fun targetText(target: TodayTarget): String = when (target) {
    is TodayTarget.MoveUp -> when {
        target.to != null -> "Target · ${load(requireNotNull(target.to), target.unit)}${target.repMin?.let { " × $it+" }.orEmpty()} — all sets hit the top last time"
        target.from != null -> "Target · move up from ${load(requireNotNull(target.from), target.unit)} — pick the next load"
        target.unit == LoadUnit.BODYWEIGHT -> "Target · top of the range — add load or a harder variation"
        else -> "Target · top of the range — pick the next load"
    }
    is TodayTarget.Repeat -> {
        val weight = when {
            target.unit == LoadUnit.BODYWEIGHT -> "bodyweight"
            target.load != null -> load(requireNotNull(target.load), target.unit)
            else -> "repeat"
        }
        val rir = when {
            target.rirMin == null && target.rirMax == null -> ""
            target.rirMin == null -> " at RIR ≤${target.rirMax}"
            target.rirMax == null -> " at RIR ${target.rirMin}+"
            target.rirMin == target.rirMax -> " at RIR ${target.rirMin}"
            else -> " at RIR ${target.rirMin}–${target.rirMax}"
        }
        val last = if (target.lastReps.isEmpty()) "" else " (last ${target.lastReps.joinToString(", ", transform = ::number)})"
        "Target · $weight$rir, add a rep where it's there$last"
    }
}
