package com.vm.core.models

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface DiaryEntryDao {
    @Query("SELECT * FROM diary_entry WHERE date = :date LIMIT 1")
    fun getByDate(date: String): Flow<DiaryEntry?>

    @Query("SELECT * FROM diary_entry WHERE date = :date LIMIT 1")
    suspend fun getByDateSync(date: String): DiaryEntry?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: DiaryEntry)
}
