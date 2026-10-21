package com.vm.core.models

import java.util.Locale
import kotlin.math.roundToInt

/**
 * Bidirectional conversions for sleep clocks, latency, and stage percentages.
 * Percentages are never stored: denominator is total sleep (fellAsleepAt → actualWakeUp).
 */
object SleepMath {
    const val MINUTES_PER_DAY = 24 * 60

    fun parseHhMm(s: String?): Int? {
        if (s.isNullOrBlank()) return null
        val p = s.trim().split(":")
        if (p.size < 2) return null
        val h = p[0].toIntOrNull() ?: return null
        val m = p[1].toIntOrNull() ?: 0
        return ((h * 60 + m) % MINUTES_PER_DAY + MINUTES_PER_DAY) % MINUTES_PER_DAY
    }

    fun formatHhMm(minutes: Int): String {
        val norm = ((minutes % MINUTES_PER_DAY) + MINUTES_PER_DAY) % MINUTES_PER_DAY
        return String.format(Locale.US, "%02d:%02d", norm / 60, norm % 60)
    }

    fun formatDuration(totalMinutes: Int?): String {
        if (totalMinutes == null) return "--"
        val abs = kotlin.math.abs(totalMinutes)
        val h = abs / 60
        val m = abs % 60
        return "${h}h${m.toString().padStart(2, '0')}m"
    }

    /** Minutes from [start] to [end], wrapping past midnight when end <= start. */
    fun durationMinutes(startHhMm: String?, endHhMm: String?): Int? {
        val start = parseHhMm(startHhMm) ?: return null
        val end = parseHhMm(endHhMm) ?: return null
        var endTotal = end
        if (endTotal <= start) endTotal += MINUTES_PER_DAY
        return endTotal - start
    }

    fun durationHours(startHhMm: String?, endHhMm: String?): Double? =
        durationMinutes(startHhMm, endHhMm)?.let { it / 60.0 }

    fun effectiveBedtime(entry: SleepEntry?): String? =
        entry?.actualBedtime ?: entry?.plannedBedtime

    fun effectiveWakeUp(entry: SleepEntry?): String? =
        entry?.actualWakeUp ?: entry?.plannedWakeUp

    fun latencyMinutes(entry: SleepEntry): Int? {
        val bed = effectiveBedtime(entry) ?: return null
        return durationMinutes(bed, entry.fellAsleepAt)
    }

    fun fellAsleepFromLatency(bedtimeHhMm: String?, latencyMinutes: Int): String? {
        val bed = parseHhMm(bedtimeHhMm) ?: return null
        return formatHhMm(bed + latencyMinutes)
    }

    /** Total sleep minutes: fellAsleepAt → actual/planned wake. */
    fun totalSleepMinutes(entry: SleepEntry): Int? {
        val wake = effectiveWakeUp(entry) ?: return null
        return durationMinutes(entry.fellAsleepAt, wake)
    }

    fun stagePercent(stageMinutes: Int?, totalSleepMinutes: Int?): Int? {
        if (stageMinutes == null || totalSleepMinutes == null || totalSleepMinutes <= 0) return null
        return ((stageMinutes * 100.0) / totalSleepMinutes).roundToInt()
    }

    fun stageMinutesFromPercent(percent: Int, totalSleepMinutes: Int?): Int? {
        if (totalSleepMinutes == null || totalSleepMinutes <= 0) return null
        return ((percent / 100.0) * totalSleepMinutes).roundToInt()
    }

    fun timesMatch(actual: String?, planned: String?): Boolean {
        val a = parseHhMm(actual)
        val p = parseHhMm(planned)
        if (a == null || p == null) return a == p
        return a == p
    }
}
