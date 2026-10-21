package com.vm.core.models

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface BrainFtsDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(row: BrainFts)

    @Query("DELETE FROM brain_fts WHERE entityType = :entityType AND entityId = :entityId")
    suspend fun delete(entityType: String, entityId: String)

    @Query("DELETE FROM brain_fts")
    suspend fun clear()

    @Query(
        """
        SELECT entityType, entityId, content FROM brain_fts
        WHERE brain_fts MATCH :matchQuery
        LIMIT :limit
        """
    )
    suspend fun search(matchQuery: String, limit: Int = 80): List<BrainFts>

    @Query("SELECT COUNT(*) FROM brain_fts")
    suspend fun count(): Int
}
