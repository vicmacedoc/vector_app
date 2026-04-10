package com.vm.vector.data

import com.vm.core.models.RoutineEntry
import com.vm.core.models.RoutineStatus
import com.vm.core.models.RoutineType
import com.vm.core.models.VectorDatabase
import com.vm.core.models.WorkoutSet
import com.vm.vector.data.analysis.AnalysisStats
import com.vm.vector.data.analysis.CountMaxStreak
import com.vm.vector.data.analysis.TrainingVolumeMath
import com.vm.vector.data.analysis.HabitStatusBreakdown
import com.vm.vector.data.analysis.LineSeries
import com.vm.vector.data.analysis.RoutineHabitKey
import com.vm.vector.data.analysis.TripleStat
import com.vm.vector.data.analysis.WeekVolumePoint
import java.time.LocalDate
import java.util.Locale

enum class AnalysisTimeWindow {
    DAYS_7,
    DAYS_30,
    DAYS_90,
    ALL,
    CUSTOM,
}

class AnalysisRepository(
    private val database: VectorDatabase,
    private val workoutRepository: WorkoutRepository,
) {

    private val routineDao get() = database.routineEntryDao()
    private val dietDao get() = database.dietEntryDao()
    private val workoutDao get() = database.workoutSetDao()
    private val homeDao get() = database.dailyHomeEntryDao()
    private val diaryDao get() = database.diaryEntryDao()

    suspend fun resolveDateRange(
        window: AnalysisTimeWindow,
        customStart: LocalDate?,
        customEnd: LocalDate?,
    ): Pair<String, String> {
        val endLd = AnalysisStats.today()
        val end = AnalysisStats.formatDate(endLd)
        if (window == AnalysisTimeWindow.CUSTOM) {
            val s = customStart ?: endLd.minusDays(29)
            val e = customEnd ?: endLd
            return if (!s.isAfter(e)) {
                Pair(AnalysisStats.formatDate(s), AnalysisStats.formatDate(e))
            } else {
                Pair(AnalysisStats.formatDate(e), AnalysisStats.formatDate(s))
            }
        }
        val startLd = when (window) {
            AnalysisTimeWindow.DAYS_7 -> endLd.minusDays(6)
            AnalysisTimeWindow.DAYS_30 -> endLd.minusDays(29)
            AnalysisTimeWindow.DAYS_90 -> endLd.minusDays(89)
            AnalysisTimeWindow.ALL -> {
                val candidates = listOfNotNull(
                    routineDao.getMinDate(),
                    dietDao.getMinDate(),
                    workoutDao.getMinDate(),
                    homeDao.getMinDate(),
                    diaryDao.getMinDate(),
                )
                val earliestStr = candidates.minOrNull()
                if (earliestStr != null) {
                    val e = AnalysisStats.parseDate(earliestStr)
                    if (e.isAfter(endLd)) endLd else e
                } else endLd
            }
            AnalysisTimeWindow.CUSTOM -> error("CUSTOM handled above")
        }
        val start = AnalysisStats.formatDate(startLd)
        return if (start > end) Pair(end, end) else Pair(start, end)
    }

    suspend fun loadDailyPlanSeries(start: String, end: String): Pair<CountMaxStreak, LineSeries> {
        val days = AnalysisStats.eachDayInclusive(AnalysisStats.parseDate(start), AnalysisStats.parseDate(end))
        val rows = homeDao.getEntriesBetweenSync(start, end).associateBy { it.date }
        val y = days.map { d -> rows[d]?.dailyPlanCompletionPercent?.toDouble() }
        val flags = days.map { rows[it] != null }
        val nums = y.filterNotNull()
        val streak = AnalysisStats.longestConsecutiveTrue(flags)
        val maxV = nums.maxOrNull() ?: 0.0
        val count = nums.size
        return Pair(
CountMaxStreak(countWithData = count, maxValue = maxV, longestStreak = streak),
LineSeries(days, y, "% complete")
        )
    }

    suspend fun loadRoutineCategorySeries(start: String, end: String): Pair<CountMaxStreak, LineSeries> {
        val days = AnalysisStats.eachDayInclusive(AnalysisStats.parseDate(start), AnalysisStats.parseDate(end))
        val all = routineDao.getEntriesBetweenSync(start, end)
        val byDate = all.groupBy { it.date }
        val y = days.map { d ->
            val list = byDate[d].orEmpty()
            if (list.isEmpty()) null
            else {
                val done = AnalysisStats.routineCompletedCount(list)
                100.0 * done / list.size
            }
        }
        val flags = days.map { byDate[it].orEmpty().isNotEmpty() }
        val nums = y.filterNotNull()
        return Pair(
CountMaxStreak(
                countWithData = nums.size,
                maxValue = nums.maxOrNull() ?: 0.0,
                longestStreak = AnalysisStats.longestConsecutiveTrue(flags)
            ),
LineSeries(days, y, "% complete")
        )
    }

    suspend fun loadDietCategorySeries(start: String, end: String): Pair<CountMaxStreak, LineSeries> {
        val days = AnalysisStats.eachDayInclusive(AnalysisStats.parseDate(start), AnalysisStats.parseDate(end))
        val all = dietDao.getEntriesBetweenSync(start, end)
        val byDate = all.groupBy { it.date }
        val y = days.map { d ->
            val list = byDate[d].orEmpty()
            if (list.isEmpty()) null
            else {
                val done = AnalysisStats.dietDoneCount(list)
                100.0 * done / list.size
            }
        }
        val flags = days.map { byDate[it].orEmpty().isNotEmpty() }
        val nums = y.filterNotNull()
        return Pair(
CountMaxStreak(
                countWithData = nums.size,
                maxValue = nums.maxOrNull() ?: 0.0,
                longestStreak = AnalysisStats.longestConsecutiveTrue(flags)
            ),
LineSeries(days, y, "% complete")
        )
    }

    suspend fun loadWorkoutCategorySeries(start: String, end: String): Pair<CountMaxStreak, LineSeries> {
        val days = AnalysisStats.eachDayInclusive(AnalysisStats.parseDate(start), AnalysisStats.parseDate(end))
        val all = workoutDao.getSetsBetweenSync(start, end)
        val byDate = all.groupBy { it.date }
        val y = days.map { d ->
            val list = byDate[d].orEmpty()
            if (list.isEmpty()) null
            else {
                val done = AnalysisStats.workoutCompletedSetCount(list)
                100.0 * done / list.size
            }
        }
        val flags = days.map { byDate[it].orEmpty().isNotEmpty() }
        val nums = y.filterNotNull()
        return Pair(
CountMaxStreak(
                countWithData = nums.size,
                maxValue = nums.maxOrNull() ?: 0.0,
                longestStreak = AnalysisStats.longestConsecutiveTrue(flags)
            ),
LineSeries(days, y, "% complete")
        )
    }

    /** Per day: % of diet items that are planned/adjusted, checked, and eaten vs all items that day. */
    suspend fun loadDietPlanComplianceSeries(start: String, end: String): Pair<CountMaxStreak, LineSeries> {
        val days = AnalysisStats.eachDayInclusive(AnalysisStats.parseDate(start), AnalysisStats.parseDate(end))
        val all = dietDao.getEntriesBetweenSync(start, end)
        val byDate = all.groupBy { it.date }
        val y = days.map { d ->
            val list = byDate[d].orEmpty()
            if (list.isEmpty()) null
            else {
                val ok = AnalysisStats.dietPlanCompliantItemCount(list)
                100.0 * ok / list.size
            }
        }
        val flags = days.map { byDate[it].orEmpty().isNotEmpty() }
        val nums = y.filterNotNull()
        return Pair(
            CountMaxStreak(
                countWithData = nums.size,
                maxValue = nums.maxOrNull() ?: 0.0,
                longestStreak = AnalysisStats.longestConsecutiveTrue(flags)
            ),
            LineSeries(days, y, "% compliant")
        )
    }

    suspend fun loadDiaryCategorySeries(start: String, end: String): Pair<CountMaxStreak, LineSeries> {
        val days = AnalysisStats.eachDayInclusive(AnalysisStats.parseDate(start), AnalysisStats.parseDate(end))
        val byDate = diaryDao.getEntriesBetweenSync(start, end).associateBy { it.date }
        val y = days.map { d ->
            val e = byDate[d] ?: return@map null
            if (AnalysisStats.diaryEntryLooksComplete(e)) 100.0 else 0.0
        }
        val flags = days.map { byDate[it] != null }
        val nums = y.filterNotNull()
        return Pair(
            CountMaxStreak(
                countWithData = nums.size,
                maxValue = nums.maxOrNull() ?: 0.0,
                longestStreak = AnalysisStats.longestConsecutiveTrue(flags)
            ),
            LineSeries(days, y, "% complete")
        )
    }

    suspend fun loadDietMacros(start: String, end: String): Map<String, Pair<TripleStat?, LineSeries>> {
        val days = AnalysisStats.eachDayInclusive(AnalysisStats.parseDate(start), AnalysisStats.parseDate(end))
        val all = dietDao.getEntriesBetweenSync(start, end)
        val byDate = all.groupBy { it.date }
        val kcalY = days.map { d -> byDate[d]?.takeIf { it.isNotEmpty() }?.sumOf { it.kcal } }
        val protY = days.map { d -> byDate[d]?.takeIf { it.isNotEmpty() }?.sumOf { it.protein } }
        val carbY = days.map { d -> byDate[d]?.takeIf { it.isNotEmpty() }?.sumOf { it.carbs } }
        val fatY = days.map { d -> byDate[d]?.takeIf { it.isNotEmpty() }?.sumOf { it.fats } }
        return mapOf(
            "kcal" to Pair(AnalysisStats.maxAvgMin(kcalY.filterNotNull()), LineSeries(days, kcalY.map { it?.toDouble() }, "kcal")),
            "protein" to Pair(AnalysisStats.maxAvgMin(protY.filterNotNull()), LineSeries(days, protY.map { it?.toDouble() }, "g")),
            "carbs" to Pair(AnalysisStats.maxAvgMin(carbY.filterNotNull()), LineSeries(days, carbY.map { it?.toDouble() }, "g")),
            "fats" to Pair(AnalysisStats.maxAvgMin(fatY.filterNotNull()), LineSeries(days, fatY.map { it?.toDouble() }, "g")),
        )
    }

    suspend fun loadDailyInfo(start: String, end: String): Map<String, Pair<TripleStat?, LineSeries>> {
        val days = AnalysisStats.eachDayInclusive(AnalysisStats.parseDate(start), AnalysisStats.parseDate(end))
        val rows = homeDao.getEntriesBetweenSync(start, end).associateBy { it.date }
        val w = days.map { d -> rows[d]?.weightKg }
        val bf = days.map { d -> rows[d]?.bodyFatPercent }
        val sleep = days.map { d ->
            rows[d]?.let { AnalysisStats.sleepDurationHours(it.actualSleepStart, it.actualSleepEnd) }
        }
        return mapOf(
            "weight" to Pair(
                AnalysisStats.maxAvgMin(w.filterNotNull()),
LineSeries(days, w.map { it?.toDouble() }, "kg")
            ),
            "bodyFat" to Pair(
                AnalysisStats.maxAvgMin(bf.filterNotNull()),
LineSeries(days, bf.map { it?.toDouble() }, "%")
            ),
            "sleep" to Pair(
                AnalysisStats.maxAvgMin(sleep.filterNotNull()),
LineSeries(days, sleep, "hours")
            ),
        )
    }

    suspend fun listRoutineHabits(start: String, end: String): List<RoutineHabitKey> {
        val all = routineDao.getEntriesBetweenSync(start, end)
        return all.map { RoutineHabitKey(it.category, it.title, it.type) }
            .distinctBy { Triple(it.category, it.title, it.type) }
            .sortedWith(compareBy({ it.category }, { it.title }))
    }

    fun habitBreakdown(start: String, end: String, key: RoutineHabitKey, allEntries: List<RoutineEntry>): HabitStatusBreakdown {
        val days = AnalysisStats.eachDayInclusive(AnalysisStats.parseDate(start), AnalysisStats.parseDate(end))
        val matching = allEntries.filter { it.category == key.category && it.title == key.title && it.type == key.type }
        val byDate = matching.associateBy { it.date }
        val statusCounts = mutableMapOf<RoutineStatus, Int>()
        RoutineStatus.entries.forEach { statusCounts[it] = 0 }
        for (e in matching) {
            statusCounts[e.status] = (statusCounts[e.status] ?: 0) + 1
        }
        if (key.type != RoutineType.NUMERICAL) {
            return HabitStatusBreakdown(statusCounts, null, null)
        }
        val y = days.map { d ->
            val e = byDate[d] ?: return@map null
            when {
                e.status == RoutineStatus.NA -> null
                e.currentValue == null -> null
                else -> e.currentValue
            }
        }
        val triple = AnalysisStats.maxAvgMin(y.filterNotNull())
        return HabitStatusBreakdown(
            counts = statusCounts,
            numericalLine = LineSeries(days, y, "value"),
            tripleStat = triple
        )
    }

    suspend fun loadRoutineEntriesForHabits(start: String, end: String): List<RoutineEntry> =
        routineDao.getEntriesBetweenSync(start, end)

    /**
     * Uses [WorkoutRepository.loadWorkoutForDateDisplay] per day so `targetMuscles` from the active preset
     * is merged even when Room rows still have an empty CSV (until Save Workout).
     */
    private suspend fun mergedResistanceSetsInRange(start: String, end: String): List<WorkoutSet> {
        val rangeStart = AnalysisStats.parseDate(start)
        val rangeEnd = AnalysisStats.parseDate(end)
        val days = AnalysisStats.eachDayInclusive(rangeStart, rangeEnd)
        val out = ArrayList<WorkoutSet>()
        for (date in days) {
            val merged = workoutRepository.loadWorkoutForDateDisplay(date).getOrNull()
            if (merged != null) {
                out.addAll(merged.filter { it.exerciseType.equals("RESISTANCE", ignoreCase = true) })
            } else {
                out.addAll(
                    workoutDao.getSetsByDateSync(date)
                        .filter { it.exerciseType.equals("RESISTANCE", ignoreCase = true) }
                )
            }
        }
        return out
    }

    suspend fun listMuscles(start: String, end: String): List<String> {
        val sets = mergedResistanceSetsInRange(start, end)
        return sets.flatMap { TrainingVolumeMath.orderedTargetTags(it.targetMuscles) }
            .distinct()
            .sorted()
    }

    suspend fun loadVolumeSeriesForMuscle(start: String, end: String, muscle: String): Pair<TripleStat?, List<WeekVolumePoint>> {
        val m = muscle.lowercase(Locale.US)
        val rangeStart = AnalysisStats.parseDate(start)
        val rangeEnd = AnalysisStats.parseDate(end)
        val weeks = AnalysisStats.weekStartsMondayInclusive(rangeStart, rangeEnd)
        val allSets = mergedResistanceSetsInRange(start, end)
        val points = weeks.map { monday ->
            val monStr = AnalysisStats.formatDate(monday)
            val sun = monday.plusDays(6)
            var load = 0.0
            for (set in allSets) {
                val d = AnalysisStats.parseDate(set.date)
                if (d.isBefore(monday) || d.isAfter(sun)) continue
                load += TrainingVolumeMath.fractionalLoadForMuscle(set, m)
            }
            WeekVolumePoint(monStr, load)
        }
        val vals = points.map { it.volumeLoad }
        val stat = AnalysisStats.maxAvgMin(vals)
        return stat to points
    }

    data class MoodGridRow(
        val mondayIso: String,
        /** 7 entries Mon..Sun: null = N/A/out of range, 1..5 mood */
        val cells: List<Int?>,
    )

    suspend fun loadMoodGrid(start: String, end: String): List<MoodGridRow> {
        val rangeStart = AnalysisStats.parseDate(start)
        val rangeEnd = AnalysisStats.parseDate(end)
        val diaries = diaryDao.getEntriesBetweenSync(start, end).associateBy { it.date }
        val weeks = AnalysisStats.weekStartsMondayInclusive(rangeStart, rangeEnd)
        return weeks.map { monday ->
            val cells = (0 until 7).map { offset ->
                val d = monday.plusDays(offset.toLong())
                val ds = AnalysisStats.formatDate(d)
                when {
                    d.isBefore(rangeStart) || d.isAfter(rangeEnd) -> null
                    else -> diaries[ds]?.mood
                }
            }
            MoodGridRow(AnalysisStats.formatDate(monday), cells)
        }
    }
}
