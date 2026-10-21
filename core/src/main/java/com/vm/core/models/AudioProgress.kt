package com.vm.core.models

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Serializable
data class AudioBookmark(
    val id: String,
    val positionMs: Long,
    val label: String = ""
)

@Entity(tableName = "audio_progress")
@Serializable
data class AudioProgress(
    @PrimaryKey val assetId: String,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val speed: Float = 1f,
    val completed: Boolean = false,
    val bookmarks: List<AudioBookmark> = emptyList(),
    val updatedAt: Long = System.currentTimeMillis()
)
