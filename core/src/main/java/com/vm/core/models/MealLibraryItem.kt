package com.vm.core.models

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Entity(
    tableName = "meal_library",
    indices = [Index(value = ["nameKey"], unique = true, name = "index_meal_library_name")]
)
@Serializable
data class MealLibraryItem(
    @PrimaryKey val id: String,
    val name: String,
    val kcal: Double = 0.0,
    val protein: Double = 0.0,
    val carbs: Double = 0.0,
    val fats: Double = 0.0,
    val amount: Double = 0.0,
    val unit: String = "g",
    val nameKey: String,
    val createdAt: Long = System.currentTimeMillis()
)
