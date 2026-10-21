package com.vm.vector.ui.analysis.charts

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs

private val iso: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE

/** "2026-04-06" -> "Apr 6". Falls back to the raw string if it is not a date. */
fun shortDate(isoDate: String): String = try {
    val d = LocalDate.parse(isoDate, iso)
    "${d.month.name.take(1)}${d.month.name.drop(1).take(2).lowercase(Locale.US)} ${d.dayOfMonth}"
} catch (_: Exception) {
    isoDate
}

/** Axis-friendly number: thousands as "1.2k", integers bare, otherwise one decimal. */
fun formatAxisValue(value: Double): String = when {
    abs(value) >= 10_000 -> String.format(Locale.US, "%.0fk", value / 1000)
    abs(value) >= 1_000 -> String.format(Locale.US, "%.1fk", value / 1000)
    abs(value - value.toInt()) < 1e-6 -> value.toInt().toString()
    abs(value) >= 100 -> String.format(Locale.US, "%.0f", value)
    else -> String.format(Locale.US, "%.1f", value)
}

/** Five evenly spaced tick values from high to low. */
internal fun axisTicks(low: Double, high: Double): List<Double> {
    val span = high - low
    return (0..4).map { high - span * it / 4.0 }
}

/**
 * Padded value bounds for a series. Flat or empty data still yields a usable
 * range so charts never divide by zero.
 */
internal fun valueBounds(values: List<Double?>, includeZero: Boolean): Pair<Double, Double> {
    val present = values.filterNotNull()
    if (present.isEmpty()) return 0.0 to 1.0
    var lo = present.min()
    var hi = present.max()
    if (includeZero) {
        lo = minOf(lo, 0.0)
        hi = maxOf(hi, 0.0)
    }
    if (lo == hi) {
        lo -= 1.0
        hi += 1.0
    }
    val pad = (hi - lo) * 0.08
    return (lo - pad) to (hi + pad)
}
