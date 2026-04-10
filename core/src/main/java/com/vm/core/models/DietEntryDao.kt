package com.vm.core.models

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface DietEntryDao {
    @Query("SELECT * FROM diet_logs WHERE date = :date ORDER BY plannedTime ASC")
    fun getEntriesByDate(date: String): Flow<List<DietEntry>>
    
    @Query("SELECT * FROM diet_logs WHERE date = :date ORDER BY plannedTime ASC")
    suspend fun getEntriesByDateSync(date: String): List<DietEntry>

    @Query(
        "SELECT * FROM diet_logs WHERE date >= :startDate AND date <= :endDate ORDER BY date ASC, plannedTime ASC"
    )
    suspend fun getEntriesBetweenSync(startDate: String, endDate: String): List<DietEntry>

    @Query("SELECT MIN(date) FROM diet_logs")
    suspend fun getMinDate(): String?
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEntry(entry: DietEntry)
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEntries(entries: List<DietEntry>)
    
    @Update
    suspend fun updateEntry(entry: DietEntry)
    
    @Delete
    suspend fun deleteEntry(entry: DietEntry)
    
    @Query("DELETE FROM diet_logs WHERE date = :date")
    suspend fun deleteEntriesByDate(date: String)
    
    @Query("DELETE FROM diet_logs")
    suspend fun deleteAllEntries()
    
    @Query("SELECT DISTINCT date FROM diet_logs WHERE isChecked = 1")
    suspend fun getDatesWithCheckedMeals(): List<String>
}
