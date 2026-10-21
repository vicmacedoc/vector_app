package com.vm.vector.data

import com.vm.core.models.MotionPriority
import com.vm.core.models.MotionScoring
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneOffset

class MotionScoringTest {
    private val zone = ZoneOffset.UTC

    private fun millis(date: String, hour: Int = 12): Long =
        LocalDate.parse(date).atTime(hour, 0).toInstant(zone).toEpochMilli()

    private fun points(base: Int, created: String, due: String?, firstDue: String? = due, done: String): Int =
        MotionScoring.pointsAt(
            base = base,
            createdAt = millis(created),
            dueAt = due?.let { millis(it) },
            firstDueAt = firstDue?.let { millis(it) },
            completedAt = millis(done),
            zone = zone,
        )

    @Test
    fun basePoints_followPriority() {
        assertEquals(5, MotionScoring.basePoints(MotionPriority.NONE))
        assertEquals(10, MotionScoring.basePoints(MotionPriority.LOW))
        assertEquals(15, MotionScoring.basePoints(MotionPriority.MEDIUM))
        assertEquals(20, MotionScoring.basePoints(MotionPriority.HIGH))
    }

    @Test
    fun legacyPriorityNames_map() {
        assertEquals(MotionPriority.HIGH, MotionPriority.parse("P1"))
        assertEquals(MotionPriority.MEDIUM, MotionPriority.parse("P2"))
        assertEquals(MotionPriority.LOW, MotionPriority.parse("P3"))
        assertEquals(MotionPriority.NONE, MotionPriority.parse("P4"))
        assertEquals(MotionPriority.MEDIUM, MotionPriority.parse("medium"))
    }

    @Test
    fun deadline_earlyBonusPerDayCappedAtSeven() {
        assertEquals(15, points(15, "2026-10-01", due = "2026-10-05", done = "2026-10-05"))
        assertEquals(18, points(15, "2026-10-01", due = "2026-10-05", done = "2026-10-02"))
        assertEquals(22, points(15, "2026-09-01", due = "2026-10-30", done = "2026-10-02"))
    }

    @Test
    fun deadline_pushedOutNeverAddsBonus() {
        // First deadline Oct 5, snoozed to Oct 20, done Oct 3: bonus counts against Oct 5.
        assertEquals(12, points(10, "2026-10-01", due = "2026-10-20", firstDue = "2026-10-05", done = "2026-10-03"))
        // Done after the first deadline but before the new one: no bonus, no penalty.
        assertEquals(10, points(10, "2026-10-01", due = "2026-10-20", firstDue = "2026-10-05", done = "2026-10-10"))
    }

    @Test
    fun deadline_latePenaltyFloorsAtHalfBase() {
        assertEquals(18, points(20, "2026-10-01", due = "2026-10-05", done = "2026-10-07"))
        assertEquals(10, points(20, "2026-10-01", due = "2026-10-05", done = "2026-11-30"))
        assertEquals(3, points(5, "2026-10-01", due = "2026-10-05", done = "2026-10-30"))
    }

    @Test
    fun noDeadline_agesFivePerWeekCappedAtTwiceBase() {
        assertEquals(10, points(10, "2026-10-01", due = null, done = "2026-10-07"))
        assertEquals(15, points(10, "2026-10-01", due = null, done = "2026-10-08"))
        assertEquals(30, points(10, "2026-01-01", due = null, done = "2026-10-08"))
    }

    @Test
    fun supermarket_firstCycleIsNextDueDay() {
        val cycle = MotionScoring.supermarketCycle(null, LocalDate.parse("2026-10-07"), DayOfWeek.SUNDAY)
        assertEquals(LocalDate.parse("2026-10-11"), cycle.dueDate)
        assertTrue(cycle.open)
    }

    @Test
    fun supermarket_cycleClosedAfterTripUntilNextWindow() {
        val lastDue = LocalDate.parse("2026-10-11")
        val friday = MotionScoring.supermarketCycle(lastDue, LocalDate.parse("2026-10-09"), DayOfWeek.SUNDAY)
        assertEquals(LocalDate.parse("2026-10-18"), friday.dueDate)
        assertFalse(friday.open)
        val monday = MotionScoring.supermarketCycle(lastDue, LocalDate.parse("2026-10-12"), DayOfWeek.SUNDAY)
        assertTrue(monday.open)
    }

    @Test
    fun supermarket_lateTripsLoseFivePerDayAndSkippedWeeksRoll() {
        val lastDue = LocalDate.parse("2026-10-04")
        val tuesday = LocalDate.parse("2026-10-13")
        val cycle = MotionScoring.supermarketCycle(lastDue, tuesday, DayOfWeek.SUNDAY)
        assertEquals(LocalDate.parse("2026-10-11"), cycle.dueDate)
        assertEquals(10, MotionScoring.supermarketPoints(cycle.dueDate, tuesday))
        assertEquals(0, MotionScoring.supermarketPoints(cycle.dueDate, LocalDate.parse("2026-10-17")))
        assertEquals(20, MotionScoring.supermarketPoints(cycle.dueDate, LocalDate.parse("2026-10-08")))
    }

    @Test
    fun streak_countsConsecutiveDaysMeetingTarget() {
        val today = LocalDate.parse("2026-10-10")
        val byDate = mapOf(
            today to 20,
            today.minusDays(1) to 60,
            today.minusDays(2) to 50,
            today.minusDays(3) to 10,
        )
        assertEquals(2, MotionScoring.streak(byDate, today, 50))
        assertEquals(3, MotionScoring.streak(byDate + (today to 55), today, 50))
    }
}
