package com.vm.core.models

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

/** Supermarket cycle the next trip counts for; [open] is false once this week's trip is logged. */
data class SupermarketCycle(val dueDate: LocalDate, val open: Boolean)

/**
 * Motion points. Points are frozen when an item is checked (a [MotionScoreEvent] row), so these
 * rules only run at check time or to preview what an open item is worth right now.
 */
object MotionScoring {
    const val MAX_EARLY_BONUS = 7
    const val AGING_POINTS_PER_WEEK = 5
    const val AGING_CAP_MULTIPLIER = 2
    const val SUPERMARKET_POINTS = 20
    const val SUPERMARKET_LATE_PENALTY = 5
    const val DEFAULT_DAILY_TARGET = 50
    val DEFAULT_SUPERMARKET_DAY: DayOfWeek = DayOfWeek.SUNDAY

    fun basePoints(priority: MotionPriority): Int = when (priority) {
        MotionPriority.NONE -> 5
        MotionPriority.LOW -> 10
        MotionPriority.MEDIUM -> 15
        MotionPriority.HIGH -> 20
    }

    fun pointsAt(task: MotionTask, completedAt: Long, zone: ZoneId = ZoneId.systemDefault()): Int =
        pointsAt(task.points, task.createdAt, task.dueAt, task.firstDueAt, completedAt, zone)

    /**
     * With a deadline: +1 per calendar day to spare (max [MAX_EARLY_BONUS]), measured against the
     * earlier of the first and current deadline so pushing it out never adds bonus; −1 per day past
     * the current deadline, never below half of [base]. Without one: +[AGING_POINTS_PER_WEEK] per
     * full week since creation, capped at [AGING_CAP_MULTIPLIER] × [base].
     */
    fun pointsAt(
        base: Int,
        createdAt: Long,
        dueAt: Long?,
        firstDueAt: Long?,
        completedAt: Long,
        zone: ZoneId = ZoneId.systemDefault(),
    ): Int {
        val doneOn = localDate(completedAt, zone)
        if (dueAt != null) {
            val bonusAnchor = localDate(minOf(dueAt, firstDueAt ?: dueAt), zone)
            val spare = ChronoUnit.DAYS.between(doneOn, bonusAnchor)
            if (spare > 0) return base + minOf(MAX_EARLY_BONUS.toLong(), spare).toInt()
            val late = ChronoUnit.DAYS.between(localDate(dueAt, zone), doneOn)
            if (late > 0) return maxOf(lateFloor(base), base - late.coerceAtMost(Int.MAX_VALUE.toLong()).toInt())
            return base
        }
        val weeks = ChronoUnit.DAYS.between(localDate(createdAt, zone), doneOn).coerceAtLeast(0) / 7
        val bonus = minOf(weeks * AGING_POINTS_PER_WEEK, (AGING_CAP_MULTIPLIER * base).toLong())
        return base + bonus.toInt()
    }

    private fun lateFloor(base: Int): Int = (base + 1) / 2

    /**
     * [lastCycleDue] is the due date the last logged trip counted for. Cycles skipped entirely roll
     * forward, so a trip is at most 6 days late. A trip earlier in the week counts for that week's
     * due day; once it's logged the cycle stays closed until the next week's window opens.
     */
    fun supermarketCycle(lastCycleDue: LocalDate?, today: LocalDate, dueDay: DayOfWeek): SupermarketCycle {
        var due = if (lastCycleDue == null) {
            today.with(TemporalAdjusters.nextOrSame(dueDay))
        } else {
            lastCycleDue.plusDays(1).with(TemporalAdjusters.nextOrSame(dueDay))
        }
        while (ChronoUnit.DAYS.between(due, today) >= 7) due = due.plusDays(7)
        val open = !today.isBefore(due.minusDays(6))
        return SupermarketCycle(due, open)
    }

    fun supermarketPoints(dueDate: LocalDate, tripDate: LocalDate): Int {
        val late = ChronoUnit.DAYS.between(dueDate, tripDate).coerceAtLeast(0)
        return (SUPERMARKET_POINTS - late * SUPERMARKET_LATE_PENALTY).coerceAtLeast(0).toInt()
    }

    /** Consecutive days meeting [target], ending today (today counts only once it's met). */
    fun streak(pointsByDate: Map<LocalDate, Int>, today: LocalDate, target: Int): Int {
        var day = if ((pointsByDate[today] ?: 0) >= target) today else today.minusDays(1)
        var count = 0
        while ((pointsByDate[day] ?: 0) >= target) {
            count++
            day = day.minusDays(1)
        }
        return count
    }

    fun localDate(millis: Long, zone: ZoneId = ZoneId.systemDefault()): LocalDate =
        Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()

    fun dateKey(millis: Long, zone: ZoneId = ZoneId.systemDefault()): String = localDate(millis, zone).toString()
}
