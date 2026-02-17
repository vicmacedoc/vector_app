package com.vm.core.models

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

/**
 * Per-date diary: mood (1–5) and journal text.
 */
@Entity(tableName = "diary_entry")
@Serializable
data class DiaryEntry(
    @PrimaryKey val date: String,  // YYYY-MM-DD
    val mood: Int? = null,        // 1 = red/sad, 5 = green/happy
    val journalText: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)
