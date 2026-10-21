package com.vm.core.models

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ActivityLabelDao {
    @Query("SELECT * FROM activity_labels ORDER BY createdAt ASC")
    fun getAll(): Flow<List<ActivityLabel>>

    @Query("SELECT * FROM activity_labels ORDER BY createdAt ASC")
    suspend fun getAllSync(): List<ActivityLabel>

    @Query("SELECT * FROM activity_labels WHERE id = :id LIMIT 1")
    suspend fun getByIdSync(id: String): ActivityLabel?

    @Query("SELECT * FROM activity_labels WHERE LOWER(name) = LOWER(:name) LIMIT 1")
    suspend fun getByNameSync(name: String): ActivityLabel?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(label: ActivityLabel)

    @Query("DELETE FROM activity_labels WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM activity_labels")
    suspend fun deleteAll()
}
