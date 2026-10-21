package com.vm.core.models

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

/**
 * A named photo album. Images live under Drive `collections/{name}/`.
 * [isSystem] marks body-scan angle albums (front/left/back/right) — non-deletable in UI.
 */
@Entity(tableName = "self_collection", indices = [Index("date")])
@Serializable
data class SelfCollection(
    @PrimaryKey val id: String,
    val date: String,              // YYYY-MM-DD (legacy; albums are global)
    val name: String,
    val driveFolderId: String? = null,
    val isSystem: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
) {
    companion object {
        val BODY_SCAN_ANGLES = listOf("front", "left", "back", "right")

        fun isBodyScanAngleName(name: String): Boolean =
            BODY_SCAN_ANGLES.any { it.equals(name.trim(), ignoreCase = true) }
    }
}
