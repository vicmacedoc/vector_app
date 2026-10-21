package com.vm.vector.data

import com.vm.core.models.SleepEntry
import com.vm.core.models.SleepMath
import com.vm.core.models.VectorDatabase
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class SleepStage { DEEP, LIGHT, REM, AWAKE }

class SleepRepository(
    database: VectorDatabase,
) {
    private val dao = database.sleepEntryDao()
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    suspend fun getNight(date: String): SleepEntry? = dao.getByDateSync(date)

    suspend fun getNightsBetween(startDate: String, endDate: String): List<SleepEntry> =
        dao.getBetweenSync(startDate, endDate)

    suspend fun getMinDate(): String? = dao.getMinDate()

    /** Writes a night as-is; an entry with no fields set removes the row instead. */
    suspend fun saveNight(entry: SleepEntry) {
        if (entry.copy(timestamp = 0L) == SleepEntry(date = entry.date, timestamp = 0L)) {
            dao.deleteByDate(entry.date)
        } else {
            dao.upsert(entry.copy(timestamp = System.currentTimeMillis()))
        }
    }

    suspend fun deleteNight(date: String) = dao.deleteByDate(date)

    /**
     * Settings default: planned bedtime tonight and planned wake the following morning,
     * stored on today's night-keyed row.
     */
    suspend fun saveTodaySleepTarget(plannedBedtime: String, plannedWakeUp: String) {
        val date = dateFormat.format(Date())
        val existing = dao.getByDateSync(date) ?: SleepEntry(date = date)
        dao.upsert(
            existing.copy(
                plannedBedtime = plannedBedtime,
                plannedWakeUp = plannedWakeUp,
                timestamp = System.currentTimeMillis(),
            )
        )
    }
}

/** Pure edits on one night; the Calendar applies them in memory and saves with the Routine day. */
object SleepEdits {
    fun plannedBedtime(entry: SleepEntry, minutes: Int) =
        entry.copy(plannedBedtime = SleepMath.formatHhMm(minutes))

    fun plannedWakeUp(entry: SleepEntry, minutes: Int) =
        entry.copy(plannedWakeUp = SleepMath.formatHhMm(minutes))

    fun actualBedtime(entry: SleepEntry, minutes: Int) =
        entry.copy(actualBedtime = SleepMath.formatHhMm(minutes))

    fun actualWakeUp(entry: SleepEntry, minutes: Int) =
        entry.copy(actualWakeUp = SleepMath.formatHhMm(minutes))

    fun fellAsleep(entry: SleepEntry, minutes: Int?) =
        entry.copy(fellAsleepAt = minutes?.let { SleepMath.formatHhMm(it) })

    fun latency(entry: SleepEntry, latencyMinutes: Int?): SleepEntry {
        if (latencyMinutes == null) return entry.copy(fellAsleepAt = null)
        val bed = SleepMath.effectiveBedtime(entry) ?: return entry
        return entry.copy(fellAsleepAt = SleepMath.fellAsleepFromLatency(bed, latencyMinutes))
    }

    fun stageMinutes(entry: SleepEntry, stage: SleepStage, minutes: Int?): SleepEntry = when (stage) {
        SleepStage.DEEP -> entry.copy(deepMinutes = minutes)
        SleepStage.LIGHT -> entry.copy(lightMinutes = minutes)
        SleepStage.REM -> entry.copy(remMinutes = minutes)
        SleepStage.AWAKE -> entry.copy(awakeMinutes = minutes)
    }

    fun stagePercent(entry: SleepEntry, stage: SleepStage, percent: Int?): SleepEntry {
        if (percent == null) return stageMinutes(entry, stage, null)
        val total = SleepMath.totalSleepMinutes(entry) ?: return entry
        val mins = SleepMath.stageMinutesFromPercent(percent, total) ?: return entry
        return stageMinutes(entry, stage, mins)
    }

    fun applyAiEstimate(existing: SleepEntry, estimate: GeminiSleepClient.SleepEstimate): SleepEntry {
        var next = existing
        estimate.actualBedtime?.let { next = next.copy(actualBedtime = it) }
        estimate.actualWakeUp?.let { next = next.copy(actualWakeUp = it) }
        estimate.plannedBedtime?.let { next = next.copy(plannedBedtime = it) }
        estimate.plannedWakeUp?.let { next = next.copy(plannedWakeUp = it) }
        when {
            estimate.fellAsleepAt != null -> next = next.copy(fellAsleepAt = estimate.fellAsleepAt)
            estimate.latencyMinutes != null -> {
                val bed = SleepMath.effectiveBedtime(next)
                SleepMath.fellAsleepFromLatency(bed, estimate.latencyMinutes)?.let {
                    next = next.copy(fellAsleepAt = it)
                }
            }
        }
        val total = SleepMath.totalSleepMinutes(next)
        return next.copy(
            deepMinutes = resolveStage(estimate.deepMinutes, estimate.deepPercent, total, next.deepMinutes),
            lightMinutes = resolveStage(estimate.lightMinutes, estimate.lightPercent, total, next.lightMinutes),
            remMinutes = resolveStage(estimate.remMinutes, estimate.remPercent, total, next.remMinutes),
            awakeMinutes = resolveStage(estimate.awakeMinutes, estimate.awakePercent, total, next.awakeMinutes),
        )
    }

    private fun resolveStage(
        minutes: Int?,
        percent: Int?,
        totalSleep: Int?,
        existing: Int?,
    ): Int? {
        if (minutes != null) return minutes
        if (percent == null) return existing
        return SleepMath.stageMinutesFromPercent(percent, totalSleep) ?: existing
    }
}
