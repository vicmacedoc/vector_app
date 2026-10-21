package com.vm.core.models

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

/**
 * Memory section: one row per memory tile (flat, `parentId` null); its sources are the
 * [MemoryNote]s whose `nodeId` points here. Motion section: task categories.
 */
@Entity(
    tableName = "brain_node",
    indices = [
        Index(value = ["parentId"]),
        Index(value = ["section"])
    ]
)
@Serializable
data class BrainNode(
    @PrimaryKey val id: String,
    val parentId: String? = null,
    val section: BrainSection,
    val name: String,
    val colorArgb: Int = 0xFF001F3F.toInt(),
    val sortOrder: Int = 0,
    val driveFolderId: String? = null,
    val isSystem: Boolean = false,
    /** Tag library names ([BrainTag.name]) applied to a memory tile. */
    val tags: List<String> = emptyList(),
    val createdAt: Long = System.currentTimeMillis()
)
