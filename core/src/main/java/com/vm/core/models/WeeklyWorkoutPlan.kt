package com.vm.core.models

import kotlinx.serialization.Serializable

/** JSON preset: presets/workout_weekly.json */
@Serializable
data class WeeklyWorkoutPlan(
    val id: String = "",
    val name: String = "",
    val weekly_plan: WeeklyWorkoutPlanDays = WeeklyWorkoutPlanDays()
)

@Serializable
data class WeeklyWorkoutPlanDays(
    val monday: List<WorkoutDaySession> = emptyList(),
    val tuesday: List<WorkoutDaySession> = emptyList(),
    val wednesday: List<WorkoutDaySession> = emptyList(),
    val thursday: List<WorkoutDaySession> = emptyList(),
    val friday: List<WorkoutDaySession> = emptyList(),
    val saturday: List<WorkoutDaySession> = emptyList(),
    val sunday: List<WorkoutDaySession> = emptyList()
)

@Serializable
data class WorkoutDaySession(
    val title: String = "",
    val exercises: List<WorkoutExercisePlan> = emptyList()
)

@Serializable
data class WorkoutExercisePlan(
    val name: String = "",
    val type: String = "RESISTANCE",
    val groupingId: String? = null,
    val description: String = "",
    val targetDistance: Double? = null,
    val unitDistance: String = "km",
    val targetDuration: Int? = null,
    val unitDuration: String = "s",
    val targetRpe: Double? = null,
    val sets: List<WorkoutSetPlan> = emptyList()
)

@Serializable
data class WorkoutSetPlan(
    val setNumber: Int = 1,
    val description: String = "",
    val cadence: String? = null,
    val targetLoad: Double? = null,
    val unitLoad: String = "kg",
    val targetReps: Int? = null,
    val targetVelocity: Double? = null,
    val unitVelocity: String = "m/s",
    val targetDuration: Int? = null,
    val unitDuration: String = "s",
    val targetDistance: Double? = null,
    val unitDistance: String = "km",
    val targetRir: Int? = null,
    val rest: Int? = null,
    val restUnit: String = "s"
)
