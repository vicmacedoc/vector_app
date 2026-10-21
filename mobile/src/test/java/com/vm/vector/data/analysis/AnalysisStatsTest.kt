package com.vm.vector.data.analysis

import com.vm.core.models.AuditStatus
import com.vm.core.models.FoodEntry
import com.vm.core.models.HabitStatus
import com.vm.core.models.WorkoutSet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate

class AnalysisStatsTest {

    @Test
    fun longestConsecutiveTrue_countsRuns() {
        assertEquals(3, AnalysisStats.longestConsecutiveTrue(listOf(true, true, true, false, true)))
        assertEquals(2, AnalysisStats.longestConsecutiveTrue(listOf(false, true, true, false)))
        assertEquals(0, AnalysisStats.longestConsecutiveTrue(listOf(false, false)))
    }

    @Test
    fun eachDayInclusive_isChronologicalStrings() {
        val start = LocalDate.of(2026, 4, 1)
        val end = LocalDate.of(2026, 4, 3)
        assertEquals(
            listOf("2026-04-01", "2026-04-02", "2026-04-03"),
            AnalysisStats.eachDayInclusive(start, end)
        )
        assertEquals(emptyList<String>(), AnalysisStats.eachDayInclusive(end, start))
    }

    @Test
    fun weekStartsMondayInclusive_overlapsRange() {
        val mondays = AnalysisStats.weekStartsMondayInclusive(
            LocalDate.of(2026, 4, 8), // Wed
            LocalDate.of(2026, 4, 22)
        )
        assertEquals(LocalDate.of(2026, 4, 6), mondays.first())
        assert(mondays.all { it.dayOfWeek == DayOfWeek.MONDAY })
    }

    @Test
    fun maxAvgMin_emptyNull() {
        assertNull(AnalysisStats.maxAvgMin(emptyList()))
    }

    @Test
    fun maxAvgMin_order() {
        val t = AnalysisStats.maxAvgMin(listOf(1.0, 2.0, 3.0))!!
        assertEquals(3.0, t.high, 0.001)
        assertEquals(2.0, t.avg, 0.001)
        assertEquals(1.0, t.low, 0.001)
    }

    @Test
    fun foodDoneCount_excludesNotEaten() {
        val meals = listOf(
            food(checked = true, notEaten = false),
            food(checked = true, notEaten = true),
            food(checked = false, notEaten = false),
        )
        assertEquals(1, AnalysisStats.foodDoneCount(meals))
    }

    @Test
    fun workoutSetCountsAsCompleted_usesStatusOrFlag() {
        assertFalse(AnalysisStats.workoutSetCountsAsCompleted(wSet(workoutStatus = null)))
        assertFalse(AnalysisStats.workoutSetCountsAsCompleted(wSet(workoutStatus = "Not Done")))
        assertTrue(AnalysisStats.workoutSetCountsAsCompleted(wSet(workoutStatus = "Done")))
        assertTrue(AnalysisStats.workoutSetCountsAsCompleted(wSet(workoutStatus = "Partial")))
        assertTrue(AnalysisStats.workoutSetCountsAsCompleted(wSet(workoutStatus = "Exceeded")))
        assertTrue(AnalysisStats.workoutSetCountsAsCompleted(wSet(workoutStatus = null, isCompleted = true)))
    }

    @Test
    fun workoutCompletedSetCount_matchesStatus() {
        val sets = listOf(
            wSet(workoutStatus = "Done"),
            wSet(workoutStatus = "Not Done"),
            wSet(workoutStatus = "Partial"),
        )
        assertEquals(2, AnalysisStats.workoutCompletedSetCount(sets))
    }

    @Test
    fun fractionalLoad_ignoresNonResistanceEvenWithMuscleTags() {
        val date = "2026-04-01"
        val other = wSet(
            id = "${date}_ex_0_0_1",
            date = date,
            workoutStatus = "Done",
            targetMuscles = "quads,glutes",
        ).copy(exerciseType = "OTHER")
        assertEquals(0.0, TrainingVolumeMath.fractionalLoadForMuscle(other, "quads"), 0.001)

        val endurance = other.copy(exerciseType = "ENDURANCE")
        assertEquals(0.0, TrainingVolumeMath.fractionalLoadForMuscle(endurance, "quads"), 0.001)

        val resistanceNoTags = other.copy(exerciseType = "RESISTANCE", targetMuscles = "")
        assertEquals(0.0, TrainingVolumeMath.fractionalLoadForMuscle(resistanceNoTags, "quads"), 0.001)
    }

