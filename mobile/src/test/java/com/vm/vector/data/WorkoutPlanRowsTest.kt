package com.vm.vector.data

import com.vm.core.models.WeeklyWorkoutPlan
import com.vm.core.models.WorkoutSet
import com.vm.core.models.WorkoutSetType
import com.vm.core.models.isDropRow
import com.vm.vector.data.analysis.TrainingVolumeMath
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Parses the v2 template fixture (week 40, 2026: Mon 09-28 … Sun 10-04). */
class WorkoutPlanRowsTest {
    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    private val plan: WeeklyWorkoutPlan by lazy {
        val text = requireNotNull(javaClass.classLoader?.getResource("ExW40Y2026.json")) {
            "ExW40Y2026.json fixture missing"
        }.readText()
        json.decodeFromString<WeeklyWorkoutPlan>(text)
    }

    private fun rows(date: String): List<WorkoutSet> = WorkoutPlanRows.planToRows(plan, date)

    @Test
    fun resistance_dropSetBecomesMainPlusDropRows() {
        val curls = rows(MONDAY).filter { it.exerciseName == "Incline Dumbbell Curls" }
        val set3 = curls.filter { it.setNumber == 3 }
        assertEquals(2, set3.size)
        val main = set3.single { !it.isDropRow }
        val drop = set3.single { it.isDropRow }
        assertEquals(WorkoutSetType.DROP, main.setType)
        assertEquals("${MONDAY}_ex_0_2_3_d1", drop.id)
        assertEquals(1, drop.dropNumber)
        assertEquals(15.0, drop.targetLoad!!, 0.0)
        assertEquals(8, drop.targetReps)
        assertEquals(0, drop.targetRir)
        assertNull(drop.targetRest)
        assertEquals(90, main.targetRest)
        assertTrue(curls.filter { it.setNumber < 3 }.all { it.setType == WorkoutSetType.NORMAL })
    }

    @Test
    fun resistance_carriesWeightedTargetsTotalAndSuperset() {
        val monday = rows(MONDAY)
        val pullUps = monday.filter { it.exerciseName == "Weighted Pull-Ups" }
        assertEquals(3, pullUps.size)
        assertTrue(pullUps.all { it.targetMuscles == "back:1.0,biceps:0.5" })
        assertTrue(pullUps.all { it.totalDuration == 17.0 && it.unitTotalDuration == "min" })

        val supersetA = monday.filter { it.groupingId == "A" }.map { it.exerciseName }.distinct()
        assertEquals(listOf("Single-Arm Cable Lateral Raise", "Cable Reverse Fly"), supersetA)
    }

    @Test
    fun endurance_intervalsBecomeIntervalRows() {
        val swim = rows(WEDNESDAY).filter { it.sessionTitle == "Swim - IM Circle" }
        assertTrue(swim.all { it.sport == "SWIM" && it.setType == WorkoutSetType.INTERVAL })
        val warmUp = swim.filter { it.exerciseName == "Warm-up" }.single()
        assertEquals("CHOICE", warmUp.stroke)
        assertEquals(300.0, warmUp.targetDistance!!, 0.0)
        assertEquals("yd", warmUp.unitDistance)
        assertEquals(1, warmUp.targetZone)
        assertEquals(2.0, warmUp.targetRpe!!, 0.0)
        assertEquals(30, warmUp.targetRest)
        assertEquals(6.5, warmUp.totalDuration!!, 0.0)

        val drills = swim.filter { it.exerciseName == "Drill set" }
        assertEquals((1..drills.size).toList(), drills.map { it.setNumber })
        assertEquals("Catch-up", drills.first().description)

        val bike = rows(WEDNESDAY).filter { it.sessionTitle == "Bike - Z2 Cadence" }
        val spinUp = bike.first { it.exerciseName == "Cadence spin-ups" }
        assertEquals("BIKE", spinUp.sport)
        assertEquals(1, spinUp.targetDuration)
        assertEquals("min", spinUp.unitDuration)
        assertEquals(108, spinUp.targetCadence)
        assertEquals(300, spinUp.targetRest)
    }

    @Test
    fun other_isSingleRowWithTotalDurationOnly() {
        val climbing = rows(FRIDAY).filter { it.sessionTitle == "Climbing - MSU" }
        val row = climbing.single()
        assertEquals("OTHER", row.exerciseType)
        assertEquals(60.0, row.totalDuration!!, 0.0)
        assertNull(row.targetDuration)
        assertTrue(row.targetMuscles.isEmpty())
    }

    @Test
    fun clipboardRoundTrip_preservesDropsIntervalsAndSupersets() {
        listOf(MONDAY, WEDNESDAY, FRIDAY).forEach { date ->
            val original = rows(date)
            original.groupBy { it.sessionTitle }.values.forEachIndexed { index, sessionRows ->
                val session = WorkoutPlanRows.rowsToSession(sessionRows)
                val rebuilt = WorkoutPlanRows.sessionToRows(date, session, idPrefix = "ex_$index")
                assertEquals(sessionRows.map { it.comparable() }, rebuilt.map { it.comparable() })
            }
        }
    }

    @Test
    fun importDraftRoundTrip_preservesV2Fields() {
        plan.weekly_plan.monday.plus(plan.weekly_plan.wednesday).forEach { session ->
            val back = session.toExerciseSessionDraft().toWorkoutDaySession()
            assertEquals(session.sport, back.sport)
            session.exercises.zip(back.exercises).forEach { (before, after) ->
                assertEquals(before.target, after.target)
                assertEquals(before.supersetGroup, after.supersetGroup)
                assertEquals(before.totalDuration, after.totalDuration)
                assertEquals(before.stroke, after.stroke)
                assertEquals(before.intervals.size, after.intervals.size)
                assertEquals(before.sets.map { it.drops.size }, after.sets.map { it.drops.size })
            }
        }
    }

    @Test
    fun trainingVolume_usesWeightsAndSkipsDrops() {
        val curls = rows(MONDAY).filter { it.exerciseName == "Incline Dumbbell Curls" }
        val total = curls.sumOf { TrainingVolumeMath.plannedLoadForMuscle(it, "biceps") }
        assertEquals(3.0, total, 0.0)

        val pullUp = rows(MONDAY).first { it.exerciseName == "Weighted Pull-Ups" }
        assertEquals(0.5, TrainingVolumeMath.plannedLoadForMuscle(pullUp, "biceps"), 0.0)
        assertEquals(1.0, TrainingVolumeMath.plannedLoadForMuscle(pullUp, "back"), 0.0)
    }

    /** Fields the clipboard must preserve (ids and session text are regenerated). */
    private fun WorkoutSet.comparable() = listOf(
        exerciseName, setNumber, dropNumber, setType, groupingId, sport, stroke, targetMuscles,
        targetLoad, unitLoad, targetReps, targetRir, targetDuration, targetDistance, unitDistance,
        targetZone, targetRpe, targetCadence, targetRest, restUnit, totalDuration,
    )

    private companion object {
        const val MONDAY = "2026-09-28"
        const val WEDNESDAY = "2026-09-30"
        const val FRIDAY = "2026-10-02"
    }
}
