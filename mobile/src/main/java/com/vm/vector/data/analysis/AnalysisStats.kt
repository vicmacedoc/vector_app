package com.vm.vector.data.analysis

import com.vm.core.models.AuditStatus
import com.vm.core.models.FoodEntry
import com.vm.core.models.HabitStatus
import com.vm.core.models.SelfEntry
import com.vm.core.models.WorkoutSet
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import java.util.Locale
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

object AnalysisStats {

    private const val MINUTES_PER_DAY = 24 * 60
    const val MIN_CORRELATION_PAIRS = 5
    private val iso: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE

    fun parseDate(s: String): LocalDate = LocalDate.parse(s, iso)

    fun formatDate(d: LocalDate): String = d.format(iso)

    fun today(): LocalDate = LocalDate.now()

    /** Inclusive list of yyyy-MM-dd from start through end. */
    fun eachDayInclusive(start: LocalDate, end: LocalDate): List<String> {
        if (end.isBefore(start)) return emptyList()
        val out = ArrayList<String>((ChronoUnit.DAYS.between(start, end) + 1).toInt())
        var d = start
        while (!d.isAfter(end)) {
            out.add(formatDate(d))
            d = d.plusDays(1)
        }
        return out
    }

    /** Mondays (week starts Monday) for each week overlapping [rangeStart, rangeEnd]. */
    fun weekStartsMondayInclusive(rangeStart: LocalDate, rangeEnd: LocalDate): List<LocalDate> {
        if (rangeEnd.isBefore(rangeStart)) return emptyList()
        var monday = rangeStart.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val out = mutableListOf<LocalDate>()
        while (!monday.isAfter(rangeEnd)) {
            val sunday = monday.plusDays(6)
            if (!sunday.isBefore(rangeStart)) {
                out.add(monday)
            }
            monday = monday.plusWeeks(1)
        }
        return out
    }

    // ------------------------------------------------------------ sequences

    /** Longest run of consecutive `true` along a dense day list (already chronological). */
    fun longestConsecutiveTrue(flags: List<Boolean>): Int {
        var best = 0
        var cur = 0
        for (f in flags) {
            if (f) {
                cur++
                best = max(best, cur)
            } else {
                cur = 0
            }
        }
        return best
    }

    /** Run of `true` ending at the last element; the streak still alive today. */
    fun trailingConsecutiveTrue(flags: List<Boolean>): Int {
        var cur = 0
        for (f in flags.asReversed()) {
            if (!f) break
            cur++
        }
        return cur
    }

    fun average(values: List<Double>): Double? =
        if (values.isEmpty()) null else values.sum() / values.size

    fun maxAvgMin(values: List<Double>): TripleStat? {
        if (values.isEmpty()) return null
        return TripleStat(
            high = values.max(),
            avg = values.sum() / values.size,
            low = values.min(),
        )
    }

    /**
     * Trailing mean over [window] points, emitted once enough non-null samples exist.
     * Smooths noisy daily series without shifting the line off the date axis.
     */
    fun rollingAverage(values: List<Double?>, window: Int): List<Double?> {
        if (window <= 1) return values
        return values.indices.map { i ->
            val from = max(0, i - window + 1)
            val slice = values.subList(from, i + 1).filterNotNull()
            if (slice.isEmpty()) null else slice.sum() / slice.size
        }
    }

    /**
     * Least-squares fit over the non-null points, evaluated at every index.
     * Returns nulls when fewer than two samples make a line meaningless.
     */
    fun linearTrend(values: List<Double?>): List<Double?> {
        val points = values.mapIndexedNotNull { i, v -> v?.let { i.toDouble() to it } }
        if (points.size < 2) return values.map { null }
        val n = points.size
        val meanX = points.sumOf { it.first } / n
        val meanY = points.sumOf { it.second } / n
        var num = 0.0
        var den = 0.0
        points.forEach { (x, y) ->
            num += (x - meanX) * (y - meanY)
            den += (x - meanX) * (x - meanX)
        }
        if (den == 0.0) return values.map { null }
        val slope = num / den
        val intercept = meanY - slope * meanX
        return values.indices.map { intercept + slope * it }
    }

