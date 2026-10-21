package com.vm.core.models

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface RoutinePresetDao {
    @Query("SELECT * FROM routine_presets ORDER BY name COLLATE NOCASE ASC")
    fun getAll(): Flow<List<RoutinePreset>>

    @Query("SELECT * FROM routine_presets")
    suspend fun getAllOnce(): List<RoutinePreset>

    @Query("SELECT * FROM routine_presets WHERE nameKey = :nameKey LIMIT 1")
    suspend fun getByNameKey(nameKey: String): RoutinePreset?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(preset: RoutinePreset)

    @Query("DELETE FROM routine_presets WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM routine_presets")
    suspend fun deleteAll()
}
