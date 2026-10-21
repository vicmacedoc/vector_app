package com.vm.core.models

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

data class MotionDayPoints(val dateKey: String, val points: Int)

@Dao
interface MotionScoreEventDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(event: MotionScoreEvent)

    @Query("DELETE FROM motion_score_event WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("SELECT dateKey, SUM(points) AS points FROM motion_score_event WHERE dateKey >= :fromKey GROUP BY dateKey")
    fun dailyPointsSince(fromKey: String): Flow<List<MotionDayPoints>>

    @Query("SELECT * FROM motion_score_event WHERE dateKey = :dateKey ORDER BY createdAt DESC")
    fun eventsOn(dateKey: String): Flow<List<MotionScoreEvent>>

    @Query("SELECT * FROM motion_score_event WHERE sourceType = 'SUPERMARKET' ORDER BY sourceId DESC LIMIT 1")
    fun lastSupermarketTrip(): Flow<MotionScoreEvent?>

    @Query("SELECT * FROM motion_score_event WHERE sourceType = 'SUPERMARKET' ORDER BY sourceId DESC LIMIT 1")
    suspend fun lastSupermarketTripSync(): MotionScoreEvent?

    @Query("SELECT * FROM motion_score_event")
    suspend fun getAllSync(): List<MotionScoreEvent>

    @Query("DELETE FROM motion_score_event")
    suspend fun deleteAll()
}
