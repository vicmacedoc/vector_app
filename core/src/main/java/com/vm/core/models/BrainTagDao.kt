package com.vm.core.models

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface BrainTagDao {
    @Query("SELECT * FROM brain_tag WHERE section = :section ORDER BY name COLLATE NOCASE ASC")
    fun getBySection(section: BrainSection): Flow<List<BrainTag>>

    @Query("SELECT * FROM brain_tag WHERE section = :section AND LOWER(name) = LOWER(:name) LIMIT 1")
    suspend fun getByNameSync(name: String, section: BrainSection): BrainTag?

    @Query("SELECT * FROM brain_tag")
    suspend fun getAllSync(): List<BrainTag>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(tag: BrainTag)

    @Query("DELETE FROM brain_tag WHERE id = :id")
    suspend fun deleteById(id: String)
}
