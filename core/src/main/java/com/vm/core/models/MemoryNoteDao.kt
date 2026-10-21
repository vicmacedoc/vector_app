package com.vm.core.models

import androidx.room.*
import kotlinx.coroutines.flow.Flow

/** Number of sources of one [kind] inside the memory tile [nodeId]. */
data class MemoryNoteKindCount(
    val nodeId: String,
    val kind: MemoryNoteKind,
    val count: Int,
)

@Dao
interface MemoryNoteDao {
    @Query("SELECT * FROM memory_note WHERE nodeId IS :nodeId ORDER BY pinned DESC, updatedAt DESC")
    fun getByNodeId(nodeId: String?): Flow<List<MemoryNote>>

    @Query("SELECT * FROM memory_note WHERE nodeId IS :nodeId ORDER BY pinned DESC, updatedAt DESC")
    suspend fun getByNodeIdSync(nodeId: String?): List<MemoryNote>

    @Query("SELECT * FROM memory_note WHERE nodeId IS NULL ORDER BY pinned DESC, updatedAt DESC")
    fun getInbox(): Flow<List<MemoryNote>>

    @Query("SELECT * FROM memory_note WHERE nodeId IS NULL ORDER BY pinned DESC, updatedAt DESC")
    suspend fun getInboxSync(): List<MemoryNote>

    @Query("SELECT * FROM memory_note ORDER BY updatedAt DESC")
    suspend fun getAllSync(): List<MemoryNote>

    @Query("SELECT COUNT(*) FROM memory_note WHERE nodeId IS NULL")
    fun getInboxCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM memory_note WHERE nodeId IS NULL")
    suspend fun getInboxCountSync(): Int

    @Query(
        """
        SELECT nodeId, kind, COUNT(*) AS count FROM memory_note
        WHERE nodeId IS NOT NULL
        GROUP BY nodeId, kind
        """
    )
    fun getKindCountsByNode(): Flow<List<MemoryNoteKindCount>>

    @Query("SELECT * FROM memory_note WHERE assetId = :assetId ORDER BY updatedAt DESC")
    suspend fun getByAssetIdSync(assetId: String): List<MemoryNote>

    @Query("SELECT * FROM memory_note WHERE id = :id LIMIT 1")
    suspend fun getByIdSync(id: String): MemoryNote?

    @Query("SELECT * FROM memory_note WHERE id = :id LIMIT 1")
    fun getById(id: String): Flow<MemoryNote?>

    @Query(
        """
        SELECT * FROM memory_note
        WHERE title LIKE '%' || :query || '%'
           OR body LIKE '%' || :query || '%'
           OR tags LIKE '%' || :query || '%'
        ORDER BY updatedAt DESC
        """
    )
    suspend fun search(query: String): List<MemoryNote>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(note: MemoryNote)

    @Query("UPDATE memory_note SET nodeId = :nodeId, updatedAt = :updatedAt WHERE id = :id")
    suspend fun moveToNode(id: String, nodeId: String?, updatedAt: Long)

    @Query("DELETE FROM memory_note WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM memory_note WHERE nodeId = :nodeId")
    suspend fun deleteByNodeId(nodeId: String)

    @Query("DELETE FROM memory_note")
    suspend fun deleteAll()
}
