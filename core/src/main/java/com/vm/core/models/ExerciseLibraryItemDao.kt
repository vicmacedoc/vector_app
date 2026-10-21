package com.vm.core.models

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ExerciseLibraryItemDao {
    @Query("SELECT * FROM exercise_library ORDER BY name COLLATE NOCASE ASC")
    fun getAll(): Flow<List<ExerciseLibraryItem>>

    @Query("SELECT * FROM exercise_library WHERE typeKey = :typeKey AND nameKey = :nameKey LIMIT 1")
    suspend fun getByTypeAndName(typeKey: String, nameKey: String): ExerciseLibraryItem?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: ExerciseLibraryItem)

    @Query("DELETE FROM exercise_library WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM exercise_library")
    suspend fun deleteAll()

    @Query("SELECT * FROM exercise_library")
    suspend fun getAllSync(): List<ExerciseLibraryItem>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<ExerciseLibraryItem>)
}
