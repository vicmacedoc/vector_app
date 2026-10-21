package com.vm.vector.data

import com.vm.core.models.MotionPriority
import com.vm.core.models.MotionRecurrence
import com.vm.core.models.MotionScoring
import com.vm.core.models.MotionTask

/** An item being written (quick capture, AI preview, item dialog) before it becomes a [MotionTask]. */
data class MotionItemDraft(
    val key: String = java.util.UUID.randomUUID().toString(),
    val title: String = "",
    val notes: String = "",
    val priority: MotionPriority = MotionPriority.NONE,
    /** Null = follow the priority's base points. */
    val points: Int? = null,
    val dueAt: Long? = null,
    val target: Double? = null,
    val unit: String? = null,
    val recurrence: MotionRecurrence = MotionRecurrence.NONE,
) {
    val effectivePoints: Int get() = points ?: MotionScoring.basePoints(priority)

    companion object {
        fun from(task: MotionTask) = MotionItemDraft(
            key = task.id,
            title = task.title,
            notes = task.notes,
            priority = task.priority,
            points = if (task.pointsEdited) task.points else null,
            dueAt = task.dueAt,
            target = task.target,
            unit = task.unit,
            recurrence = task.recurrence,
        )
    }
}

/** Applies an edited draft to an existing item, keeping its status, progress and history. */
fun MotionTask.withDraft(draft: MotionItemDraft): MotionTask = copy(
    title = draft.title.trim(),
    notes = draft.notes.trim(),
    priority = draft.priority,
    points = draft.effectivePoints,
    pointsEdited = draft.points != null && draft.points != MotionScoring.basePoints(draft.priority),
    dueAt = draft.dueAt,
    target = draft.target?.takeIf { it > 0.0 },
    unit = draft.unit?.trim()?.takeIf { it.isNotEmpty() },
    recurrence = draft.recurrence,
)
