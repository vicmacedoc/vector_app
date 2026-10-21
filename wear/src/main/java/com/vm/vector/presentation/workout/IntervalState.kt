package com.vm.vector.presentation.workout

import com.vm.core.models.WorkoutSet
import com.vm.core.models.isInterval
import java.util.Locale
import kotlin.math.roundToInt

/**
 * One ENDURANCE or OTHER exercise run as a sequence of intervals. OTHER exercises (and legacy
 * endurance rows without intervals) are a single interval of their whole duration.
 */
data class IntervalExercise(
    val exerciseId: String,
    val exerciseName: String,
    val description: String,
    val stroke: String?,
    val steps: List<IntervalStep>,
) {
    val rowIds: List<String> get() = steps.map { it.row.id }
}

data class IntervalStep(
    val row: WorkoutSet,
    /** 1-based position inside the exercise. */
    val number: Int,
    /** Countdown length; null runs a stopwatch (distance or open-ended work). */
    val workSeconds: Int?,
    val restSeconds: Int,
)

fun SessionItem.toIntervalExercises(): List<IntervalExercise> =
    sets.groupBy { it.exerciseId }
        .entries
        .sortedBy { (id, _) -> id.substringAfterLast('_').toIntOrNull() ?: 0 }
        .map { (id, rows) ->
            val ordered = rows.sortedBy { it.setNumber }
            val first = ordered.first()
            val singleRow = ordered.size == 1 && !first.isInterval
            IntervalExercise(
                exerciseId = id,
                exerciseName = first.exerciseName,
                description = first.exerciseDescription.ifBlank { first.description },
                stroke = first.stroke,
                steps = ordered.mapIndexed { index, row ->
                    IntervalStep(
                        row = row,
                        number = index + 1,
                        workSeconds = workSecondsFor(row, singleRow),
                        restSeconds = durationValueToSeconds(row.targetRest, row.restUnit),
                    )
                },
            )
        }

private fun workSecondsFor(row: WorkoutSet, singleRow: Boolean): Int? {
    row.targetDuration?.let { return durationValueToSeconds(it, row.unitDuration).takeIf { s -> s > 0 } }
    if (row.targetDistance != null || !singleRow) return null
    return row.totalDuration?.let { minutesToSeconds(it, row.unitTotalDuration) }?.takeIf { it > 0 }
}

private fun minutesToSeconds(value: Double, unit: String): Int = when (unit.lowercase()) {
    "s", "sec", "secs", "second", "seconds" -> value.roundToInt()
    "h", "hr", "hrs", "hour", "hours" -> (value * 3600).roundToInt()
    else -> (value * 60).roundToInt()
}

fun IntervalStep.distanceLabel(): String? = row.targetDistance?.let { "${formatAmount(it)} ${row.unitDistance}" }

/** "100 yd · Z2 · RPE 3 · 90 rpm" — whatever the interval prescribes. */
fun IntervalStep.targetLabel(): String = listOfNotNull(
    distanceLabel(),
    workSeconds?.takeIf { row.targetDistance == null }?.let { formatDurationShort(it) },
    row.targetZone?.let { "Z$it" },
    row.targetRpe?.let { "RPE ${formatHalfStep(it)}" },
    row.targetCadence?.let { "$it ${row.unitCadence}" },
).joinToString(" · ")

fun IntervalExercise.summary(): String {
    if (steps.size == 1) {
        val step = steps.first()
        return listOfNotNull(
            step.targetLabel().ifBlank { null },
            step.restSeconds.takeIf { it > 0 }?.let { "Rest ${formatDurationShort(it)}" },
        ).joinToString(" · ")
    }
    val first = steps.first()
    val uniform = steps.all { it.targetLabel() == first.targetLabel() && it.restSeconds == first.restSeconds }
    return if (uniform) {
        listOfNotNull(
            "${steps.size} × ${first.targetLabel()}",
            first.restSeconds.takeIf { it > 0 }?.let { "on ${formatDurationShort(it)} rest" },
        ).joinToString(" ")
    } else {
        "${steps.size} intervals"
    }
}

fun durationValueToSeconds(value: Int?, unit: String?): Int {
    val amount = value ?: return 0
    return when (unit?.lowercase()) {
        "min", "minutes", "minute" -> amount * 60
        "h", "hr", "hrs", "hour", "hours" -> amount * 3600
        else -> amount
    }
}

fun formatCountdown(totalSeconds: Int): String {
    val safe = totalSeconds.coerceAtLeast(0)
    val h = safe / 3600
    val m = (safe % 3600) / 60
    val s = safe % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

/** "45s", "7 min", "1:30". */
fun formatDurationShort(seconds: Int): String = when {
    seconds < 60 -> "${seconds}s"
    seconds % 60 == 0 -> "${seconds / 60} min"
    else -> formatCountdown(seconds)
}

private fun formatAmount(value: Double): String =
    if (value == value.toLong().toDouble()) value.toLong().toString() else String.format(Locale.US, "%.2f", value).trimEnd('0')
