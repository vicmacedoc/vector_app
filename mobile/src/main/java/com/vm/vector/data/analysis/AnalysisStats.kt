package com.vm.vector.data.analysis

import com.vm.core.models.AuditStatus
import com.vm.core.models.DiaryEntry
import com.vm.core.models.DietEntry
import com.vm.core.models.RoutineEntry
import com.vm.core.models.RoutineStatus
import com.vm.core.models.RoutineType
import com.vm.core.models.WorkoutSet
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import java.util.Locale
import kotlin.math.max

object AnalysisStats {

    private val iso: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE

    fun parseDate(s: String): LocalDate = LocalDate.parse(s, iso)

    fun formatDate(d: LocalDate): String = d.format(iso)

    fun today(): LocalDate = LocalDate.now()

    /** Inclusive list of yyyy-MM-dd from start through end. */
    fun eachDayInclusive(start: LocalDate, end: LocalDate): List<String> {
        if (end.isBefore(start)) return emptyList()
        val out = ArrayList<String>((ChronoUnit.DAYS.between(start, end) + 1).toInt())
        var d = start
        while (!d.isAfter(end)) {
            out.add(formatDate(d))
            d = d.plusDays(1)
        }
        return out
    }

    /** Longest run of consecutive `true` along a dense day list (already chronological). */
    fun longestConsecutiveTrue(flags: List<Boolean>): Int {
        var best = 0
        var cur = 0
        for (f in flags) {
            if (f) {
                cur++
                best = max(best, cur)
            } else {
                cur = 0
            }
        }
        return best
    }

    fun maxAvgMin(values: List<Double>): TripleStat? {
        if (values.isEmpty()) return null
        val low = values.minOrNull()!!
        val high = values.maxOrNull()!!
        val avg = values.sum() / values.size
        return TripleStat(high = high, avg = avg, low = low)
    }

    /** Mondays (week starts Monday) for each week overlapping [rangeStart, rangeEnd]. */
    fun weekStartsMondayInclusive(rangeStart: LocalDate, rangeEnd: LocalDate): List<LocalDate> {
        if (rangeEnd.isBefore(rangeStart)) return emptyList()
        var monday = rangeStart.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val out = mutableListOf<LocalDate>()
        while (!monday.isAfter(rangeEnd)) {
            val sunday = monday.plusDays(6)
            if (!sunday.isBefore(rangeStart)) {
                out.add(monday)
            }
            monday = monday.plusWeeks(1)
        }
        return out
    }

    fun routineCompletedCount(entries: List<RoutineEntry>): Int =
        entries.count { e ->
            e.status == RoutineStatus.DONE ||
                (e.type == RoutineType.NUMERICAL && e.isGoalMet)
        }

    fun dietDoneCount(entries: List<DietEntry>): Int =
        entries.count { it.isChecked && !it.notEaten }

    /**
     * Items that follow the planned/adjusted audit trail and are marked eaten (done): excludes [AuditStatus.UNPLANNED].
     */
    fun dietPlanCompliantItemCount(entries: List<DietEntry>): Int =
        entries.count { e ->
            (e.status == AuditStatus.PLANNED || e.status == AuditStatus.ADJUSTED) &&
                e.isChecked && !e.notEaten
        }

    /** Diary row counts as "filled" for completion % (same idea as Home Daily Inputs diary circle). */
    fun diaryEntryLooksComplete(entry: DiaryEntry): Boolean =
        entry.mood != null ||
            !entry.journalText.isNullOrBlank() ||
            !entry.journalAudioDriveFileId.isNullOrBlank()

    /**
     * Calendar marks exercises via [WorkoutSet.workoutStatus] ("Done", "Partial", "Exceeded", …);
     * some paths still set [WorkoutSet.isCompleted].
     */
    fun workoutSetCountsAsCompleted(set: WorkoutSet): Boolean {
        if (set.isCompleted) return true
        return when (set.workoutStatus?.trim()?.lowercase(Locale.US)) {
            "partial", "done", "exceeded", "exceed" -> true
            else -> false
        }
    }

    fun workoutCompletedSetCount(sets: List<WorkoutSet>): Int =
        sets.count { workoutSetCountsAsCompleted(it) }

    /** Sleep duration hours from HH:mm strings; overnight if wake is before bed in same-day sense. */
    fun sleepDurationHours(bed: String?, wake: String?): Double? {
        if (bed.isNullOrBlank() || wake.isNullOrBlank()) return null
        val bedM = parseHmToMinutes(bed) ?: return null
        val wakeM = parseHmToMinutes(wake) ?: return null
        var wakeTotal = wakeM
        if (wakeTotal <= bedM) wakeTotal += 24 * 60
        return (wakeTotal - bedM) / 60.0
    }

    private fun parseHmToMinutes(s: String): Int? {
        val p = s.trim().split(":")
        if (p.size < 2) return null
        val h = p[0].toIntOrNull() ?: return null
        val m = p[1].toIntOrNull() ?: 0
        return ((h * 60 + m) % (24 * 60) + 24 * 60) % (24 * 60)
    }
}
