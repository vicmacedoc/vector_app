package com.vm.core.models

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface DailyHomeEntryDao {
    @Query("SELECT * FROM daily_home WHERE date = :date LIMIT 1")
    fun getByDate(date: String): Flow<DailyHomeEntry?>

    @Query("SELECT * FROM daily_home WHERE date = :date LIMIT 1")
    suspend fun getByDateSync(date: String): DailyHomeEntry?

    @Query(
        "SELECT * FROM daily_home WHERE date >= :startDate AND date <= :endDate ORDER BY date ASC"
    )
    suspend fun getEntriesBetweenSync(startDate: String, endDate: String): List<DailyHomeEntry>

    @Query("SELECT MIN(date) FROM daily_home")
    suspend fun getMinDate(): String?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: DailyHomeEntry)

    @Query("DELETE FROM daily_home")
    suspend fun deleteAll()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entries: List<DailyHomeEntry>)

    @Query("DELETE FROM daily_home WHERE date >= :startDate AND date <= :endDate")
    suspend fun deleteBetween(startDate: String, endDate: String)

    @Query("SELECT DISTINCT substr(date, 1, 7) FROM daily_home")
    suspend fun getMonths(): List<String>
}
