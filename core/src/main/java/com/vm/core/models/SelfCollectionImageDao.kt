package com.vm.core.models

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface SelfCollectionImageDao {
    @Query("SELECT * FROM self_collection_image WHERE collectionId = :collectionId ORDER BY takenAtMillis ASC")
    fun getByCollectionId(collectionId: String): Flow<List<SelfCollectionImage>>

    @Query("SELECT * FROM self_collection_image WHERE collectionId = :collectionId ORDER BY takenAtMillis ASC")
    suspend fun getByCollectionIdSync(collectionId: String): List<SelfCollectionImage>

    @Query("SELECT * FROM self_collection_image WHERE id = :id LIMIT 1")
    suspend fun getByIdSync(id: String): SelfCollectionImage?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(image: SelfCollectionImage)

    @Query("DELETE FROM self_collection_image WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM self_collection_image WHERE collectionId = :collectionId")
    suspend fun deleteByCollectionId(collectionId: String)

    @Query("DELETE FROM self_collection_image")
    suspend fun deleteAll()

    @Query("SELECT * FROM self_collection_image ORDER BY takenAtMillis ASC")
    suspend fun getAllSync(): List<SelfCollectionImage>
}
