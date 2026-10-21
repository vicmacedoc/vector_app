package com.vm.core.models

import androidx.room.*

@Dao
interface HabitDayConfigDao {
    @Query(
        "SELECT * FROM habit_day_config WHERE date <= :date ORDER BY date DESC LIMIT 1"
    )
    suspend fun getLatestConfigOnOrBefore(date: String): HabitDayConfig?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(config: HabitDayConfig)

    @Query("DELETE FROM habit_day_config WHERE date > :date")
    suspend fun deleteConfigsAfter(date: String)

    @Query("DELETE FROM habit_day_config")
    suspend fun deleteAll()

    @Query("SELECT * FROM habit_day_config WHERE date >= :startDate AND date <= :endDate ORDER BY date ASC")
    suspend fun getBetweenSync(startDate: String, endDate: String): List<HabitDayConfig>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(configs: List<HabitDayConfig>)

    @Query("DELETE FROM habit_day_config WHERE date >= :startDate AND date <= :endDate")
    suspend fun deleteBetween(startDate: String, endDate: String)

    @Query("SELECT DISTINCT substr(date, 1, 7) FROM habit_day_config")
    suspend fun getMonths(): List<String>
}