    @Test
    fun trailingConsecutiveTrue_countsOnlyTheLiveRun() {
        assertEquals(2, AnalysisStats.trailingConsecutiveTrue(listOf(true, false, true, true)))
        assertEquals(0, AnalysisStats.trailingConsecutiveTrue(listOf(true, true, false)))
        assertEquals(0, AnalysisStats.trailingConsecutiveTrue(emptyList()))
    }

    @Test
    fun rollingAverage_smoothsOverTrailingWindow() {
        val out = AnalysisStats.rollingAverage(listOf(1.0, 2.0, 3.0, 4.0), 2)
        assertEquals(1.0, out[0]!!, 0.001)
        assertEquals(1.5, out[1]!!, 0.001)
        assertEquals(2.5, out[2]!!, 0.001)
        assertEquals(3.5, out[3]!!, 0.001)
    }

    @Test
    fun rollingAverage_skipsGapsWithoutBreakingTheWindow() {
        val out = AnalysisStats.rollingAverage(listOf(2.0, null, 4.0), 3)
        assertEquals(3.0, out[2]!!, 0.001)
    }

    @Test
    fun linearTrend_fitsAStraightLineThroughGaps() {
        val out = AnalysisStats.linearTrend(listOf(10.0, null, 30.0))
        assertEquals(10.0, out[0]!!, 0.001)
        assertEquals(20.0, out[1]!!, 0.001)
        assertEquals(30.0, out[2]!!, 0.001)
    }

    @Test
    fun linearTrend_needsTwoPoints() {
        assertTrue(AnalysisStats.linearTrend(listOf(5.0, null)).all { it == null })
    }

    @Test
    fun halfSplitDelta_comparesSecondHalfToFirst() {
        val delta = AnalysisStats.halfSplitDelta(listOf(10.0, 10.0, 20.0, 20.0))!!
        assertEquals(10.0, delta, 0.001)
    }

    @Test
    fun firstToLastDelta_ignoresMissingEdges() {
        assertEquals(5.0, AnalysisStats.firstToLastDelta(listOf(null, 70.0, null, 75.0))!!, 0.001)
        assertNull(AnalysisStats.firstToLastDelta(listOf(null, 70.0)))
    }

    @Test
    fun pearson_pairsOnlyDaysWithBothValues() {
        val mood = listOf(1.0, 2.0, null, 3.0, 4.0, 5.0)
        val energy = listOf(1.0, 2.0, 5.0, 3.0, 4.0, 5.0)
        assertEquals(1.0, AnalysisStats.pearson(mood, energy)!!, 0.001)
        val inverted = listOf(5.0, 4.0, 1.0, 3.0, 2.0, 1.0)
        assertEquals(-1.0, AnalysisStats.pearson(mood, inverted)!!, 0.001)
    }

    @Test
    fun pearson_needsEnoughPairsAndSomeVariance() {
        val short = List(AnalysisStats.MIN_CORRELATION_PAIRS - 1) { it.toDouble() }
        assertNull(AnalysisStats.pearson(short, short))
        val flat = List(AnalysisStats.MIN_CORRELATION_PAIRS + 1) { 3.0 }
        val varied = List(AnalysisStats.MIN_CORRELATION_PAIRS + 1) { it.toDouble() }
        assertNull(AnalysisStats.pearson(flat, varied))
    }

    @Test
    fun circularMeanMinutes_averagesAcrossMidnight() {
        // 23:00 and 01:00 average to midnight, not to noon.
        val mean = AnalysisStats.circularMeanMinutes(listOf(23 * 60, 1 * 60))!!
        assertTrue("expected near midnight but was $mean", mean <= 1 || mean >= 24 * 60 - 1)
    }

    @Test
    fun circularStdDevMinutes_growsWithScatter() {
        val tight = AnalysisStats.circularStdDevMinutes(listOf(23 * 60, 23 * 60 + 10))!!
        val loose = AnalysisStats.circularStdDevMinutes(listOf(21 * 60, 2 * 60))!!
        assertTrue(tight < loose)
    }

