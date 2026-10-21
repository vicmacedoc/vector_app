package com.vm.core.models

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ActivityLibraryItemDao {
    @Query("SELECT * FROM activity_library ORDER BY title COLLATE NOCASE ASC")
    fun getAll(): Flow<List<ActivityLibraryItem>>

    @Query("SELECT * FROM activity_library WHERE nameKey = :nameKey LIMIT 1")
    suspend fun getByNameKey(nameKey: String): ActivityLibraryItem?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: ActivityLibraryItem)

    @Query("DELETE FROM activity_library WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM activity_library")
    suspend fun deleteAll()

    @Query("SELECT * FROM activity_library")
    suspend fun getAllSync(): List<ActivityLibraryItem>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<ActivityLibraryItem>)
}
