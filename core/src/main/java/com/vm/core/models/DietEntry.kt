package com.vm.core.models

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Serializable
enum class AuditStatus { PLANNED, ADJUSTED, UNPLANNED }

@Entity(tableName = "diet_logs")
@Serializable
data class DietEntry(
    @PrimaryKey val id: String,
    val date: String,             // ISO 8601: YYYY-MM-DD
    val plannedTime: String,      // For header grouping (e.g., "08:00")
    val name: String,
    val kcal: Double,
    val protein: Double,          // Added back
    val carbs: Double,            // Added back
    val fats: Double,             // Added back
    val plannedAmount: Double,    // Base weight/count
    val quantityMultiplier: Double = 1.0, 
    val unit: String,
    val isChecked: Boolean = false,
    val status: AuditStatus = AuditStatus.PLANNED,
    val timestamp: Long = System.currentTimeMillis()
)