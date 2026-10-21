package com.vm.core.models

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Entity(
    tableName = "brain_asset",
    indices = [Index(value = ["syncState"])]
)
@Serializable
data class BrainAsset(
    @PrimaryKey val id: String,
    val localRelativePath: String,
    val mimeType: String,
    val sizeBytes: Long = 0L,
    val driveFileId: String? = null,
    val syncState: BrainAssetSyncState = BrainAssetSyncState.PENDING,
    val fileName: String = "",
    /** User-written note on the play screen. Separate from a voice-note transcript. */
    val description: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
