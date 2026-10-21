package com.vm.vector.data.backup

import com.vm.vector.data.DatabaseBackupManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

class BackupRetentionTest {

    @Test
    fun parseStamp_readsDateAndTime() {
        assertEquals(LocalDateTime.of(2026, 10, 4, 14, 0), BackupRetention.parseStamp("vector_db_2026-10-04_1400_v38.db.gz"))
        assertEquals(LocalDate.of(2026, 10, 4).atStartOfDay(), BackupRetention.parseStamp("state_2026-10-04.json"))
        assertNull(BackupRetention.parseStamp("vector_database.db"))
    }

    @Test
    fun keep_dailyForAWeekThenWeeklyThenMonthly() {
        val today = LocalDate.of(2026, 10, 4)
        val all = (0L until 400L).map { today.minusDays(it).atTime(14, 0) }
        val kept = BackupRetention.keep(all, timeOf = { it })

        (0L until 7L).forEach { assertTrue(today.minusDays(it).atTime(14, 0) in kept) }
        assertFalse(today.minusDays(9).atTime(14, 0) in kept)
        // 7 daily + up to 4 weekly + up to 12 monthly, with overlaps
        assertTrue(kept.size in 12..23)
        assertTrue(kept.minOrNull()!! >= today.minusMonths(12).atStartOfDay())
    }

    @Test
    fun keep_newestOfSameDayWins() {
        val morning = LocalDateTime.of(2026, 10, 4, 8, 0)
        val afternoon = LocalDateTime.of(2026, 10, 4, 14, 0)
        val kept = BackupRetention.keep(listOf(morning, afternoon), timeOf = { it })
        assertEquals(setOf(afternoon), kept)
    }

    @Test
    fun guard_flagsEmptyDatabaseAgainstLargeBackup() {
        assertTrue(DatabaseBackupManager.isMuchSmaller(localRows = 30, remoteRows = 5_000))
        assertFalse(DatabaseBackupManager.isMuchSmaller(localRows = 4_000, remoteRows = 5_000))
        assertFalse(DatabaseBackupManager.isMuchSmaller(localRows = 0, remoteRows = 20))
        assertTrue(DatabaseBackupManager.isMuchSmallerBytes(localBytes = 100_000, remoteBytes = 2_000_000))
        assertFalse(DatabaseBackupManager.isMuchSmallerBytes(localBytes = 1_900_000, remoteBytes = 2_000_000))
    }
}
