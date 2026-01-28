package com.vm.core.models

import kotlinx.serialization.Serializable

@Serializable
data class WeeklyDietPlan(
    val id: String,
    val name: String,
    val weekly_plan: WeeklyPlan
)

@Serializable
data class WeeklyPlan(
    val monday: List<DietPlanEntry> = emptyList(),
    val tuesday: List<DietPlanEntry> = emptyList(),
    val wednesday: List<DietPlanEntry> = emptyList(),
    val thursday: List<DietPlanEntry> = emptyList(),
    val friday: List<DietPlanEntry> = emptyList(),
    val saturday: List<DietPlanEntry> = emptyList(),
    val sunday: List<DietPlanEntry> = emptyList()
)

@Serializable
data class DietPlanEntry(
    val id: String,
    val plannedTime: String,
    val name: String,
    val kcal: Double,
    val protein: Double = 0.0,
    val carbs: Double = 0.0,
    val fats: Double = 0.0,
    val plannedAmount: Double,
    val unit: String
)