    /**
     * Pearson correlation across days where both series were logged.
     * Null below [MIN_CORRELATION_PAIRS] pairs or when either side never varies,
     * since a coefficient from three flat days would only mislead.
     */
    fun pearson(x: List<Double?>, y: List<Double?>): Double? {
        val pairs = x.indices.mapNotNull { i ->
            val a = x.getOrNull(i) ?: return@mapNotNull null
            val b = y.getOrNull(i) ?: return@mapNotNull null
            a to b
        }
        if (pairs.size < MIN_CORRELATION_PAIRS) return null
        val n = pairs.size
        val meanX = pairs.sumOf { it.first } / n
        val meanY = pairs.sumOf { it.second } / n
        var cov = 0.0
        var varX = 0.0
        var varY = 0.0
        pairs.forEach { (a, b) ->
            val dx = a - meanX
            val dy = b - meanY
            cov += dx * dy
            varX += dx * dx
            varY += dy * dy
        }
        if (varX <= 1e-9 || varY <= 1e-9) return null
        return (cov / sqrt(varX * varY)).coerceIn(-1.0, 1.0)
    }

    /** Last non-null value minus the first, or null when there is nothing to compare. */
    fun firstToLastDelta(values: List<Double?>): Double? {
        val present = values.filterNotNull()
        if (present.size < 2) return null
        return present.last() - present.first()
    }

    /**
     * Second-half average minus first-half average. Used for "is this improving"
     * chips where a first-to-last delta would be too sensitive to one bad day.
     */
    fun halfSplitDelta(values: List<Double?>): Double? {
        val indexed = values.mapIndexedNotNull { i, v -> v?.let { i to it } }
        if (indexed.size < 2) return null
        val mid = values.size / 2
        val early = indexed.filter { it.first < mid }.map { it.second }
        val late = indexed.filter { it.first >= mid }.map { it.second }
        if (early.isEmpty() || late.isEmpty()) return null
        return (late.sum() / late.size) - (early.sum() / early.size)
    }

    // ------------------------------------------------------------ clock time

    fun parseHhMm(s: String?): Int? {
        if (s.isNullOrBlank()) return null
        val p = s.trim().split(":")
        if (p.size < 2) return null
        val h = p[0].toIntOrNull() ?: return null
        val m = p[1].toIntOrNull() ?: 0
        return ((h * 60 + m) % MINUTES_PER_DAY + MINUTES_PER_DAY) % MINUTES_PER_DAY
    }

    fun formatMinutesAsClock(minutes: Int): String {
        val norm = ((minutes % MINUTES_PER_DAY) + MINUTES_PER_DAY) % MINUTES_PER_DAY
        return String.format(Locale.US, "%02d:%02d", norm / 60, norm % 60)
    }

    /**
     * Sleep duration hours from HH:mm clock times; overnight if wake is at or before bed
     * (e.g. bed 23:00 → wake 07:00). Same-morning spans work too (bed 02:00 → wake 08:00).
     */
    fun sleepDurationHours(bed: String?, wake: String?): Double? {
        val bedM = parseHhMm(bed) ?: return null
        val wakeM = parseHhMm(wake) ?: return null
        var wakeTotal = wakeM
        if (wakeTotal <= bedM) wakeTotal += MINUTES_PER_DAY
        return (wakeTotal - bedM) / 60.0
    }

    /**
     * Sleep duration hours from bedtime to wake. Overnight if wake is at or before bed.
     * Prefer [SleepMath.durationHours] for new sleep_entry rows (one night, one row).
     */
    fun sleepHoursAttributedToDay(previousDayBedtime: String?, dayWakeUp: String?): Double? =
        sleepDurationHours(previousDayBedtime, dayWakeUp)

    /**
     * Mean of times-of-day on the 24h circle, so bedtimes either side of midnight
     * average to midnight rather than to noon.
     */
    fun circularMeanMinutes(values: List<Int>): Int? {
        if (values.isEmpty()) return null
        val (s, c) = circularComponents(values)
        if (abs(s) < 1e-9 && abs(c) < 1e-9) return null
        val angle = atan2(s, c)
        val minutes = (angle / (2 * Math.PI) * MINUTES_PER_DAY).roundToInt()
        return ((minutes % MINUTES_PER_DAY) + MINUTES_PER_DAY) % MINUTES_PER_DAY
    }

