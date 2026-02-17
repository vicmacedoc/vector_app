package com.vm.core.models

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

/**
 * Single image in a diary collection. Stored in Drive under the collection folder.
 */
@Entity(tableName = "diary_collection_image", indices = [Index("collectionId")])
@Serializable
data class DiaryCollectionImage(
    @PrimaryKey val id: String,
    val collectionId: String,
    val driveFileId: String,
    val fileName: String,          // name on Drive (e.g. image_123.jpg)
    val takenAtMillis: Long = System.currentTimeMillis(),
    val createdAt: Long = System.currentTimeMillis()
)
