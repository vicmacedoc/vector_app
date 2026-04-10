package com.vm.core.models

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface WorkoutSetDao {
    @Query("SELECT * FROM workout_sets WHERE date = :date ORDER BY exerciseId ASC, setNumber ASC")
    fun getSetsByDate(date: String): Flow<List<WorkoutSet>>

    @Query("SELECT * FROM workout_sets WHERE date = :date ORDER BY exerciseId ASC, setNumber ASC")
    suspend fun getSetsByDateSync(date: String): List<WorkoutSet>

    @Query(
        "SELECT * FROM workout_sets WHERE date >= :startDate AND date <= :endDate ORDER BY date ASC, exerciseId ASC, setNumber ASC"
    )
    suspend fun getSetsBetweenSync(startDate: String, endDate: String): List<WorkoutSet>

    @Query("SELECT MIN(date) FROM workout_sets")
    suspend fun getMinDate(): String?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSet(entry: WorkoutSet)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSets(entries: List<WorkoutSet>)

    @Update
    suspend fun updateSet(entry: WorkoutSet)

    @Query("DELETE FROM workout_sets WHERE date = :date")
    suspend fun deleteSetsByDate(date: String)

    @Query("DELETE FROM workout_sets")
    suspend fun deleteAllSets()
}
