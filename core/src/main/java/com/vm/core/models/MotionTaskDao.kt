package com.vm.core.models

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface MotionTaskDao {
    @Query("SELECT * FROM motion_task WHERE status = 'OPEN' ORDER BY dueAt ASC, sortOrder ASC")
    fun getOpen(): Flow<List<MotionTask>>

    @Query("SELECT * FROM motion_task WHERE status = 'OPEN' ORDER BY dueAt ASC, sortOrder ASC")
    suspend fun getOpenSync(): List<MotionTask>

    @Query("SELECT * FROM motion_task ORDER BY updatedAt DESC")
    suspend fun getAllSync(): List<MotionTask>

    @Query("SELECT * FROM motion_task WHERE status = :status ORDER BY dueAt ASC, sortOrder ASC")
    fun getByStatus(status: MotionTaskStatus): Flow<List<MotionTask>>

    @Query("SELECT * FROM motion_task WHERE nodeId IS :nodeId AND status = 'OPEN' ORDER BY dueAt ASC, sortOrder ASC")
    fun getOpenByNode(nodeId: String?): Flow<List<MotionTask>>

    /** A list tile's open items plus the ones checked since [doneSince] (so they can be unchecked). */
    @Query(
        """
        SELECT * FROM motion_task
        WHERE nodeId = :nodeId
          AND (status = 'OPEN' OR (status = 'DONE' AND completedAt >= :doneSince))
        ORDER BY dueAt ASC, sortOrder ASC
        """
    )
    fun getForList(nodeId: String, doneSince: Long): Flow<List<MotionTask>>

    /** Quick-captured to-dos waiting in the Inbox. */
    @Query("SELECT * FROM motion_task WHERE nodeId IS NULL AND status = 'OPEN' ORDER BY createdAt ASC")
    fun getInbox(): Flow<List<MotionTask>>

    @Query("SELECT COUNT(*) FROM motion_task WHERE nodeId IS NULL AND status = 'OPEN'")
    fun countInbox(): Flow<Int>

    @Query("UPDATE motion_task SET nodeId = :nodeId, updatedAt = :now WHERE id IN (:ids)")
    suspend fun moveToNode(ids: List<String>, nodeId: String, now: Long)

    @Query("SELECT id FROM motion_task WHERE nodeId = :nodeId")
    suspend fun idsByNode(nodeId: String): List<String>

    @Query("DELETE FROM motion_task WHERE nodeId = :nodeId")
    suspend fun deleteByNodeId(nodeId: String)

    @Query("SELECT * FROM motion_task WHERE id = :id LIMIT 1")
    suspend fun getByIdSync(id: String): MotionTask?

    @Query("SELECT * FROM motion_task WHERE id = :id LIMIT 1")
    fun getById(id: String): Flow<MotionTask?>

    @Query(
        """
        SELECT * FROM motion_task
        WHERE status = 'OPEN'
          AND dueAt IS NOT NULL
          AND dueAt >= :start
          AND dueAt < :end
        ORDER BY dueAt ASC
        """
    )
    suspend fun getOpenDueBetween(start: Long, end: Long): List<MotionTask>

    @Query(
        """
        SELECT * FROM motion_task
        WHERE status = 'OPEN'
          AND dueAt IS NOT NULL
          AND dueAt < :now
        ORDER BY dueAt ASC
        """
    )
    suspend fun getOverdue(now: Long): List<MotionTask>

    @Query(
        """
        SELECT COUNT(*) FROM motion_task
        WHERE status = 'OPEN' AND dueAt IS NOT NULL AND dueAt < :now
        """
    )
    fun countOverdue(now: Long): Flow<Int>

    @Query(
        """
        SELECT COUNT(*) FROM motion_task
        WHERE status = 'OPEN' AND dueAt IS NOT NULL AND dueAt >= :start AND dueAt < :end
        """
    )
    fun countDueBetween(start: Long, end: Long): Flow<Int>

    @Query(
        """
        SELECT * FROM motion_task
        WHERE title LIKE '%' || :query || '%' OR notes LIKE '%' || :query || '%'
        ORDER BY updatedAt DESC
        """
    )
    suspend fun search(query: String): List<MotionTask>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(task: MotionTask)

    @Query("UPDATE motion_task SET sortOrder = :sortOrder WHERE id = :id")
    suspend fun updateSortOrder(id: String, sortOrder: Int)

    @Query("DELETE FROM motion_task WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM motion_task")
    suspend fun deleteAll()
}
