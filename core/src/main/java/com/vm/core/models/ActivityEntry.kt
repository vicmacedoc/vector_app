package com.vm.core.models

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Serializable
enum class ActivityCompletion { NONE, DONE, NOT_DONE, CANCELLED, NA }

@Serializable
enum class ActivityType { PLANNED, NEW }

/**
 * Timed activity log for a calendar day.
 *
 * [startTime]/[endTime] are the planned window; editing the activity changes them.
 * [adjustedStartTime]/[adjustedEndTime] hold when it actually happened; both null means unadjusted.
 * A new adjustment replaces the previous one, and one matching the planned window is cleared.
 */
@Entity(
    tableName = "activity_logs",
    indices = [Index(value = ["date"], name = "index_activity_logs_date")]
)
@Serializable
data class ActivityEntry(
    @PrimaryKey val id: String,
    val date: String,
    val title: String,
    val startTime: String,   // planned HH:mm
    val endTime: String,     // planned HH:mm
    val adjustedStartTime: String? = null,
    val adjustedEndTime: String? = null,
    val labelId: String,
    val completion: ActivityCompletion = ActivityCompletion.NONE,
    val type: ActivityType = ActivityType.NEW,
    val description: String = "",
    val timestamp: Long = System.currentTimeMillis()
)

fun ActivityEntry.effectiveStartTime(): String = adjustedStartTime ?: startTime

fun ActivityEntry.effectiveEndTime(): String = adjustedEndTime ?: endTime

fun ActivityEntry.isAdjusted(): Boolean =
    adjustedStartTime != null || adjustedEndTime != null

/** Duration in minutes for [start]–[end] HH:mm, supporting overnight ranges. */
fun activityRangeMinutes(startHhMm: String, endHhMm: String): Int? {
    val start = parseActivityHhMm(startHhMm) ?: return null
    val end = parseActivityHhMm(endHhMm) ?: return null
    return if (end >= start) end - start else (24 * 60 - start) + end
}

fun ActivityEntry.plannedDurationMinutes(): Int? =
    activityRangeMinutes(startTime, endTime)

fun ActivityEntry.effectiveDurationMinutes(): Int? =
    activityRangeMinutes(effectiveStartTime(), effectiveEndTime())

/**
 * Minutes to credit as "done" for planned completion stats:
 * min(effective duration, original planned duration).
 */
fun ActivityEntry.cappedDoneMinutesTowardPlan(): Int {
    val planned = plannedDurationMinutes() ?: return 0
    val effective = effectiveDurationMinutes() ?: return 0
    return minOf(effective, planned)
}

private fun parseActivityHhMm(time: String?): Int? {
    if (time.isNullOrBlank()) return null
    val parts = time.trim().split(":")
    if (parts.size < 2) return null
    val h = parts[0].toIntOrNull() ?: return null
    val m = parts[1].toIntOrNull() ?: return null
    return h.coerceIn(0, 23) * 60 + m.coerceIn(0, 59)
}
