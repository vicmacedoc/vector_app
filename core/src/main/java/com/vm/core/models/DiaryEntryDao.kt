package com.vm.core.models

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface DiaryEntryDao {
    @Query("SELECT * FROM diary_entry WHERE date = :date LIMIT 1")
    fun getByDate(date: String): Flow<DiaryEntry?>

    @Query("SELECT * FROM diary_entry WHERE date = :date LIMIT 1")
    suspend fun getByDateSync(date: String): DiaryEntry?

    @Query(
        "SELECT * FROM diary_entry WHERE date >= :startDate AND date <= :endDate ORDER BY date ASC"
    )
    suspend fun getEntriesBetweenSync(startDate: String, endDate: String): List<DiaryEntry>

    @Query("SELECT MIN(date) FROM diary_entry")
    suspend fun getMinDate(): String?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: DiaryEntry)

    @Query("DELETE FROM diary_entry")
    suspend fun deleteAll()
}
