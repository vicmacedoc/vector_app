package com.vm.core.models

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface MealLibraryItemDao {
    @Query("SELECT * FROM meal_library ORDER BY name COLLATE NOCASE ASC")
    fun getAll(): Flow<List<MealLibraryItem>>

    @Query("SELECT * FROM meal_library WHERE nameKey = :nameKey LIMIT 1")
    suspend fun getByNameKey(nameKey: String): MealLibraryItem?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: MealLibraryItem)

    @Query("DELETE FROM meal_library WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM meal_library")
    suspend fun deleteAll()

    @Query("SELECT * FROM meal_library")
    suspend fun getAllSync(): List<MealLibraryItem>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<MealLibraryItem>)
}
