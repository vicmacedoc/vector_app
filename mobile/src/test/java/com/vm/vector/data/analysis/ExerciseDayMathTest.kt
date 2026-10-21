package com.vm.vector.data.analysis

import com.vm.core.models.WorkoutSet
import com.vm.core.models.WorkoutSetType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExerciseDayMathTest {

    @Test
    fun resistanceMinutes_useCompletedShareWithoutWatchTime() {
        val rows = (1..3).map { n ->
            row("ex_0_0", n, totalDuration = 9.0, isCompleted = n <= 2, targetMuscles = "back:1.0,traps:0.5")
        }
        val summary = ExerciseDayMath.summarize(rows)
        assertEquals(9.0, summary.minutes.planned, 0.001)
        assertEquals(6.0, summary.minutes.done, 0.001)
        assertEquals(ExerciseBucket.RESISTANCE, summary.byBucket.single().bucket)
    }

    @Test
    fun watchMeasuredTime_winsOverProportionalEstimate() {
        val rows = (1..3).map { n ->
            row("ex_0_0", n, totalDuration = 9.0, isCompleted = n == 1, actualTotalDuration = 600)
        }
        val summary = ExerciseDayMath.summarize(rows)
        assertEquals(10.0, summary.minutes.done, 0.001)
    }

    @Test
    fun statusOnlyExercise_partialCountsHalf() {
        val rows = (1..2).map { n -> row("ex_0_0", n, totalDuration = 10.0, workoutStatus = "Partial") }
        assertEquals(5.0, ExerciseDayMath.summarize(rows).minutes.done, 0.001)
    }

    @Test
    fun weightedVolume_excludesDropRows() {
        val main = row("ex_0_0", 1, targetMuscles = "biceps:1.0", isCompleted = true)
            .copy(setType = WorkoutSetType.DROP)
        val drop = main.copy(id = "${main.id}_d1", dropNumber = 1)
        val second = row("ex_0_0", 2, targetMuscles = "biceps:1.0")
        val summary = ExerciseDayMath.summarize(listOf(main, drop, second))
        val biceps = summary.muscles.single { it.muscle == "biceps" }
        assertEquals(2.0, biceps.sets.planned, 0.001)
        assertEquals(1.0, biceps.sets.done, 0.001)
    }

    @Test
    fun enduranceSport_bucketsBySport_andOtherSeparately() {
        val swim = row("ex_0_0", 1, totalDuration = 6.5, isCompleted = true)
            .copy(exerciseType = "ENDURANCE", sport = "SWIM", setType = WorkoutSetType.INTERVAL)
        val climb = row("ex_1_0", 1, totalDuration = 60.0).copy(exerciseType = "OTHER")
        val summary = ExerciseDayMath.summarize(listOf(swim, climb))
        assertEquals(listOf(ExerciseBucket.SWIM, ExerciseBucket.OTHER), summary.byBucket.map { it.bucket })
        assertEquals(66.5, summary.minutes.planned, 0.001)
        assertEquals(6.5, summary.minutes.done, 0.001)
        assertTrue(summary.muscles.isEmpty())
    }

    @Test
    fun groups_areFixed_andEnduranceSumsSports() {
        val bike = row("ex_0_0", 1, totalDuration = 30.0, isCompleted = true)
            .copy(exerciseType = "ENDURANCE", sport = "BIKE", exerciseName = "Ride")
        val swim = row("ex_1_0", 1, totalDuration = 20.0)
            .copy(exerciseType = "ENDURANCE", sport = "SWIM", exerciseName = "Laps")
        val summary = ExerciseDayMath.summarize(listOf(bike, swim))
        assertEquals(ExerciseGroup.entries.toList(), summary.byGroup.map { it.group })
        val endurance = summary.byGroup.single { it.group == ExerciseGroup.ENDURANCE }.minutes
        assertEquals(50.0, endurance.planned, 0.001)
        assertEquals(30.0, endurance.done, 0.001)
        assertEquals(ExerciseProgress.ZERO, summary.byGroup.single { it.group == ExerciseGroup.RESISTANCE }.minutes)
        assertEquals(listOf("Ride", "Laps"), summary.activities.map { it.name })
        assertEquals(ExerciseProgress(30.0, 30.0), summary.activities.first().minutes)
    }

    @Test
    fun addedExercise_countsDoneMinutes_butNotTargets() {
        val planned = row("ex_0_0", 1, totalDuration = 30.0, isCompleted = true)
            .copy(exerciseType = "ENDURANCE", sport = "RUN")
        val added = row("ex_added_0_0", 1, totalDuration = 20.0, isCompleted = true)
            .copy(exerciseType = "ENDURANCE", sport = "BIKE", exerciseName = "Extra ride")
        val replanned = row("ex_plan_0_0", 1, totalDuration = 15.0).copy(exerciseType = "OTHER")
        val summary = ExerciseDayMath.summarize(listOf(planned, added, replanned))

        assertEquals(ExerciseProgress(50.0, 45.0), summary.minutes)
        val endurance = summary.byGroup.single { it.group == ExerciseGroup.ENDURANCE }.minutes
        assertEquals(ExerciseProgress(50.0, 30.0), endurance)
        assertEquals(ExerciseProgress(20.0, 0.0), summary.byBucket.single { it.bucket == ExerciseBucket.BIKE }.minutes)
        assertEquals(ExerciseProgress(0.0, 15.0), summary.byGroup.single { it.group == ExerciseGroup.OTHER }.minutes)
        assertEquals(ExerciseProgress(20.0, 20.0), summary.activities.single { it.name == "Extra ride" }.minutes)
    }

    @Test
    fun emptyDay_stillHasThreeZeroGroups() {
        val summary = ExerciseDayMath.summarize(emptyList())
        assertEquals(3, summary.byGroup.size)
        assertTrue(summary.byGroup.all { it.minutes == ExerciseProgress.ZERO })
        assertTrue(summary.activities.isEmpty())
    }

    @Test
    fun legacyRowsWithoutTotal_fallBackToDurationsAndRests() {
        val interval = row("ex_0_0", 1).copy(
            exerciseType = "ENDURANCE",
            targetDuration = 60,
            unitDuration = "s",
            targetRest = 30,
            restUnit = "s",
        )
        assertEquals(1.5, ExerciseDayMath.plannedMinutes(listOf(interval)), 0.001)
    }

    private fun row(
        exerciseId: String,
        setNumber: Int,
        totalDuration: Double? = null,
        isCompleted: Boolean = false,
        workoutStatus: String? = null,
        targetMuscles: String = "",
        actualTotalDuration: Int? = null,
    ) = WorkoutSet(
        id = "2026-10-05_${exerciseId}_$setNumber",
        exerciseId = exerciseId,
        setNumber = setNumber,
        date = "2026-10-05",
        totalDuration = totalDuration,
        isCompleted = isCompleted,
        workoutStatus = workoutStatus,
        targetMuscles = targetMuscles,
        actualTotalDuration = actualTotalDuration,
    )
}
