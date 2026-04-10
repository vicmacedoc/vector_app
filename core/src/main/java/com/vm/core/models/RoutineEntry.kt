package com.vm.core.models

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Serializable
enum class RoutineType { CATEGORICAL, NUMERICAL }

@Serializable
enum class RoutineStatus { NONE, NOT_DONE, PARTIAL, DONE, EXCEEDED, NA }

@Entity(
    tableName = "routine_logs",
    indices = [Index(value = ["date"], name = "index_routine_logs_date")]
)
@Serializable
data class RoutineEntry(
    @PrimaryKey val id: String,
    val date: String,              // YYYY-MM-DD
    val category: String,          // e.g., "PhD", "Mindset"
    val title: String,
    val type: RoutineType,
    val unit: String? = null,
    // Numerical Logic
    val goalValue: Double? = null,
    val partialThreshold: Double? = null, // The "floor" for Orange status
    val minValue: Double? = null,
    val maxValue: Double? = null,
    val currentValue: Double? = null,    // Null = Not Reported (NONE)
    val directionBetter: Int = 1,        // 1: Higher is better, -1: Lower is better
    // Categorical Logic
    val status: RoutineStatus = RoutineStatus.NONE,
    val isGoalMet: Boolean = false,
    val timestamp: Long = System.currentTimeMillis()
)