    @Test
    fun sleepDurationHours_handlesOvernight() {
        assertEquals(8.0, AnalysisStats.sleepDurationHours("23:00", "07:00")!!, 0.001)
        assertEquals(1.5, AnalysisStats.sleepDurationHours("13:00", "14:30")!!, 0.001)
        assertNull(AnalysisStats.sleepDurationHours("23:00", null))
    }

    @Test
    fun sleepHoursAttributedToDay_usesPreviousBedtimeAndTodayWake() {
        // Day 1 bedtime 02:00 → day 2 wake 08:00 = 6h (not same-day bed/wake).
        assertEquals(6.0, AnalysisStats.sleepHoursAttributedToDay("02:00", "08:00")!!, 0.001)
        // Day 1 bedtime 23:00 → day 2 wake 08:00 = 9h overnight.
        assertEquals(9.0, AnalysisStats.sleepHoursAttributedToDay("23:00", "08:00")!!, 0.001)
        assertNull(AnalysisStats.sleepHoursAttributedToDay(null, "08:00"))
    }

    @Test
    fun formatMinutesAsClock_padsAndWraps() {
        assertEquals("07:05", AnalysisStats.formatMinutesAsClock(7 * 60 + 5))
        assertEquals("00:00", AnalysisStats.formatMinutesAsClock(24 * 60))
    }

    @Test
    fun habitScore_dropsNotApplicableAndHalvesPartial() {
        assertNull(AnalysisStats.habitScore(HabitStatus.NA))
        assertEquals(0.0, AnalysisStats.habitScore(HabitStatus.NOT_DONE)!!, 0.001)
        assertEquals(0.5, AnalysisStats.habitScore(HabitStatus.PARTIAL)!!, 0.001)
        assertEquals(1.0, AnalysisStats.habitScore(HabitStatus.DONE)!!, 0.001)
        assertEquals(1.0, AnalysisStats.habitScore(HabitStatus.EXCEEDED)!!, 0.001)
    }

    @Test
    fun tonnage_onlyCountsCompletedSetsWithLoadAndReps() {
        val done = wSet(workoutStatus = "Done", actualLoad = 60.0, actualReps = 10)
        assertEquals(600.0, TrainingVolumeMath.tonnage(done), 0.001)
        assertEquals(0.0, TrainingVolumeMath.tonnage(done.copy(workoutStatus = "Not Done")), 0.001)
        assertEquals(0.0, TrainingVolumeMath.tonnage(done.copy(actualReps = null)), 0.001)
    }

    @Test
    fun estimatedOneRepMax_usesEpley() {
        val set = wSet(workoutStatus = "Done", actualLoad = 100.0, actualReps = 3)
        assertEquals(110.0, TrainingVolumeMath.estimatedOneRepMax(set)!!, 0.001)
        assertNull(TrainingVolumeMath.estimatedOneRepMax(set.copy(actualLoad = null)))
        assertNull(TrainingVolumeMath.estimatedOneRepMax(set.copy(workoutStatus = "Not Done")))
    }

    @Test
    fun foodPlanCompliantItemCount_needsEatenAndPlanned() {
        val entries = listOf(
            food(checked = true, notEaten = false, status = AuditStatus.PLANNED),
            food(checked = true, notEaten = false, status = AuditStatus.ADJUSTED),
            food(checked = true, notEaten = false, status = AuditStatus.UNPLANNED),
            food(checked = true, notEaten = true, status = AuditStatus.PLANNED),
        )
        assertEquals(2, AnalysisStats.foodPlanCompliantItemCount(entries))
    }