    /** Circular standard deviation in minutes: how erratic a daily time is. */
    fun circularStdDevMinutes(values: List<Int>): Double? {
        if (values.size < 2) return null
        val (s, c) = circularComponents(values)
        val r = sqrt(s * s + c * c).coerceIn(1e-9, 1.0)
        val radians = sqrt(-2.0 * ln(r))
        return radians / (2 * Math.PI) * MINUTES_PER_DAY
    }

    private fun circularComponents(values: List<Int>): Pair<Double, Double> {
        val n = values.size
        var s = 0.0
        var c = 0.0
        values.forEach { v ->
            val angle = 2 * Math.PI * v / MINUTES_PER_DAY
            s += sin(angle)
            c += cos(angle)
        }
        return (s / n) to (c / n)
    }

    // ------------------------------------------------------------ domain rules

    fun foodDoneCount(entries: List<FoodEntry>): Int =
        entries.count { it.isChecked && !it.notEaten }

    fun foodWasEaten(entry: FoodEntry): Boolean = entry.isChecked && !entry.notEaten

    /**
     * Calendar Food totals: [FoodDayMacroTotals.got*] from checked (eaten) items,
     * [FoodDayMacroTotals.planned*] from meals that have a planned baseline
     * (preset or an added meal marked Planned).
     */
    fun foodDayMacroTotals(entries: List<FoodEntry>): FoodDayMacroTotals {
        var gotKcal = 0.0
        var gotProtein = 0.0
        var gotCarbs = 0.0
        var gotFats = 0.0
        var plannedKcal = 0.0
        var plannedProtein = 0.0
        var plannedCarbs = 0.0
        var plannedFats = 0.0
        for (entry in entries) {
            if (foodWasEaten(entry)) {
                gotKcal += entry.currentKcal()
                gotProtein += entry.currentProtein()
                gotCarbs += entry.currentCarbs()
                gotFats += entry.currentFats()
            }
            if (entry.hasPresetBaseline()) {
                plannedKcal += entry.plannedKcal()
                plannedProtein += entry.plannedProtein()
                plannedCarbs += entry.plannedCarbs()
                plannedFats += entry.plannedFats()
            }
        }
        return FoodDayMacroTotals(
            gotKcal = gotKcal,
            gotProtein = gotProtein,
            gotCarbs = gotCarbs,
            gotFats = gotFats,
            plannedKcal = plannedKcal,
            plannedProtein = plannedProtein,
            plannedCarbs = plannedCarbs,
            plannedFats = plannedFats,
        )
    }

    /** Items following the planned/adjusted audit trail and marked eaten. */
    fun foodPlanCompliantItemCount(entries: List<FoodEntry>): Int =
        entries.count { e ->
            (e.status == AuditStatus.PLANNED || e.status == AuditStatus.ADJUSTED) &&
                foodWasEaten(e)
        }

    /** Self row counts as "filled" for completion % (mirrors the Home daily inputs circle). */
    fun selfEntryLooksComplete(entry: SelfEntry): Boolean = entry.hasAnyContent()

    /**
     * Calendar marks exercises via [WorkoutSet.workoutStatus] ("Done", "Partial", "Exceeded", …);
     * some paths still set [WorkoutSet.isCompleted].
     */
    fun workoutSetCountsAsCompleted(set: WorkoutSet): Boolean {
        if (set.isCompleted) return true
        return when (set.workoutStatus?.trim()?.lowercase(Locale.US)) {
            "partial", "done", "exceeded", "exceed" -> true
            else -> false
        }
    }

    fun workoutCompletedSetCount(sets: List<WorkoutSet>): Int =
        sets.count { workoutSetCountsAsCompleted(it) }

    /**
     * Credit a habit status earns toward its completion rate.
     * [HabitStatus.NA] returns null so the day drops out of the denominator.
     */
    fun habitScore(status: HabitStatus): Double? = when (status) {
        HabitStatus.NA -> null
        HabitStatus.NONE, HabitStatus.NOT_DONE -> 0.0
        HabitStatus.PARTIAL -> 0.5
        HabitStatus.DONE, HabitStatus.EXCEEDED -> 1.0
    }
}
