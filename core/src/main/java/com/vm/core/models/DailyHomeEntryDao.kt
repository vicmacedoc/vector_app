package com.vm.core.models

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface DailyHomeEntryDao {
    @Query("SELECT * FROM daily_home WHERE date = :date LIMIT 1")
    fun getByDate(date: String): Flow<DailyHomeEntry?>

    @Query("SELECT * FROM daily_home WHERE date = :date LIMIT 1")
    suspend fun getByDateSync(date: String): DailyHomeEntry?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: DailyHomeEntry)

    @Query("DELETE FROM daily_home")
    suspend fun deleteAll()
}
