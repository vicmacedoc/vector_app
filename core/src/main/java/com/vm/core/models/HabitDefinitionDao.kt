package com.vm.core.models

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface HabitDefinitionDao {
    @Query("SELECT * FROM habit_definitions ORDER BY createdAt ASC")
    fun getAll(): Flow<List<HabitDefinition>>

    @Query("SELECT * FROM habit_definitions ORDER BY createdAt ASC")
    suspend fun getAllSync(): List<HabitDefinition>

    @Query("SELECT * FROM habit_definitions WHERE id = :id LIMIT 1")
    suspend fun getByIdSync(id: String): HabitDefinition?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(definition: HabitDefinition)

    @Query("DELETE FROM habit_definitions WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM habit_definitions")
    suspend fun deleteAll()
}
