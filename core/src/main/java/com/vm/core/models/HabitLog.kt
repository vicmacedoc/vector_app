package com.vm.core.models

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Entity(
    tableName = "habit_logs",
    indices = [
        Index(value = ["date"], name = "index_habit_logs_date"),
        Index(value = ["date", "habitId"], name = "index_habit_logs_date_habit", unique = true)
    ]
)
@Serializable
data class HabitLog(
    @PrimaryKey val id: String,
    val date: String,
    val habitId: String,
    val status: HabitStatus = HabitStatus.NONE,
    val timestamp: Long = System.currentTimeMillis()
)
