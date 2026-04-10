package com.vm.core.models

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

/**
 * A named photo album for a calendar day. Images are stored in a single shared Drive
 * `collections` folder; filenames carry datetime + collection id.
 * [driveFolderId] is legacy (per-folder uploads); new rows leave it null.
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
