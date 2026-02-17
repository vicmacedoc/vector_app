package com.vm.core.models

import kotlinx.serialization.Serializable

@Serializable
data class WeeklyRoutinePlan(
    val id: String = "",
    val weekly_plan: WeeklyRoutinePlanDays
)

@Serializable
data class WeeklyRoutinePlanDays(
    val monday: List<RoutinePlanEntry> = emptyList(),
    val tuesday: List<RoutinePlanEntry> = emptyList(),
    val wednesday: List<RoutinePlanEntry> = emptyList(),
    val thursday: List<RoutinePlanEntry> = emptyList(),
    val friday: List<RoutinePlanEntry> = emptyList(),
    val saturday: List<RoutinePlanEntry> = emptyList(),
    val sunday: List<RoutinePlanEntry> = emptyList()
)

@Serializable
data class RoutinePlanEntry(
    val id: String = "",
    val category: String,
    val title: String,
    val type: RoutineType,
    val unit: String? = null,
    val goalValue: Double? = null,
    val partialThreshold: Double? = null,  // The "floor" for Orange status
    val minValue: Double? = null,
    val maxValue: Double? = null,
    val directionBetter: Int = 1  // 1: Higher is better, -1: Lower is better
)
