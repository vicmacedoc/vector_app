package com.vm.core.models

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface DiaryCollectionImageDao {
    @Query("SELECT * FROM diary_collection_image WHERE collectionId = :collectionId ORDER BY takenAtMillis ASC")
    fun getByCollectionId(collectionId: String): Flow<List<DiaryCollectionImage>>

    @Query("SELECT * FROM diary_collection_image WHERE collectionId = :collectionId ORDER BY takenAtMillis ASC")
    suspend fun getByCollectionIdSync(collectionId: String): List<DiaryCollectionImage>

    @Query("SELECT * FROM diary_collection_image WHERE id = :id LIMIT 1")
    suspend fun getByIdSync(id: String): DiaryCollectionImage?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(image: DiaryCollectionImage)

    @Query("DELETE FROM diary_collection_image WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM diary_collection_image WHERE collectionId = :collectionId")
    suspend fun deleteByCollectionId(collectionId: String)

    @Query("DELETE FROM diary_collection_image")
    suspend fun deleteAll()
}
