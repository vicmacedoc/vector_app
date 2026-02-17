package com.vm.core.models

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

/**
 * Single row per date: body metrics, sleep window, and daily plan.
 * Save replaces any existing row for the same date.
 */
@Entity(tableName = "daily_home")
@Serializable
data class DailyHomeEntry(
    @PrimaryKey val date: String,       // YYYY-MM-DD
    val weightKg: Double? = null,
    val bodyFatPercent: Double? = null,
    val sleepStart: String? = null,    // HH:mm, e.g. "22:00"
    val sleepEnd: String? = null,      // HH:mm, e.g. "06:00"
    val dailyPlanText: String? = null,
    val dailyPlanCompletionPercent: Int = 0,
    val timestamp: Long = System.currentTimeMillis()
)
