package com.vm.core.models

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface BrainAssetDao {
    @Query("SELECT * FROM brain_asset WHERE id = :id LIMIT 1")
    suspend fun getByIdSync(id: String): BrainAsset?

    @Query("SELECT * FROM brain_asset WHERE id = :id LIMIT 1")
    fun getById(id: String): Flow<BrainAsset?>

    @Query("SELECT * FROM brain_asset WHERE driveFileId = :driveFileId LIMIT 1")
    suspend fun getByDriveFileIdSync(driveFileId: String): BrainAsset?

    @Query("SELECT * FROM brain_asset ORDER BY createdAt ASC")
    suspend fun getAllSync(): List<BrainAsset>

    @Query("SELECT * FROM brain_asset WHERE syncState IN (:states) ORDER BY createdAt ASC")
    suspend fun getBySyncStates(states: List<BrainAssetSyncState>): List<BrainAsset>

    @Query("SELECT * FROM brain_asset WHERE syncState = :state ORDER BY createdAt ASC")
    suspend fun getBySyncState(state: BrainAssetSyncState): List<BrainAsset>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(asset: BrainAsset)

    @Query(
        """
        UPDATE brain_asset
        SET driveFileId = :driveFileId, syncState = :syncState, updatedAt = :updatedAt
        WHERE id = :id
        """
    )
    suspend fun updateSync(id: String, driveFileId: String?, syncState: BrainAssetSyncState, updatedAt: Long)

    @Query("UPDATE brain_asset SET syncState = :syncState, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateSyncState(id: String, syncState: BrainAssetSyncState, updatedAt: Long)

    @Query("DELETE FROM brain_asset WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM brain_asset")
    suspend fun deleteAll()
}
