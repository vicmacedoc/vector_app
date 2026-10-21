package com.vm.core.models

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonNames

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
    val description: String = "",
    val type: String = "RESISTANCE",
    /** ENDURANCE sport: RUN | BIKE | SWIM. */
    val sport: String? = null,
    val exercises: List<WorkoutExercisePlan> = emptyList()
)

@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class WorkoutExercisePlan(
    val name: String = "",
    val type: String = "RESISTANCE",
    /** Muscle -> volume weight per set, e.g. {"back":1.0,"biceps":0.5}. Legacy lists are accepted. */
    @Serializable(with = MuscleTargetSerializer::class)
    val target: Map<String, Double> = emptyMap(),
    /** Exercises sharing a group letter run as a superset (round-robin). */
    @JsonNames("groupingId") val supersetGroup: String? = null,
    val description: String = "",
    val timing: String? = null,
    /** Planned time for the whole exercise, in [unitDuration]. */
    val totalDuration: Double? = null,
    /** Unit of [totalDuration] (or legacy [targetDuration]); null resolves via [durationUnit]. */
    val unitDuration: String? = null,
    /** Swim stroke: FREE | BACK | BREAST | FLY | IM | CHOICE | DRILL | KICK. */
    val stroke: String? = null,
    val targetDistance: Double? = null,
    val unitDistance: String = "km",
    /** Legacy exercise-level duration (pre-v2 ENDURANCE/OTHER); read as a fallback for [totalDuration]. */
    val targetDuration: Int? = null,
    /** Rest after this exercise (legacy OTHER sequenced drills). */
    val rest: Int? = null,
    @SerialName("unitRest") val restUnit: String = "s",
    val targetRpe: Double? = null,
    val sets: List<WorkoutSetPlan> = emptyList(),
    /** ENDURANCE work blocks. */
    val intervals: List<WorkoutIntervalPlan> = emptyList()
) {
    /** Legacy presets omitted the unit for second-based durations; v2 totals default to minutes. */
    val durationUnit: String
        get() = unitDuration ?: if (totalDuration != null) "min" else "s"

    /** Planned minutes for the exercise: explicit total, then legacy duration, then interval sum. */
    fun plannedTotalMinutes(): Double? {
        totalDuration?.let { return durationToMinutes(it, durationUnit) }
        targetDuration?.let { return durationToMinutes(it.toDouble(), durationUnit) }
        if (intervals.isEmpty()) return null
        val workAndRest = intervals.sumOf { interval ->
            val work = interval.targetDuration?.let { durationToMinutes(it.toDouble(), interval.unitDuration) } ?: 0.0
            val rest = interval.rest?.let { durationToMinutes(it.toDouble(), interval.restUnit) } ?: 0.0
            work + rest
        }
        return workAndRest.takeIf { it > 0.0 }
    }
}

@Serializable
data class WorkoutSetPlan(
    val setNumber: Int = 1,
    val description: String = "",
    val cadence: String? = null,
    /** NORMAL | DROP. */
    val setType: String = WorkoutSetType.NORMAL,
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
    /** Lighter follow-ups done straight after the main set, with no rest. */
    val drops: List<WorkoutDropPlan> = emptyList(),
    val rest: Int? = null,
    @SerialName("unitRest") val restUnit: String = "s"
)

@Serializable
data class WorkoutDropPlan(
    val dropNumber: Int = 1,
    val description: String = "",
    val targetLoad: Double? = null,
    /** Null inherits the parent set's unit. */
    val unitLoad: String? = null,
    val targetReps: Int? = null,
    val targetRir: Int? = null
)

@Serializable
data class WorkoutIntervalPlan(
    val intervalNumber: Int = 1,
    val description: String = "",
    val targetDuration: Int? = null,
    val unitDuration: String = "s",
    val targetDistance: Double? = null,
    val unitDistance: String = "km",
    val targetZone: Int? = null,
    val targetRpe: Double? = null,
    val targetCadence: Int? = null,
    val unitCadence: String = "rpm",
    val rest: Int? = null,
    @SerialName("unitRest") val restUnit: String = "s"
)

fun durationToMinutes(value: Double, unit: String?): Double = when (unit?.lowercase()) {
    "s", "sec", "secs", "second", "seconds" -> value / 60.0
    "h", "hr", "hrs", "hour", "hours" -> value * 60.0
    else -> value
}
