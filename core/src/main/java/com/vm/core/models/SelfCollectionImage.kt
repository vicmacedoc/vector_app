package com.vm.core.models

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

/**
 * Single image in a self collection. Stored in Drive under the album folder.
 */
@Entity(tableName = "self_collection_image", indices = [Index("collectionId")])
@Serializable
data class SelfCollectionImage(
    @PrimaryKey val id: String,
    val collectionId: String,
    val driveFileId: String,
    val fileName: String,
    val takenAtMillis: Long = System.currentTimeMillis(),
    val createdAt: Long = System.currentTimeMillis()
)
