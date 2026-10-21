package com.vm.core.models

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface AudioProgressDao {
    @Query("SELECT * FROM audio_progress WHERE assetId = :assetId LIMIT 1")
    suspend fun getByAssetIdSync(assetId: String): AudioProgress?

    @Query("SELECT * FROM audio_progress WHERE assetId = :assetId LIMIT 1")
    fun getByAssetId(assetId: String): Flow<AudioProgress?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(progress: AudioProgress)

    @Query("DELETE FROM audio_progress WHERE assetId = :assetId")
    suspend fun deleteByAssetId(assetId: String)

    @Query("DELETE FROM audio_progress")
    suspend fun deleteAll()
}
