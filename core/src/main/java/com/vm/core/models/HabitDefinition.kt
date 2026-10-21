package com.vm.core.models

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Serializable
enum class HabitStatus { NONE, NOT_DONE, PARTIAL, DONE, EXCEEDED, NA }

@Entity(tableName = "habit_definitions")
@Serializable
data class HabitDefinition(
    @PrimaryKey val id: String,
    val name: String,
    val createdAt: Long = System.currentTimeMillis()
)
