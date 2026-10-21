package com.vm.core.models

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Entity(
    tableName = "memory_note",
    indices = [
        Index(value = ["nodeId"]),
        Index(value = ["kind"])
    ]
)
@Serializable
data class MemoryNote(
    @PrimaryKey val id: String,
    /** Null means the note is in the Inbox (unfiled). */
    val nodeId: String? = null,
    val kind: MemoryNoteKind,
    val title: String = "",
    /** Short summary shown on the source card; TEXT notes keep their markdown in [body]. */
    val description: String = "",
    val body: String = "",
    val assetId: String? = null,
    val url: String? = null,
    val tags: List<String> = emptyList(),
    val pinned: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
