package com.vm.core.models

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface SelfCollectionDao {
    @Query("SELECT * FROM self_collection WHERE date = :date ORDER BY createdAt ASC")
    fun getByDate(date: String): Flow<List<SelfCollection>>

    @Query("SELECT * FROM self_collection WHERE date = :date ORDER BY createdAt ASC")
    suspend fun getByDateSync(date: String): List<SelfCollection>

    @Query("SELECT * FROM self_collection WHERE id = :id LIMIT 1")
    suspend fun getByIdSync(id: String): SelfCollection?

    @Query("SELECT * FROM self_collection WHERE driveFolderId = :folderId LIMIT 1")
    suspend fun getByDriveFolderIdSync(folderId: String): SelfCollection?

    @Query("SELECT * FROM self_collection WHERE LOWER(name) = LOWER(:name) LIMIT 1")
    suspend fun getByNameSync(name: String): SelfCollection?

    @Query("SELECT * FROM self_collection")
    suspend fun getAllSync(): List<SelfCollection>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(collection: SelfCollection)

    @Query("DELETE FROM self_collection WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("UPDATE self_collection SET driveFolderId = :folderId WHERE id = :id")
    suspend fun updateDriveFolderId(id: String, folderId: String)

    @Query("UPDATE self_collection SET driveFolderId = NULL WHERE id = :id")
    suspend fun clearDriveFolderId(id: String)

    @Query("UPDATE self_collection SET name = :name WHERE id = :id")
    suspend fun updateName(id: String, name: String)

    @Query("DELETE FROM self_collection")
    suspend fun deleteAll()
}
