package com.vm.core.models

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Serializable
enum class MotionScoreSource { TASK, SUPERMARKET }

/**
 * Points ledger. TASK rows use id `task_<taskId>` and are deleted when the item is unchecked.
 * SUPERMARKET rows use `sourceId` = the cycle due date (yyyy-MM-dd) the trip counted for.
 */
@Entity(
    tableName = "motion_score_event",
    indices = [
        Index(value = ["dateKey"]),
        Index(value = ["sourceType"])
    ]
)
@Serializable
data class MotionScoreEvent(
    @PrimaryKey val id: String,
    /** Local day the points were earned, yyyy-MM-dd. */
    val dateKey: String,
    val sourceType: MotionScoreSource,
    val sourceId: String,
    val title: String,
    val points: Int,
    val createdAt: Long = System.currentTimeMillis(),
) {
    companion object {
        fun taskEventId(taskId: String) = "task_$taskId"
    }
}
