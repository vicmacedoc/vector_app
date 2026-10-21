package com.vm.core.models

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface HabitLogDao {
    @Query("SELECT * FROM habit_logs WHERE date = :date")
    fun getByDate(date: String): Flow<List<HabitLog>>

    @Query("SELECT * FROM habit_logs")
    fun getAll(): Flow<List<HabitLog>>

    @Query("SELECT * FROM habit_logs")
    suspend fun getAllSync(): List<HabitLog>

    @Query("SELECT * FROM habit_logs WHERE date = :date")
    suspend fun getByDateSync(date: String): List<HabitLog>

    @Query(
        "SELECT * FROM habit_logs WHERE date >= :startDate AND date <= :endDate ORDER BY date ASC"
    )
    suspend fun getBetweenSync(startDate: String, endDate: String): List<HabitLog>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(log: HabitLog)

    @Update
    suspend fun update(log: HabitLog)

    @Query("DELETE FROM habit_logs WHERE date = :date AND habitId = :habitId")
    suspend fun deleteForDateAndHabit(date: String, habitId: String)

    @Query("DELETE FROM habit_logs WHERE habitId = :habitId")
    suspend fun deleteByHabitId(habitId: String)

    @Query("DELETE FROM habit_logs WHERE habitId = :habitId AND date > :date")
    suspend fun deleteHabitAfterDate(date: String, habitId: String)

    @Query("SELECT DISTINCT date FROM habit_logs")
    suspend fun getDatesWithLogs(): List<String>

    @Query("DELETE FROM habit_logs")
    suspend fun deleteAll()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(logs: List<HabitLog>)

    @Query("DELETE FROM habit_logs WHERE date >= :startDate AND date <= :endDate")
    suspend fun deleteBetween(startDate: String, endDate: String)

    @Query("SELECT DISTINCT substr(date, 1, 7) FROM habit_logs")
    suspend fun getMonths(): List<String>
}
