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

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(collection: DiaryCollection)

    @Query("DELETE FROM diary_collection WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("UPDATE diary_collection SET driveFolderId = :folderId WHERE id = :id")
    suspend fun updateDriveFolderId(id: String, folderId: String)

    @Query("DELETE FROM diary_collection")
    suspend fun deleteAll()
}
