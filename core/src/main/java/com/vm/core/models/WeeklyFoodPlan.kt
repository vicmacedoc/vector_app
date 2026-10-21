package com.vm.core.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class WeeklyFoodPlan(
    val id: String = "",
    val name: String,
    val weekly_plan: WeeklyFoodPlanDays
)

@Serializable
data class WeeklyFoodPlanDays(
    val monday: List<FoodPlanEntry> = emptyList(),
    val tuesday: List<FoodPlanEntry> = emptyList(),
    val wednesday: List<FoodPlanEntry> = emptyList(),
    val thursday: List<FoodPlanEntry> = emptyList(),
    val friday: List<FoodPlanEntry> = emptyList(),
    val saturday: List<FoodPlanEntry> = emptyList(),
    val sunday: List<FoodPlanEntry> = emptyList()
)

@Serializable
data class FoodPlanEntry(
    val id: String = "",
    @SerialName("time") val plannedTime: String,
    val name: String,
    val kcal: Double,
    val protein: Double = 0.0,
    val carbs: Double = 0.0,
    val fats: Double = 0.0,
    @SerialName("amount") val plannedAmount: Double,
    val unit: String
)
