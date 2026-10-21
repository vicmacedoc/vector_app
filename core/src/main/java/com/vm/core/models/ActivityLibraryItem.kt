package com.vm.core.models

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

/** One reusable Routine activity (Calendar → Add one → Library). Unique by [nameKey] (lowercased title). */
@Entity(
    tableName = "activity_library",
    indices = [Index(value = ["nameKey"], unique = true, name = "index_activity_library_name")]
)
@Serializable
data class ActivityLibraryItem(
    @PrimaryKey val id: String,
    val title: String,
    val startTime: String,
    val endTime: String,
    val labelName: String = "",
    val type: ActivityType = ActivityType.PLANNED,
    val description: String = "",
    val nameKey: String,
    val createdAt: Long = System.currentTimeMillis()
)
