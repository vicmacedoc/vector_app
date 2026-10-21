package com.vm.core.models

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface LearningTileDao {
    @Query("SELECT * FROM learning_tile")
    fun getAll(): Flow<List<LearningTile>>

    @Query("SELECT * FROM learning_tile")
    suspend fun getAllSync(): List<LearningTile>

    @Query("SELECT * FROM learning_tile WHERE id = :id LIMIT 1")
    suspend fun getByIdSync(id: String): LearningTile?

    @Query("SELECT * FROM learning_tile WHERE id = :id LIMIT 1")
    fun getById(id: String): Flow<LearningTile?>

    @Query("SELECT COUNT(*) FROM learning_tile WHERE status IN (:statuses)")
    fun countByStatuses(statuses: List<LearningTileStatus>): Flow<Int>

    @Query(
        """
        SELECT * FROM learning_tile
        WHERE status = 'LEARNED' AND nextReviewAt IS NOT NULL AND nextReviewAt <= :now
        """
    )
    suspend fun getDueForReview(now: Long): List<LearningTile>

    @Query("SELECT * FROM learning_tile WHERE status = 'IN_PROGRESS' AND lastActivityAt < :cutoff")
    suspend fun getStaleInProgress(cutoff: Long): List<LearningTile>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(tile: LearningTile)

    @Query("DELETE FROM learning_tile WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM learning_tile")
    suspend fun deleteAll()
}
