package com.vm.core.models

import androidx.room.Entity
import kotlinx.serialization.Serializable

/**
 * Unsaved Calendar edits for one category/day. A row exists only while the in-memory state
 * differs from what is saved (or from the preset when nothing is saved); Save and discard delete it.
 *
 * [category] is one of [CATEGORY_ROUTINE], [CATEGORY_FOOD], [CATEGORY_EXERCISE], [CATEGORY_SELF].
 * [payloadJson] is the category's serialized snapshot.
 */
@Entity(tableName = "calendar_drafts", primaryKeys = ["category", "date"])
@Serializable
data class CalendarDraft(
    val category: String,
    val date: String,
    val payloadJson: String,
    val updatedAt: Long = System.currentTimeMillis()
) {
    companion object {
        const val CATEGORY_ROUTINE = "Routine"
        const val CATEGORY_FOOD = "Food"
        const val CATEGORY_EXERCISE = "Exercise"
        const val CATEGORY_SELF = "Self"
    }
}

/** Key of a draft without its payload, for Home's day squares. */
data class CalendarDraftKey(
    val category: String,
    val date: String
)
