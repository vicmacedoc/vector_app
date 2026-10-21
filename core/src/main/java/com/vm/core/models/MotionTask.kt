package com.vm.core.models

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

/**
 * A to-do item. `nodeId` is its Motion list tile; null = captured to the Inbox, waiting to be merged.
 * `notes` is the item's description.
 */
@Entity(
    tableName = "motion_task",
    indices = [
        Index(value = ["nodeId"]),
        Index(value = ["status"]),
        Index(value = ["dueAt"])
    ]
)
@Serializable
data class MotionTask(
    @PrimaryKey val id: String,
    val nodeId: String? = null,
    val title: String,
    val notes: String = "",
    val priority: MotionPriority = MotionPriority.NONE,
    val dueAt: Long? = null,
    val leadMinutes: Int = 0,
    val recurrence: MotionRecurrence = MotionRecurrence.NONE,
    val status: MotionTaskStatus = MotionTaskStatus.OPEN,
    val completedAt: Long? = null,
    val sortOrder: Int = 0,
    val linkedTileId: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    /** Base points; follows [priority] until the user edits it ([pointsEdited]). */
    val points: Int = MotionScoring.basePoints(priority),
    val pointsEdited: Boolean = false,
    /** First deadline ever set; the early bonus never counts past it. */
    val firstDueAt: Long? = dueAt,
    /** Minimum numeric target for completion; null = a plain checkbox item. */
    val target: Double? = null,
    val progress: Double = 0.0,
    val unit: String? = null,
    /** Points frozen at check time; null while open. */
    val earnedPoints: Int? = null,
    /** Next recurring occurrence created at check time, removed again if this item is unchecked. */
    val spawnedTaskId: String? = null,
)
