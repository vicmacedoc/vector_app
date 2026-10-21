package com.vm.core.models

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

/**
 * Snapshot of selected habit IDs for a date. Future days inherit the latest
 * config with date <= selected date.
 */
@Entity(tableName = "habit_day_config")
@Serializable
data class HabitDayConfig(
    @PrimaryKey val date: String,
    val habitIdsJson: String, // JSON array of habit definition IDs
    val updatedAt: Long = System.currentTimeMillis()
)
