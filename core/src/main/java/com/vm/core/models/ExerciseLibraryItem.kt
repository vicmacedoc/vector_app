package com.vm.core.models

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Entity(
    tableName = "exercise_library",
    indices = [Index(value = ["typeKey", "nameKey"], unique = true, name = "index_exercise_library_type_name")]
)
@Serializable
data class ExerciseLibraryItem(
    @PrimaryKey val id: String,
    val type: String,
    val name: String,
    val description: String = "",
    val payloadJson: String,
    val typeKey: String,
    val nameKey: String,
    val createdAt: Long = System.currentTimeMillis()
)
