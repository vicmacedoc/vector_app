package com.vm.core.models

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface DiaryCollectionDao {
    @Query("SELECT * FROM diary_collection WHERE date = :date ORDER BY createdAt ASC")
    fun getByDate(date: String): Flow<List<DiaryCollection>>

    @Query("SELECT * FROM diary_collection WHERE date = :date ORDER BY createdAt ASC")
    suspend fun getByDateSync(date: String): List<DiaryCollection>

    @Query("SELECT * FROM diary_collection WHERE id = :id LIMIT 1")
    suspend fun getByIdSync(id: String): DiaryCollection?

    @Query("SELECT * FROM diary_collection WHERE driveFolderId = :folderId LIMIT 1")
    suspend fun getByDriveFolderIdSync(folderId: String): DiaryCollection?

    @Query("SELECT * FROM diary_collection")
    suspend fun getAllSync(): List<DiaryCollection>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(collection: DiaryCollection)

    @Query("DELETE FROM diary_collection WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("UPDATE diary_collection SET driveFolderId = :folderId WHERE id = :id")
    suspend fun updateDriveFolderId(id: String, folderId: String)

    @Query("UPDATE diary_collection SET driveFolderId = NULL WHERE id = :id")
    suspend fun clearDriveFolderId(id: String)

    @Query("UPDATE diary_collection SET name = :name WHERE id = :id")
    suspend fun updateName(id: String, name: String)

    @Query("DELETE FROM diary_collection")
    suspend fun deleteAll()
}
