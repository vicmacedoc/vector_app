package com.vm.core.models

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface LearningLinkDao {
    @Query("SELECT * FROM learning_link")
    fun getAll(): Flow<List<LearningLink>>

    @Query("SELECT * FROM learning_link")
    suspend fun getAllSync(): List<LearningLink>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(link: LearningLink)

    @Query("DELETE FROM learning_link WHERE fromNodeId = :fromNodeId AND toNodeId = :toNodeId")
    suspend fun delete(fromNodeId: String, toNodeId: String)

    @Query("DELETE FROM learning_link WHERE fromNodeId = :nodeId OR toNodeId = :nodeId")
    suspend fun deleteForNode(nodeId: String)

    @Query("DELETE FROM learning_link")
    suspend fun deleteAll()
}
