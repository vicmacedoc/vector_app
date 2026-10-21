package com.vm.core.models

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface CalendarDraftDao {
    @Query("SELECT * FROM calendar_drafts WHERE category = :category AND date = :date LIMIT 1")
    suspend fun get(category: String, date: String): CalendarDraft?

    @Query("SELECT category, date FROM calendar_drafts WHERE date >= :startDate AND date <= :endDate")
    fun observeKeysBetween(startDate: String, endDate: String): Flow<List<CalendarDraftKey>>

    @Query("SELECT category, date FROM calendar_drafts WHERE date >= :startDate AND date <= :endDate")
    suspend fun getKeysBetween(startDate: String, endDate: String): List<CalendarDraftKey>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(draft: CalendarDraft)

    @Query("DELETE FROM calendar_drafts WHERE category = :category AND date = :date")
    suspend fun delete(category: String, date: String)

    @Query("DELETE FROM calendar_drafts")
    suspend fun deleteAll()
}
