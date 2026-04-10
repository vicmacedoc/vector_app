package com.vm.core.models

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface RoutineEntryDao {
    @Query("SELECT * FROM routine_logs WHERE date = :date ORDER BY category ASC, title ASC")
    fun getEntriesByDate(date: String): Flow<List<RoutineEntry>>
    
    @Query("SELECT * FROM routine_logs WHERE date = :date ORDER BY category ASC, title ASC")
    suspend fun getEntriesByDateSync(date: String): List<RoutineEntry>

    @Query(
        "SELECT * FROM routine_logs WHERE date >= :startDate AND date <= :endDate ORDER BY date ASC, category ASC, title ASC"
    )
    suspend fun getEntriesBetweenSync(startDate: String, endDate: String): List<RoutineEntry>

    @Query("SELECT MIN(date) FROM routine_logs")
    suspend fun getMinDate(): String?
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEntry(entry: RoutineEntry)
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEntries(entries: List<RoutineEntry>)
    
    @Update
    suspend fun updateEntry(entry: RoutineEntry)
    
    @Delete
    suspend fun deleteEntry(entry: RoutineEntry)
    
    @Query("DELETE FROM routine_logs WHERE date = :date")
    suspend fun deleteEntriesByDate(date: String)
    
    @Query("DELETE FROM routine_logs")
    suspend fun deleteAllEntries()
    
    @Query("SELECT DISTINCT date FROM routine_logs WHERE status = 'DONE' OR (type = 'NUMERICAL' AND isGoalMet = 1)")
    suspend fun getDatesWithCompletedRoutines(): List<String>
}
