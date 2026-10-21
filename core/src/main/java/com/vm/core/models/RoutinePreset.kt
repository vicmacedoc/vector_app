package com.vm.core.models

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Serializable
data class RoutinePresetActivity(
    val title: String = "",
    val startTime: String = "09:00",
    val endTime: String = "10:00",
    val labelName: String = "",
    val labelColorArgb: Int = 0xFF1565C0.toInt(),
    val type: ActivityType = ActivityType.PLANNED,
    val description: String = ""
)

/**
 * Legacy named bundle of activities. The Routine library now holds single [ActivityLibraryItem]s;
 * remaining bundles are flattened into it on first Calendar load and then deleted.
 */
@Entity(
    tableName = "routine_presets",
    indices = [Index(value = ["nameKey"], unique = true, name = "index_routine_presets_name")]
)
@Serializable
data class RoutinePreset(
    @PrimaryKey val id: String,
    val name: String,
    val payloadJson: String,
    val nameKey: String,
    val createdAt: Long = System.currentTimeMillis()
)
