package com.vm.core.models

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

/**
 * One sleep episode keyed by the calendar date you went to bed.
 *
 * Row N holds bedtime on night N, the wake-up the following morning, and
 * stage data for that same continuous span. Calendar day D reads row D−1
 * for last night and row D for tonight's bedtime.
 */
@Entity(tableName = "sleep_entry")
@Serializable
data class SleepEntry(
    @PrimaryKey val date: String,       // YYYY-MM-DD — night you went to bed
    /** HH:mm planned bedtime (may be past midnight). */
    val plannedBedtime: String? = null,
    /** HH:mm planned wake-up the following morning. */
    val plannedWakeUp: String? = null,
    /** HH:mm actual bedtime (may be past midnight). */
    val actualBedtime: String? = null,
    /** HH:mm actual wake-up the following morning. */
    val actualWakeUp: String? = null,
    /** HH:mm when sleep actually started; null means unset. */
    val fellAsleepAt: String? = null,
    val deepMinutes: Int? = null,
    val lightMinutes: Int? = null,
    val remMinutes: Int? = null,
    val awakeMinutes: Int? = null,
    val timestamp: Long = System.currentTimeMillis(),
)
