package com.vm.core.models

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface SleepEntryDao {
    @Query("SELECT * FROM sleep_entry WHERE date = :date LIMIT 1")
    fun getByDate(date: String): Flow<SleepEntry?>

    @Query("SELECT * FROM sleep_entry WHERE date = :date LIMIT 1")
    suspend fun getByDateSync(date: String): SleepEntry?

    @Query(
        "SELECT * FROM sleep_entry WHERE date >= :startDate AND date <= :endDate ORDER BY date ASC"
    )
    suspend fun getBetweenSync(startDate: String, endDate: String): List<SleepEntry>

    @Query("SELECT MIN(date) FROM sleep_entry")
    suspend fun getMinDate(): String?

    @Query(
        """
        SELECT date FROM sleep_entry
        WHERE plannedBedtime IS NOT NULL
           OR plannedWakeUp IS NOT NULL
           OR actualBedtime IS NOT NULL
           OR actualWakeUp IS NOT NULL
           OR fellAsleepAt IS NOT NULL
           OR deepMinutes IS NOT NULL
           OR lightMinutes IS NOT NULL
           OR remMinutes IS NOT NULL
           OR awakeMinutes IS NOT NULL
        """
    )
    suspend fun getDatesWithContent(): List<String>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entry: SleepEntry)

    @Query("DELETE FROM sleep_entry WHERE date = :date")
    suspend fun deleteByDate(date: String)

    @Query("DELETE FROM sleep_entry")
    suspend fun deleteAll()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entries: List<SleepEntry>)

    @Query("DELETE FROM sleep_entry WHERE date >= :startDate AND date <= :endDate")
    suspend fun deleteBetween(startDate: String, endDate: String)

    @Query("SELECT DISTINCT substr(date, 1, 7) FROM sleep_entry")
    suspend fun getMonths(): List<String>
}