    @Test
    fun foodDayMacroTotals_gotFromCheckedAndPlannedFromBaseline() {
        val planned = food(
            checked = true,
            notEaten = false,
            status = AuditStatus.PLANNED,
            kcal = 400.0,
            protein = 30.0,
            carbs = 40.0,
            fats = 10.0,
            plannedAmount = 200.0,
            amount = 200.0,
        )
        val uncheckedPlanned = food(
            checked = false,
            notEaten = false,
            status = AuditStatus.PLANNED,
            kcal = 200.0,
            protein = 20.0,
            carbs = 10.0,
            fats = 5.0,
            plannedAmount = 100.0,
            amount = 100.0,
        )
        val unplannedEaten = food(
            checked = true,
            notEaten = false,
            status = AuditStatus.UNPLANNED,
            kcal = 150.0,
            protein = 5.0,
            carbs = 20.0,
            fats = 8.0,
            plannedTime = null,
            plannedAmount = null,
        )
        val notEaten = food(
            checked = true,
            notEaten = true,
            status = AuditStatus.PLANNED,
            kcal = 999.0,
            protein = 99.0,
            carbs = 99.0,
            fats = 99.0,
        )
        val totals = AnalysisStats.foodDayMacroTotals(
            listOf(planned, uncheckedPlanned, unplannedEaten, notEaten)
        )
        assertEquals(550.0, totals.gotKcal, 0.001)
        assertEquals(35.0, totals.gotProtein, 0.001)
        assertEquals(60.0, totals.gotCarbs, 0.001)
        assertEquals(18.0, totals.gotFats, 0.001)
        assertEquals(1599.0, totals.plannedKcal, 0.001)
        assertEquals(149.0, totals.plannedProtein, 0.001)
        assertEquals(149.0, totals.plannedCarbs, 0.001)
        assertEquals(114.0, totals.plannedFats, 0.001)
    }

    @Test
    fun foodDayMacroTotals_newPlannedMealAddsToPlan_sliderDoesNot() {
        val addedAsPlanned = food(
            checked = false,
            status = AuditStatus.PLANNED,
            kcal = 300.0,
            protein = 25.0,
            carbs = 15.0,
            fats = 12.0,
            plannedAmount = 150.0,
            amount = 150.0,
        )
        val adjusted = food(
            checked = true,
            status = AuditStatus.ADJUSTED,
            kcal = 200.0,
            protein = 10.0,
            carbs = 20.0,
            fats = 8.0,
            plannedAmount = 100.0,
            amount = 100.0,
            quantityMultiplier = 2.0,
        )
        val totals = AnalysisStats.foodDayMacroTotals(listOf(addedAsPlanned, adjusted))
        assertEquals(400.0, totals.gotKcal, 0.001)
        assertEquals(500.0, totals.plannedKcal, 0.001)
        assertEquals(20.0, totals.gotProtein, 0.001)
        assertEquals(35.0, totals.plannedProtein, 0.001)
    }

    @Test
    fun plannedNutrients_scaleBackAfterBakedAdjustment() {
        val baked = food(
            checked = false,
            status = AuditStatus.ADJUSTED,
            kcal = 400.0,
            protein = 20.0,
            carbs = 40.0,
            fats = 16.0,
            plannedAmount = 100.0,
            amount = 200.0,
            quantityMultiplier = 1.0,
        )
        assertEquals(200.0, baked.plannedKcal(), 0.001)
        assertEquals(10.0, baked.plannedProtein(), 0.001)
        assertEquals(20.0, baked.plannedCarbs(), 0.001)
        assertEquals(8.0, baked.plannedFats(), 0.001)
    }

    private fun wSet(
        id: String = "2026-01-01_ex_0_0_1",
        date: String = "2026-01-01",
        workoutStatus: String? = null,
        isCompleted: Boolean = false,
        targetMuscles: String = "",
        actualLoad: Double? = null,
        actualReps: Int? = null,
    ) = WorkoutSet(
        id = id,
        exerciseId = "ex_0_0",
        setNumber = 1,
        date = date,
        workoutStatus = workoutStatus,
        isCompleted = isCompleted,
        targetMuscles = targetMuscles,
        actualLoad = actualLoad,
        actualReps = actualReps,
    )

    private fun food(
        checked: Boolean,
        notEaten: Boolean = false,
        status: AuditStatus = AuditStatus.PLANNED,
        kcal: Double = 0.0,
        protein: Double = 0.0,
        carbs: Double = 0.0,
        fats: Double = 0.0,
        plannedTime: String? = "08:00",
        plannedAmount: Double? = 1.0,
        amount: Double? = 1.0,
        quantityMultiplier: Double = 1.0,
    ) = FoodEntry(
        status = status,
        id = "id",
        date = "2026-01-01",
        plannedTime = plannedTime,
        name = "m",
        kcal = kcal,
        protein = protein,
        carbs = carbs,
        fats = fats,
        plannedAmount = plannedAmount,
        time = "08:00",
        amount = amount,
        quantityMultiplier = quantityMultiplier,
        unit = "g",
        isChecked = checked,
        notEaten = notEaten,
    )
}
