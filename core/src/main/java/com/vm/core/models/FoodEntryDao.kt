package com.vm.core.models

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface FoodEntryDao {
    @Query(
        "SELECT * FROM food_logs WHERE date = :date ORDER BY COALESCE(time, plannedTime) ASC"
    )
    fun getEntriesByDate(date: String): Flow<List<FoodEntry>>

    @Query(
        "SELECT * FROM food_logs WHERE date = :date ORDER BY COALESCE(time, plannedTime) ASC"
    )
    suspend fun getEntriesByDateSync(date: String): List<FoodEntry>

    @Query(
        "SELECT * FROM food_logs WHERE date >= :startDate AND date <= :endDate " +
            "ORDER BY date ASC, COALESCE(time, plannedTime) ASC"
    )
    suspend fun getEntriesBetweenSync(startDate: String, endDate: String): List<FoodEntry>

    @Query("SELECT MIN(date) FROM food_logs")
    suspend fun getMinDate(): String?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEntry(entry: FoodEntry)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEntries(entries: List<FoodEntry>)

    @Update
    suspend fun updateEntry(entry: FoodEntry)

    @Delete
    suspend fun deleteEntry(entry: FoodEntry)

    @Query("DELETE FROM food_logs WHERE date = :date")
    suspend fun deleteEntriesByDate(date: String)

    @Query("DELETE FROM food_logs")
    suspend fun deleteAllEntries()

    @Query("SELECT DISTINCT date FROM food_logs WHERE isChecked = 1")
    suspend fun getDatesWithCheckedMeals(): List<String>

    @Query("DELETE FROM food_logs WHERE date >= :startDate AND date <= :endDate")
    suspend fun deleteBetween(startDate: String, endDate: String)

    @Query("SELECT DISTINCT substr(date, 1, 7) FROM food_logs")
    suspend fun getMonths(): List<String>
}
