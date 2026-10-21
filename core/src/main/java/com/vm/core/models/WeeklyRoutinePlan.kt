package com.vm.core.models

import kotlinx.serialization.Serializable

/**
 * Weekly Routine preset: timed activities per weekday.
 *
 * Accepted on disk either wrapped (`{ "name", "weekly_plan": { "monday": [...] } }`, same shape as
 * the Food/Workout presets) or bare (`{ "monday": [...], ... }`).
 */
@Serializable
data class WeeklyRoutinePlan(
    val name: String = "",
    val weekly_plan: WeeklyRoutinePlanDays = WeeklyRoutinePlanDays()
)

@Serializable
data class WeeklyRoutinePlanDays(
    val monday: List<RoutinePlanActivity> = emptyList(),
    val tuesday: List<RoutinePlanActivity> = emptyList(),
    val wednesday: List<RoutinePlanActivity> = emptyList(),
    val thursday: List<RoutinePlanActivity> = emptyList(),
    val friday: List<RoutinePlanActivity> = emptyList(),
    val saturday: List<RoutinePlanActivity> = emptyList(),
    val sunday: List<RoutinePlanActivity> = emptyList()
) {
    fun all(): List<RoutinePlanActivity> =
        monday + tuesday + wednesday + thursday + friday + saturday + sunday
}

@Serializable
data class RoutinePlanActivity(
    val title: String,
    val startTime: String,
    val endTime: String,
    /** Activity label name, matched case-insensitively against `activity_labels`. */
    val label: String = "",
    /** "PLANNED" or "NEW"; presets default to PLANNED. */
    val type: String = "PLANNED",
    val description: String = ""
)
