package com.vm.core.models

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface SelfEntryDao {
    @Query("SELECT * FROM self_entry WHERE date = :date LIMIT 1")
    fun getByDate(date: String): Flow<SelfEntry?>

    @Query("SELECT * FROM self_entry WHERE date = :date LIMIT 1")
    suspend fun getByDateSync(date: String): SelfEntry?

    @Query(
        "SELECT * FROM self_entry WHERE date >= :startDate AND date <= :endDate ORDER BY date ASC"
    )
    suspend fun getEntriesBetweenSync(startDate: String, endDate: String): List<SelfEntry>

    @Query("SELECT MIN(date) FROM self_entry")
    suspend fun getMinDate(): String?

    /** Dates carrying any self signal; drives the calendar week dots. */
    @Query(
        """
        SELECT date FROM self_entry
        WHERE mood IS NOT NULL
           OR energy IS NOT NULL
           OR stress IS NOT NULL
           OR cognition IS NOT NULL
           OR motivation IS NOT NULL
           OR physical IS NOT NULL
           OR sleepQuality IS NOT NULL
           OR social IS NOT NULL
           OR selfEsteem IS NOT NULL
           OR actions IS NOT NULL
           OR external IS NOT NULL
           OR future IS NOT NULL
           OR (intention IS NOT NULL AND intention != '')
           OR (highlight IS NOT NULL AND highlight != '')
           OR (lowlight IS NOT NULL AND lowlight != '')
           OR (gratitude IS NOT NULL AND gratitude != '')
           OR (journalText IS NOT NULL AND journalText != '')
           OR (journalAudioDriveFileId IS NOT NULL AND journalAudioDriveFileId != '')
        """
    )
    suspend fun getDatesWithContent(): List<String>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: SelfEntry)

    @Query("DELETE FROM self_entry WHERE date = :date")
    suspend fun deleteByDate(date: String)

    @Query("DELETE FROM self_entry")
    suspend fun deleteAll()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entries: List<SelfEntry>)

    @Query("DELETE FROM self_entry WHERE date >= :startDate AND date <= :endDate")
    suspend fun deleteBetween(startDate: String, endDate: String)

    @Query("SELECT DISTINCT substr(date, 1, 7) FROM self_entry")
    suspend fun getMonths(): List<String>
}
