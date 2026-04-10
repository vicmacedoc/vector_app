package com.vm.vector.data.analysis

import com.vm.core.models.DietEntry
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
    fun dietDoneCount_excludesNotEaten() {
        val meals = listOf(
            diet(checked = true, notEaten = false),
            diet(checked = true, notEaten = true),
            diet(checked = false, notEaten = false),
        )
        assertEquals(1, AnalysisStats.dietDoneCount(meals))
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
    fun fractionalLoad_primaryAndSecondary() {
        val date = "2026-04-01"
        val done = wSet(
            id = "${date}_ex_0_0_1",
            date = date,
            workoutStatus = "Done",
            targetMuscles = "chest,triceps",
        )
        assertEquals(1.0, TrainingVolumeMath.fractionalLoadForMuscle(done, "chest"), 0.001)
        assertEquals(0.5, TrainingVolumeMath.fractionalLoadForMuscle(done, "triceps"), 0.001)
        assertEquals(0.0, TrainingVolumeMath.fractionalLoadForMuscle(done, "back"), 0.001)
        val notDone = done.copy(workoutStatus = "Not Done")
        assertEquals(0.0, TrainingVolumeMath.fractionalLoadForMuscle(notDone, "chest"), 0.001)
    }

    private fun wSet(
        id: String = "2026-01-01_ex_0_0_1",
        date: String = "2026-01-01",
        workoutStatus: String? = null,
        isCompleted: Boolean = false,
        targetMuscles: String = "",
    ) = WorkoutSet(
        id = id,
        exerciseId = "ex_0_0",
        setNumber = 1,
        date = date,
        workoutStatus = workoutStatus,
        isCompleted = isCompleted,
        targetMuscles = targetMuscles,
    )

    private fun diet(checked: Boolean, notEaten: Boolean) = DietEntry(
        id = "id",
        date = "2026-01-01",
        plannedTime = "08:00",
        name = "m",
        kcal = 0.0,
        protein = 0.0,
        carbs = 0.0,
        fats = 0.0,
        plannedAmount = 1.0,
        unit = "g",
        isChecked = checked,
        notEaten = notEaten,
    )
}
