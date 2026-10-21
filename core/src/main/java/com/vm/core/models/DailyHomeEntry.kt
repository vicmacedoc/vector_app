package com.vm.core.models

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

/**
 * Single row per date: body metrics and daily plan text/audio.
 * Sleep lives on [SleepEntry]. Save replaces any existing row for the same date.
 */
@Entity(tableName = "daily_home")
@Serializable
data class DailyHomeEntry(
    @PrimaryKey val date: String,       // YYYY-MM-DD
    val weightKg: Double? = null,
    val bodyFatPercent: Double? = null,
    val dailyPlanText: String? = null,
    val dailyPlanAudioPath: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)
