package com.vm.core.models

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface BrainNodeDao {
    @Query("SELECT * FROM brain_node WHERE section = :section ORDER BY sortOrder ASC, name ASC")
    fun getBySection(section: BrainSection): Flow<List<BrainNode>>

    @Query("SELECT * FROM brain_node WHERE section = :section ORDER BY sortOrder ASC, name ASC")
    suspend fun getBySectionSync(section: BrainSection): List<BrainNode>

    @Query("SELECT * FROM brain_node WHERE parentId IS :parentId AND section = :section ORDER BY sortOrder ASC, name ASC")
    fun getChildren(parentId: String?, section: BrainSection): Flow<List<BrainNode>>

    @Query("SELECT * FROM brain_node WHERE parentId IS :parentId AND section = :section ORDER BY sortOrder ASC, name ASC")
    suspend fun getChildrenSync(parentId: String?, section: BrainSection): List<BrainNode>

    @Query("SELECT * FROM brain_node WHERE id = :id LIMIT 1")
    suspend fun getByIdSync(id: String): BrainNode?

    @Query("SELECT * FROM brain_node WHERE id = :id LIMIT 1")
    fun getById(id: String): Flow<BrainNode?>

    @Query("SELECT * FROM brain_node WHERE driveFolderId = :driveFolderId LIMIT 1")
    suspend fun getByDriveFolderIdSync(driveFolderId: String): BrainNode?

    @Query("SELECT * FROM brain_node ORDER BY sortOrder ASC, name ASC")
    suspend fun getAllSync(): List<BrainNode>

    @Query("SELECT * FROM brain_node WHERE name LIKE '%' || :query || '%' ORDER BY name ASC")
    suspend fun searchByName(query: String): List<BrainNode>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(node: BrainNode)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(nodes: List<BrainNode>)

    @Query("UPDATE brain_node SET sortOrder = :sortOrder WHERE id = :id")
    suspend fun updateSortOrder(id: String, sortOrder: Int)

    @Query("UPDATE brain_node SET driveFolderId = :driveFolderId WHERE id = :id")
    suspend fun updateDriveFolderId(id: String, driveFolderId: String?)

    @Query("DELETE FROM brain_node WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM brain_node")
    suspend fun deleteAll()
}
