package com.vm.vector.presentation.routine

import com.vm.core.wear.RoutineWearEntry
import java.util.Locale
import kotlin.math.round

internal fun unitToSeconds(value: Double, unit: String): Long {
    return when (unit.lowercase(Locale.US)) {
        "h" -> (value * 3600.0).toLong()
        "min" -> (value * 60.0).toLong()
        else -> value.toLong()
    }
}

internal fun secondsToUnit(seconds: Long, unit: String): Double {
    return when (unit.lowercase(Locale.US)) {
        "h" -> seconds / 3600.0
        "min" -> seconds / 60.0
        else -> seconds.toDouble()
    }
}

/** Matches mobile routine slider: one decimal (e.g. 00:30:00 → 0.5 h). */
internal fun secondsToUnitForSliderPayload(seconds: Long, unit: String): Double {
    val raw = secondsToUnit(seconds, unit)
    return round(raw * 10.0) / 10.0
}

internal fun formatHms(totalSeconds: Long): String {
    val s = totalSeconds.coerceAtLeast(0)
    val h = s / 3600
    val m = (s % 3600) / 60
    val sec = s % 60
    return String.format(Locale.US, "%02d:%02d:%02d", h, m, sec)
}

internal fun entrySecondsBounds(entry: RoutineWearEntry): Pair<Long, Long> {
    val minV = entry.minValue ?: 0.0
    val maxV = entry.maxValue ?: Double.MAX_VALUE
    val minS = unitToSeconds(minV, entry.unit).coerceIn(0L, Long.MAX_VALUE / 8)
    val rawMax = unitToSeconds(maxV, entry.unit)
    val maxS = if (rawMax < minS) Long.MAX_VALUE / 8 else rawMax.coerceIn(minS, Long.MAX_VALUE / 8)
    return minS to maxS
}

internal fun formatRefLine(label: String, value: Double, unit: String): String {
    val u = when (unit.lowercase(Locale.US)) {
        "h" -> "h"
        "min" -> "min"
        else -> "s"
    }
    val num = if (value == value.toLong().toDouble()) {
        value.toLong().toString()
    } else {
        String.format(Locale.US, "%.2f", value).trimEnd('0').trimEnd('.')
    }
    return "$label: $num $u"
}
