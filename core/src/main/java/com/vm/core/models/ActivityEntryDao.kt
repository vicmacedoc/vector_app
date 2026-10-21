package com.vm.core.models

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ActivityEntryDao {
    @Query("SELECT * FROM activity_logs WHERE date = :date ORDER BY startTime ASC")
    fun getByDate(date: String): Flow<List<ActivityEntry>>

    @Query("SELECT * FROM activity_logs WHERE date = :date ORDER BY startTime ASC")
    suspend fun getByDateSync(date: String): List<ActivityEntry>

    @Query(
        "SELECT * FROM activity_logs WHERE date >= :startDate AND date <= :endDate ORDER BY date ASC, startTime ASC"
    )
    suspend fun getBetweenSync(startDate: String, endDate: String): List<ActivityEntry>

    @Query("SELECT * FROM activity_logs WHERE id = :id LIMIT 1")
    suspend fun getByIdSync(id: String): ActivityEntry?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: ActivityEntry)

    @Update
    suspend fun update(entry: ActivityEntry)

    @Query("DELETE FROM activity_logs WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM activity_logs WHERE date = :date")
    suspend fun deleteByDate(date: String)

    @Query("DELETE FROM activity_logs WHERE labelId = :labelId")
    suspend fun deleteByLabelId(labelId: String)

    @Query("SELECT DISTINCT date FROM activity_logs")
    suspend fun getDatesWithEntries(): List<String>

    @Query("DELETE FROM activity_logs")
    suspend fun deleteAll()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entries: List<ActivityEntry>)

    @Query("DELETE FROM activity_logs WHERE date >= :startDate AND date <= :endDate")
    suspend fun deleteBetween(startDate: String, endDate: String)

    @Query("SELECT DISTINCT substr(date, 1, 7) FROM activity_logs")
    suspend fun getMonths(): List<String>
}
