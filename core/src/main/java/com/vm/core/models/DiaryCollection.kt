package com.vm.core.models

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

/**
 * A named photo collection for a given date. Drive folder: collections/{date}_{name}/.
 */
@Entity(tableName = "diary_collection", indices = [Index("date")])
@Serializable
data class DiaryCollection(
    @PrimaryKey val id: String,
    val date: String,              // YYYY-MM-DD
    val name: String,
    val driveFolderId: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)
