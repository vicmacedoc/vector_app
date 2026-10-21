package com.vm.vector.data

import com.vm.core.models.ActivityCompletion
import com.vm.core.models.ActivityEntry
import com.vm.core.models.ActivityLabel
import com.vm.core.models.ActivityType
import com.vm.core.models.AuditStatus
import com.vm.core.models.FoodEntry
import com.vm.core.models.HabitLog
import com.vm.core.models.HabitStatus
import com.vm.core.models.SelfEntry
import com.vm.core.models.SelfScale
import com.vm.core.models.SleepEntry
import com.vm.core.models.SleepMath
import com.vm.core.models.VectorDatabase
import com.vm.core.models.WorkoutSet
import com.vm.core.models.cappedDoneMinutesTowardPlan
import com.vm.core.models.effectiveDurationMinutes
import com.vm.core.models.effectiveStartTime
import com.vm.core.models.isAdjusted
import com.vm.core.models.plannedDurationMinutes
import com.vm.vector.data.analysis.AnalysisStats
import com.vm.vector.data.analysis.BodyData
import com.vm.vector.data.analysis.DayTimes
import com.vm.vector.data.analysis.DomainSeries
import com.vm.vector.data.analysis.FoodData
import com.vm.vector.data.analysis.HabitRow
import com.vm.vector.data.analysis.LabelMinutes
import com.vm.vector.data.analysis.MoodGridRow
import com.vm.vector.data.analysis.MuscleVolume
import com.vm.vector.data.analysis.OverviewData
import com.vm.vector.data.analysis.RoutineData
import com.vm.vector.data.analysis.SelfData
import com.vm.vector.data.analysis.SelfScaleCorrelation
import com.vm.vector.data.analysis.SelfScaleSeries
import com.vm.vector.data.analysis.SleepCorrelation
import com.vm.vector.data.analysis.SleepData
import com.vm.vector.data.analysis.StackedSeries
import com.vm.vector.data.analysis.TrainingData
import com.vm.vector.data.analysis.TrainingVolumeMath
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale
import kotlin.math.abs

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

    private val foodDao get() = database.foodEntryDao()
    private val workoutDao get() = database.workoutSetDao()
    private val homeDao get() = database.dailyHomeEntryDao()
    private val sleepDao get() = database.sleepEntryDao()
    private val selfDao get() = database.selfEntryDao()
    private val habitLogDao get() = database.habitLogDao()
    private val habitDefinitionDao get() = database.habitDefinitionDao()
    private val activityEntryDao get() = database.activityEntryDao()
    private val activityLabelDao get() = database.activityLabelDao()
    private val collectionImageDao get() = database.selfCollectionImageDao()

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
                    foodDao.getMinDate(),
                    workoutDao.getMinDate(),
                    homeDao.getMinDate(),
                    sleepDao.getMinDate(),
                    selfDao.getMinDate(),
                    habitLogDao.getDatesWithLogs().minOrNull(),
                    activityEntryDao.getDatesWithEntries().minOrNull(),
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

    // ============================================================== Overview

    suspend fun loadOverview(start: String, end: String): OverviewData {
        val days = daysIn(start, end)

        val foodByDate = foodDao.getEntriesBetweenSync(start, end).groupBy { it.date }
        val workoutByDate = workoutDao.getSetsBetweenSync(start, end).groupBy { it.date }
        val selfByDate = selfDao.getEntriesBetweenSync(start, end).associateBy { it.date }
        val habitsByDate = habitLogDao.getBetweenSync(start, end).groupBy { it.date }
        val activitiesByDate = activityEntryDao.getBetweenSync(start, end).groupBy { it.date }

        val routine = days.map { d ->
            routineCompletionPercent(habitsByDate[d].orEmpty(), activitiesByDate[d].orEmpty())
        }
        val food = days.map { d ->
            val list = foodByDate[d].orEmpty()
            if (list.isEmpty()) null else 100.0 * AnalysisStats.foodDoneCount(list) / list.size
        }
        val exercise = days.map { d ->
            val list = workoutByDate[d].orEmpty()
            if (list.isEmpty()) null else 100.0 * AnalysisStats.workoutCompletedSetCount(list) / list.size
        }
        val self = days.map { d ->
            val entry = selfByDate[d] ?: return@map null
            if (AnalysisStats.selfEntryLooksComplete(entry)) 100.0 else 0.0
        }

        val domains = listOf(
            domainSeries("Routine", routine),
            domainSeries("Food", food),
            domainSeries("Exercise", exercise),
            domainSeries("Self", self),
        )

        val score = days.indices.map { i ->
            val present = domains.mapNotNull { it.values[i] }
            if (present.isEmpty()) null else present.sum() / present.size
        }

        return OverviewData(
            dates = days,
            domains = domains,
            score = score,
            scoreAverage = AnalysisStats.average(score.filterNotNull()),
            scoreDelta = AnalysisStats.halfSplitDelta(score),
            daysLogged = score.count { it != null },
        )
    }

    private fun domainSeries(name: String, values: List<Double?>): DomainSeries {
        val flags = values.map { it != null }
        return DomainSeries(
            name = name,
            values = values,
            daysWithData = flags.count { it },
            currentStreak = AnalysisStats.trailingConsecutiveTrue(flags),
            bestStreak = AnalysisStats.longestConsecutiveTrue(flags),
            average = AnalysisStats.average(values.filterNotNull()),
            delta = AnalysisStats.halfSplitDelta(values),
        )
    }

    /** Blends habit scores and planned-activity execution into one 0..100 routine figure. */
    private fun routineCompletionPercent(
        logs: List<HabitLog>,
        activities: List<ActivityEntry>,
    ): Double? {
        val habitScores = logs.mapNotNull { AnalysisStats.habitScore(it.status) }
        val habitPart = if (habitScores.isEmpty()) null else habitScores.sum() / habitScores.size

        val planned = activities.filter { it.type == ActivityType.PLANNED }
        val activityPart = if (planned.isEmpty()) {
            null
        } else {
            planned.count { it.completion == ActivityCompletion.DONE }.toDouble() / planned.size
        }

        val parts = listOfNotNull(habitPart, activityPart)
        if (parts.isEmpty()) return null
        return 100.0 * parts.sum() / parts.size
    }

    // =============================================================== Routine

    suspend fun loadRoutine(start: String, end: String): RoutineData {
        val days = daysIn(start, end)
        val dayCount = days.size.coerceAtLeast(1)

        val activities = activityEntryDao.getBetweenSync(start, end)
        val labels = activityLabelDao.getAllSync().associateBy { it.id }
        val sleepByDate = loadSleepWithPrior(start, end)

        val plannedByLabel = mutableMapOf<String, Double>()
        val doneByLabel = mutableMapOf<String, Double>()
        val colorByLabel = mutableMapOf<String, Int>()

        activities.forEach { entry ->
            val label = labels[entry.labelId]
            val name = label?.name ?: "Unlabelled"
            colorByLabel[name] = label?.colorArgb ?: UNLABELLED_COLOR
            if (entry.type == ActivityType.PLANNED) {
                plannedByLabel[name] =
                    (plannedByLabel[name] ?: 0.0) + (entry.plannedDurationMinutes() ?: 0)
            }
            if (entry.completion == ActivityCompletion.DONE) {
                val minutes = if (entry.type == ActivityType.PLANNED) {
                    entry.cappedDoneMinutesTowardPlan()
                } else {
                    entry.effectiveDurationMinutes() ?: 0
                }
                doneByLabel[name] = (doneByLabel[name] ?: 0.0) + minutes
            }
        }

        // Sleep attributed to day D = night keyed D−1 (bedtime that night → wake this morning).
        var sleepMinutes = 0.0
        days.forEach { d ->
            val hours = actualSleepHoursForDay(sleepByDate, d) ?: return@forEach
            sleepMinutes += hours * 60.0
        }
        if (sleepMinutes > 0.0) {
            val name = ActivityLabel.SLEEP_LABEL_NAME
            colorByLabel[name] = ActivityLabel.SLEEP_COLOR_ARGB
            plannedByLabel[name] = (plannedByLabel[name] ?: 0.0) + sleepMinutes
            doneByLabel[name] = (doneByLabel[name] ?: 0.0) + sleepMinutes
        }

        val labelMinutes = colorByLabel.keys
            .map { name ->
                LabelMinutes(
                    name = name,
                    colorArgb = colorByLabel[name] ?: UNLABELLED_COLOR,
                    plannedMinutes = (plannedByLabel[name] ?: 0.0) / dayCount,
                    doneMinutes = (doneByLabel[name] ?: 0.0) / dayCount,
                )
            }
            .filter { it.plannedMinutes > 0.0 || it.doneMinutes > 0.0 }
            .sortedByDescending { maxOf(it.plannedMinutes, it.doneMinutes) }

        val plannedTotal = labelMinutes.sumOf { it.plannedMinutes }
        val doneTotal = labelMinutes.sumOf { it.doneMinutes }

        val weekStarts = AnalysisStats.weekStartsMondayInclusive(
            AnalysisStats.parseDate(start),
            AnalysisStats.parseDate(end),
        )
        val weeks = weekStarts.map { AnalysisStats.formatDate(it) }
        val weeklyLabelHours = labelMinutes.map { label ->
            StackedSeries(
                name = label.name,
                colorArgb = label.colorArgb,
                values = weekStarts.map { monday ->
                    val sunday = monday.plusDays(6)
                    var minutes = 0.0
                    activities.forEach { entry ->
                        if (entry.completion != ActivityCompletion.DONE) return@forEach
                        val entryLabel = labels[entry.labelId]?.name ?: "Unlabelled"
                        if (entryLabel != label.name) return@forEach
                        val d = AnalysisStats.parseDate(entry.date)
                        if (d.isBefore(monday) || d.isAfter(sunday)) return@forEach
                        minutes += if (entry.type == ActivityType.PLANNED) {
                            entry.cappedDoneMinutesTowardPlan()
                        } else {
                            entry.effectiveDurationMinutes() ?: 0
                        }
                    }
                    if (label.name == ActivityLabel.SLEEP_LABEL_NAME) {
                        days.forEach { d ->
                            val date = AnalysisStats.parseDate(d)
                            if (date.isBefore(monday) || date.isAfter(sunday)) return@forEach
                            val hours = actualSleepHoursForDay(sleepByDate, d) ?: return@forEach
                            minutes += hours * 60.0
                        }
                    }
                    minutes / 60.0
                },
            )
        }.filter { series -> series.values.any { it > 0.0 } }

        val habits = buildHabitRows(days, start, end)
        val habitRates = habits.mapNotNull { it.completionRate }

        val drifts = activities
            .filter { it.type == ActivityType.PLANNED }
            .mapNotNull { entry ->
                val plannedStart = AnalysisStats.parseHhMm(entry.startTime)
                    ?: return@mapNotNull null
                val actualStart = AnalysisStats.parseHhMm(entry.effectiveStartTime())
                    ?: return@mapNotNull null
                val raw = abs(actualStart - plannedStart)
                minOf(raw, 24 * 60 - raw).toDouble()
            }

        return RoutineData(
            dates = days,
            labels = labelMinutes,
            avgPlannedHours = plannedTotal / 60.0,
            avgDoneHours = doneTotal / 60.0,
            planCompletionPercent = if (plannedTotal <= 0.0) {
                0
            } else {
                ((doneTotal / plannedTotal) * 100.0).toInt().coerceIn(0, 100)
            },
            weeks = weeks,
            weeklyLabelHours = weeklyLabelHours,
            habits = habits,
            habitOverallRate = AnalysisStats.average(habitRates),
            activitiesLogged = activities.size,
            adjustedPercent = if (activities.isEmpty()) {
                null
            } else {
                100.0 * activities.count { it.isAdjusted() } / activities.size
            },
            avgDriftMinutes = AnalysisStats.average(drifts),
        )
    }

    /**
     * A habit log row exists for exactly the days a habit was scheduled, so log presence
     * doubles as the schedule and one range query replaces per-day config lookups.
     */
    private suspend fun buildHabitRows(
        days: List<String>,
        start: String,
        end: String,
    ): List<HabitRow> {
        val logs = habitLogDao.getBetweenSync(start, end)
        if (logs.isEmpty()) return emptyList()
        val names = habitDefinitionDao.getAllSync().associate { it.id to it.name }
        val byHabit = logs.groupBy { it.habitId }

        return byHabit.entries
            .mapNotNull { (habitId, habitLogs) ->
                val name = names[habitId] ?: return@mapNotNull null
                val byDate = habitLogs.associateBy { it.date }
                val cells: List<HabitStatus?> = days.map { byDate[it]?.status }
                val scored = cells.mapNotNull { it?.let(AnalysisStats::habitScore) }
                val doneFlags = cells.map { status ->
                    status != null && AnalysisStats.habitScore(status) == 1.0
                }
                HabitRow(
                    name = name,
                    cells = cells,
                    scheduledDays = cells.count { it != null },
                    completionRate = if (scored.isEmpty()) null else 100.0 * scored.sum() / scored.size,
                    currentStreak = AnalysisStats.trailingConsecutiveTrue(doneFlags),
                    bestStreak = AnalysisStats.longestConsecutiveTrue(doneFlags),
                )
            }
            .sortedByDescending { it.completionRate ?: -1.0 }
    }

    // ================================================================== Food

    suspend fun loadFood(start: String, end: String): FoodData {
        val days = daysIn(start, end)
        val all = foodDao.getEntriesBetweenSync(start, end)
        val byDate = all.groupBy { it.date }

        fun dailySum(selector: (FoodEntry) -> Double): List<Double?> = days.map { d ->
            val list = byDate[d].orEmpty()
            if (list.isEmpty()) null else list.filter(AnalysisStats::foodWasEaten).sumOf(selector)
        }

        val kcal = dailySum { it.kcal * it.quantityMultiplier }
        val protein = dailySum { it.protein * it.quantityMultiplier }
        val carbs = dailySum { it.carbs * it.quantityMultiplier }
        val fats = dailySum { it.fats * it.quantityMultiplier }

        val avgProtein = AnalysisStats.average(protein.filterNotNull()) ?: 0.0
        val avgCarbs = AnalysisStats.average(carbs.filterNotNull()) ?: 0.0
        val avgFats = AnalysisStats.average(fats.filterNotNull()) ?: 0.0
        val proteinKcal = avgProtein * 4
        val carbsKcal = avgCarbs * 4
        val fatsKcal = avgFats * 9
        val macroKcal = (proteinKcal + carbsKcal + fatsKcal).takeIf { it > 0.0 }

        val weekStarts = AnalysisStats.weekStartsMondayInclusive(
            AnalysisStats.parseDate(start),
            AnalysisStats.parseDate(end),
        )
        val weeks = weekStarts.map { AnalysisStats.formatDate(it) }
        fun weeklyCount(predicate: (FoodEntry) -> Boolean): List<Double> =
            weekStarts.map { monday ->
                val sunday = monday.plusDays(6)
                all.count { entry ->
                    val d = AnalysisStats.parseDate(entry.date)
                    !d.isBefore(monday) && !d.isAfter(sunday) && predicate(entry)
                }.toDouble()
            }

        val adherence = listOf(
            StackedSeries("Planned", ADHERENCE_PLANNED_COLOR, weeklyCount {
                AnalysisStats.foodWasEaten(it) && it.status == AuditStatus.PLANNED
            }),
            StackedSeries("Adjusted", ADHERENCE_ADJUSTED_COLOR, weeklyCount {
                AnalysisStats.foodWasEaten(it) && it.status == AuditStatus.ADJUSTED
            }),
            StackedSeries("Unplanned", ADHERENCE_UNPLANNED_COLOR, weeklyCount {
                AnalysisStats.foodWasEaten(it) && it.status == AuditStatus.UNPLANNED
            }),
            StackedSeries("Skipped", ADHERENCE_SKIPPED_COLOR, weeklyCount { it.notEaten }),
        ).filter { series -> series.values.any { it > 0.0 } }

        val mealTimes = days.mapNotNull { d ->
            val minutes = byDate[d].orEmpty()
                .filter(AnalysisStats::foodWasEaten)
                .mapNotNull { AnalysisStats.parseHhMm(it.effectiveTime()) }
                .sorted()
            if (minutes.isEmpty()) null else DayTimes(d, minutes)
        }
        val firsts = mealTimes.mapNotNull { it.minutes.minOrNull()?.toDouble() }
        val lasts = mealTimes.mapNotNull { it.minutes.maxOrNull()?.toDouble() }
        val windows = mealTimes.mapNotNull { row ->
            val lo = row.minutes.minOrNull() ?: return@mapNotNull null
            val hi = row.minutes.maxOrNull() ?: return@mapNotNull null
            (hi - lo) / 60.0
        }

        val compliant = AnalysisStats.foodPlanCompliantItemCount(all)

        return FoodData(
            dates = days,
            kcal = kcal,
            kcalRolling = AnalysisStats.rollingAverage(kcal, 7),
            kcalStat = AnalysisStats.maxAvgMin(kcal.filterNotNull()),
            proteinStat = AnalysisStats.maxAvgMin(protein.filterNotNull()),
            avgKcal = AnalysisStats.average(kcal.filterNotNull()) ?: 0.0,
            avgProteinG = avgProtein,
            avgCarbsG = avgCarbs,
            avgFatsG = avgFats,
            proteinKcalShare = macroKcal?.let { 100.0 * proteinKcal / it } ?: 0.0,
            carbsKcalShare = macroKcal?.let { 100.0 * carbsKcal / it } ?: 0.0,
            fatsKcalShare = macroKcal?.let { 100.0 * fatsKcal / it } ?: 0.0,
            weeks = weeks,
            adherence = adherence,
            compliancePercent = if (all.isEmpty()) null else 100.0 * compliant / all.size,
            mealTimes = mealTimes,
            avgFirstMealMinutes = AnalysisStats.average(firsts)?.toInt(),
            avgLastMealMinutes = AnalysisStats.average(lasts)?.toInt(),
            avgEatingWindowHours = AnalysisStats.average(windows),
            daysLogged = byDate.keys.size,
        )
    }

    // ============================================================== Training

    private var mergedSetsRange: Pair<String, String>? = null
    private var mergedSetsCache: List<WorkoutSet> = emptyList()

    /**
     * Uses [WorkoutRepository.loadWorkoutForDateDisplay] per day so `targetMuscles` from the active
     * preset is merged even when Room rows still hold an empty CSV (until Save Workout).
     *
     * That is one preset resolution per day, so the result is cached against the range:
     * changing the selected muscle or exercise must not re-read the whole window.
     */
    private suspend fun mergedSetsInRange(start: String, end: String): List<WorkoutSet> {
        val key = start to end
        if (mergedSetsRange == key) return mergedSetsCache
        val out = ArrayList<WorkoutSet>()
        for (date in daysIn(start, end)) {
            val merged = workoutRepository.loadWorkoutForDateDisplay(date).getOrNull()
            if (merged != null) out.addAll(merged) else out.addAll(workoutDao.getSetsByDateSync(date))
        }
        mergedSetsRange = key
        mergedSetsCache = out
        return out
    }

    /** Drops the merged-workout memo so a pull-to-refresh re-reads presets. */
    fun invalidateCaches() {
        mergedSetsRange = null
        mergedSetsCache = emptyList()
    }

    suspend fun loadTraining(
        start: String,
        end: String,
        preferredMuscle: String?,
        preferredExercise: String?,
    ): TrainingData {
        val allSets = mergedSetsInRange(start, end)
        val resistance = allSets.filter { TrainingVolumeMath.isResistance(it) }
        // Muscle volume only from resistance sets that actually declare target tags.
        val resistanceWithMuscles = resistance.filter {
            TrainingVolumeMath.orderedTargetTags(it.targetMuscles).isNotEmpty()
        }

        val weekStarts = AnalysisStats.weekStartsMondayInclusive(
            AnalysisStats.parseDate(start),
            AnalysisStats.parseDate(end),
        )
        val weeks = weekStarts.map { AnalysisStats.formatDate(it) }

        val muscleNames = resistanceWithMuscles
            .flatMap { TrainingVolumeMath.orderedTargetTags(it.targetMuscles) }
            .distinct()
        val ranking = muscleNames
            .map { muscle ->
                MuscleVolume(
                    name = muscle,
                    setEquivalents = resistanceWithMuscles.sumOf {
                        TrainingVolumeMath.fractionalLoadForMuscle(it, muscle)
                    },
                )
            }
            .filter { it.setEquivalents > 0.0 }
            .sortedByDescending { it.setEquivalents }

        val selectedMuscle = preferredMuscle?.takeIf { pick ->
            ranking.any { it.name.equals(pick, ignoreCase = true) }
        } ?: ranking.firstOrNull()?.name

        val muscleWeekly: List<Double?> = if (selectedMuscle == null) {
            weeks.map { null }
        } else {
            val target = selectedMuscle.lowercase(Locale.US)
            weekStarts.map { monday ->
                val sunday = monday.plusDays(6)
                resistanceWithMuscles.sumOf { set ->
                    val d = AnalysisStats.parseDate(set.date)
                    if (d.isBefore(monday) || d.isAfter(sunday)) {
                        0.0
                    } else {
                        TrainingVolumeMath.fractionalLoadForMuscle(set, target)
                    }
                }
            }
        }

        val tonnageWeekly: List<Double?> = weekStarts.map { monday ->
            val sunday = monday.plusDays(6)
            allSets.sumOf { set ->
                val d = AnalysisStats.parseDate(set.date)
                if (d.isBefore(monday) || d.isAfter(sunday)) 0.0 else TrainingVolumeMath.tonnage(set)
            }
        }

        val exerciseCounts = allSets
            .filter { it.exerciseName.isNotBlank() }
            .groupingBy { it.exerciseName }
            .eachCount()
        val exercises = exerciseCounts.keys.sorted()
        val selectedExercise = preferredExercise?.takeIf { it in exerciseCounts }
            ?: exerciseCounts.maxByOrNull { it.value }?.key

        val e1rmByDate = if (selectedExercise == null) {
            emptyMap()
        } else {
            allSets
                .filter { it.exerciseName == selectedExercise }
                .mapNotNull { set ->
                    TrainingVolumeMath.estimatedOneRepMax(set)?.let { set.date to it }
                }
                .groupBy({ it.first }, { it.second })
                .mapValues { (_, values) -> values.max() }
        }
        val e1rmDates = e1rmByDate.keys.sorted()
        val e1rm: List<Double?> = e1rmDates.map { e1rmByDate[it] }

        val rpes = allSets.mapNotNull { it.actualRpe }.filter { it > 0.0 }
        val rirs = allSets.mapNotNull { it.actualRir?.toDouble() }
        val loadDeltas = allSets.mapNotNull { set ->
            val actual = set.actualLoad ?: return@mapNotNull null
            val target = set.targetLoad ?: return@mapNotNull null
            if (target <= 0.0) null else actual - target
        }
        val completed = AnalysisStats.workoutCompletedSetCount(allSets)

        return TrainingData(
            weeks = weeks,
            muscleRanking = ranking,
            selectedMuscle = selectedMuscle,
            muscleWeekly = muscleWeekly,
            muscleStat = AnalysisStats.maxAvgMin(muscleWeekly.filterNotNull()),
            tonnageWeekly = tonnageWeekly,
            totalTonnage = tonnageWeekly.filterNotNull().sum(),
            exercises = exercises,
            selectedExercise = selectedExercise,
            e1rmDates = e1rmDates,
            e1rm = e1rm,
            bestE1rm = e1rm.filterNotNull().maxOrNull(),
            e1rmDelta = AnalysisStats.firstToLastDelta(e1rm),
            avgRpe = AnalysisStats.average(rpes),
            avgRir = AnalysisStats.average(rirs),
            setsCompleted = completed,
            setsLogged = allSets.size,
            completionPercent = if (allSets.isEmpty()) null else 100.0 * completed / allSets.size,
            avgLoadDelta = AnalysisStats.average(loadDeltas),
            sessionDays = allSets.map { it.date }.distinct().size,
        )
    }

    // ================================================================== Body

    suspend fun loadBody(start: String, end: String): BodyData {
        val days = daysIn(start, end)
        val home = homeDao.getEntriesBetweenSync(start, end).associateBy { it.date }

        val weight = days.map { home[it]?.weightKg }
        val bodyFat = days.map { home[it]?.bodyFatPercent }
        val leanMass = days.indices.map { i ->
            val w = weight[i] ?: return@map null
            val bf = bodyFat[i] ?: return@map null
            w * (1.0 - bf / 100.0)
        }

        return BodyData(
            dates = days,
            weight = weight,
            bodyFat = bodyFat,
            leanMass = leanMass,
            weightTrend = AnalysisStats.linearTrend(weight),
            latestWeight = weight.filterNotNull().lastOrNull(),
            latestBodyFat = bodyFat.filterNotNull().lastOrNull(),
            weightDelta = AnalysisStats.firstToLastDelta(weight),
            bodyFatDelta = AnalysisStats.firstToLastDelta(bodyFat),
            leanMassDelta = AnalysisStats.firstToLastDelta(leanMass),
        )
    }

    suspend fun loadSleep(start: String, end: String): SleepData {
        val days = daysIn(start, end)
        val sleep = loadSleepWithPrior(start, end)
        val self = selfDao.getEntriesBetweenSync(start, end).associateBy { it.date }
        val workouts = workoutDao.getSetsBetweenSync(start, end).groupBy { it.date }

        val sleepHours = days.map { d -> actualSleepHoursForDay(sleep, d) }
        val plannedSleep = days.mapNotNull { d -> plannedSleepHoursForDay(sleep, d) }
        val bedtimes = days.mapNotNull { d ->
            SleepMath.parseHhMm(SleepMath.effectiveBedtime(sleep[previousDateString(d)]))
        }
        val wakes = days.mapNotNull { d ->
            SleepMath.parseHhMm(SleepMath.effectiveWakeUp(sleep[previousDateString(d)]))
        }

        val deepH = days.map { d ->
            sleep[previousDateString(d)]?.deepMinutes?.let { it / 60.0 } ?: 0.0
        }
        val lightH = days.map { d ->
            sleep[previousDateString(d)]?.lightMinutes?.let { it / 60.0 } ?: 0.0
        }
        val remH = days.map { d ->
            sleep[previousDateString(d)]?.remMinutes?.let { it / 60.0 } ?: 0.0
        }
        val awakeH = days.map { d ->
            sleep[previousDateString(d)]?.awakeMinutes?.let { it / 60.0 } ?: 0.0
        }

        val efficiency = days.map { d ->
            val night = sleep[previousDateString(d)] ?: return@map null
            val inBed = SleepMath.durationMinutes(
                SleepMath.effectiveBedtime(night),
                SleepMath.effectiveWakeUp(night),
            ) ?: return@map null
            val asleep = SleepMath.totalSleepMinutes(night) ?: return@map null
            if (inBed <= 0) null else 100.0 * asleep / inBed
        }
        val latency = days.map { d ->
            sleep[previousDateString(d)]?.let { SleepMath.latencyMinutes(it)?.toDouble() }
        }

        val remPct = days.map { d ->
            val night = sleep[previousDateString(d)] ?: return@map null
            SleepMath.stagePercent(night.remMinutes, SleepMath.totalSleepMinutes(night))?.toDouble()
        }
        val deepPct = days.map { d ->
            val night = sleep[previousDateString(d)] ?: return@map null
            SleepMath.stagePercent(night.deepMinutes, SleepMath.totalSleepMinutes(night))?.toDouble()
        }
        val mood = days.map { self[it]?.mood?.toDouble() }
        val energy = days.map { self[it]?.energy?.toDouble() }
        val trainingVol = days.map { d ->
            val sets = workouts[d].orEmpty()
            if (sets.isEmpty()) null else sets.sumOf { TrainingVolumeMath.tonnage(it) }
        }

        val correlations = listOfNotNull(
            pairCorr("REM % vs mood", remPct, mood),
            pairCorr("Deep % vs mood", deepPct, mood),
            pairCorr("Hours vs mood", sleepHours, mood),
            pairCorr("Efficiency vs mood", efficiency, mood),
            pairCorr("Latency vs mood", latency, mood),
            pairCorr("Hours vs energy", sleepHours, energy),
            pairCorr("Hours vs training", sleepHours, trainingVol),
            pairCorr("REM % vs energy", remPct, energy),
        ).sortedByDescending { kotlin.math.abs(it.r) }

        return SleepData(
            dates = days,
            sleepHours = sleepHours,
            sleepStat = AnalysisStats.maxAvgMin(sleepHours.filterNotNull()),
            sleepTargetHours = AnalysisStats.average(plannedSleep),
            avgBedtimeMinutes = AnalysisStats.circularMeanMinutes(bedtimes),
            avgWakeMinutes = AnalysisStats.circularMeanMinutes(wakes),
            bedtimeVarianceMinutes = AnalysisStats.circularStdDevMinutes(bedtimes),
            stageSeries = listOf(
                StackedSeries("Deep", 0xFF003366.toInt(), deepH),
                StackedSeries("Light", 0xFF0074D9.toInt(), lightH),
                StackedSeries("REM", 0xFF392E5C.toInt(), remH),
                StackedSeries("Awake", 0xFFFF9800.toInt(), awakeH),
            ),
            efficiency = efficiency,
            latencyMinutes = latency,
            avgEfficiency = AnalysisStats.average(efficiency.filterNotNull()),
            avgLatencyMinutes = AnalysisStats.average(latency.filterNotNull()),
            bedtimeRows = days.map { d ->
                DayTimes(
                    date = d,
                    minutes = listOfNotNull(
                        SleepMath.parseHhMm(SleepMath.effectiveBedtime(sleep[previousDateString(d)]))
                    ),
                )
            }.filter { it.minutes.isNotEmpty() },
            wakeRows = days.map { d ->
                DayTimes(
                    date = d,
                    minutes = listOfNotNull(
                        SleepMath.parseHhMm(SleepMath.effectiveWakeUp(sleep[previousDateString(d)]))
                    ),
                )
            }.filter { it.minutes.isNotEmpty() },
            correlations = correlations,
        )
    }

    private fun pairCorr(label: String, x: List<Double?>, y: List<Double?>): SleepCorrelation? {
        val r = AnalysisStats.pearson(x, y) ?: return null
        return SleepCorrelation(label, r)
    }

    // ================================================================== Self

    /**
     * Affect, feelings and reflection for the range, crossed with the domains most
     * likely to move them: sleep, training and habit adherence.
     */
    suspend fun loadSelf(start: String, end: String): SelfData {
        val days = daysIn(start, end)
        val entries = selfDao.getEntriesBetweenSync(start, end).associateBy { it.date }
        val sleep = loadSleepWithPrior(start, end)
        val trainingDays = workoutDao.getSetsBetweenSync(start, end).map { it.date }.toSet()
        val habitsByDate = habitLogDao.getBetweenSync(start, end).groupBy { it.date }

        val scaleSeries = SelfScale.entries.map { scale ->
            val values = days.map { date -> entries[date]?.let { scale.valueIn(it)?.toDouble() } }
            SelfScaleSeries(
                label = scale.label,
                lowLabel = scale.lowLabel,
                highLabel = scale.highLabel,
                values = values,
                average = AnalysisStats.average(values.filterNotNull()),
                delta = AnalysisStats.halfSplitDelta(values),
                daysRated = values.count { it != null },
            )
        }
        val seriesFor = { scale: SelfScale -> scaleSeries[scale.ordinal].values }

        val mood = seriesFor(SelfScale.MOOD)
        val energy = seriesFor(SelfScale.ENERGY)
        val stress = seriesFor(SelfScale.STRESS)
        val moodValues = mood.filterNotNull()
        val distribution = (1..5).map { level -> moodValues.count { it.toInt() == level } }

        val moodCorrelations = SelfScale.entries
            .filter { it != SelfScale.MOOD }
            .mapNotNull { scale ->
                val values = seriesFor(scale)
                val r = AnalysisStats.pearson(mood, values) ?: return@mapNotNull null
                SelfScaleCorrelation(
                    label = scale.label,
                    r = r,
                    pairedDays = days.indices.count { mood[it] != null && values[it] != null },
                )
            }
            .sortedByDescending { abs(it.r) }

        val goodSleepMoods = mutableListOf<Double>()
        val poorSleepMoods = mutableListOf<Double>()
        val goodSleepRatings = mutableListOf<Double>()
        val poorSleepRatings = mutableListOf<Double>()
        val trainingMoods = mutableListOf<Double>()
        val restMoods = mutableListOf<Double>()
        val dayAfterTrainingMoods = mutableListOf<Double>()
        val highHabitMoods = mutableListOf<Double>()
        val lowHabitMoods = mutableListOf<Double>()

        days.forEachIndexed { i, date ->
            val sleepHours = actualSleepHoursForDay(sleep, date)
            entries[date]?.sleepQuality?.toDouble()?.let { rating ->
                if (sleepHours == null) return@let
                if (sleepHours >= GOOD_SLEEP_HOURS) goodSleepRatings += rating
                else poorSleepRatings += rating
            }

            val m = entries[date]?.mood?.toDouble() ?: return@forEachIndexed

            if (sleepHours != null) {
                if (sleepHours >= GOOD_SLEEP_HOURS) goodSleepMoods += m else poorSleepMoods += m
            }

            if (date in trainingDays) trainingMoods += m else restMoods += m
            if (i > 0 && days[i - 1] in trainingDays) dayAfterTrainingMoods += m

            val habitScores = habitsByDate[date].orEmpty().mapNotNull { AnalysisStats.habitScore(it.status) }
            if (habitScores.isNotEmpty()) {
                val percent = 100.0 * habitScores.sum() / habitScores.size
                if (percent >= HIGH_HABIT_PERCENT) highHabitMoods += m else lowHabitMoods += m
            }
        }

        val logged = days.mapNotNull { entries[it] }.filter { it.hasAnyContent() }
        val (withIntention, withoutIntention) = logged.partition { !it.intention.isNullOrBlank() }
        val reflected = logged.filter { it.hasReflection() }
        val words = reflected.map { it.writtenWordCount().toDouble() }

        val zone = ZoneId.systemDefault()
        val fromMillis = AnalysisStats.parseDate(start).atStartOfDay(zone).toInstant().toEpochMilli()
        val toMillis = AnalysisStats.parseDate(end).plusDays(1).atStartOfDay(zone)
            .toInstant().toEpochMilli()
        val photoCount = collectionImageDao.getAllSync()
            .count { it.takenAtMillis in fromMillis until toMillis }

        return SelfData(
            dates = days,
            scales = scaleSeries,
            moodCorrelations = moodCorrelations,
            mood = mood,
            energy = energy,
            stress = stress,
            moodTrend = AnalysisStats.linearTrend(mood),
            moodAverage = AnalysisStats.average(moodValues),
            energyAverage = AnalysisStats.average(energy.filterNotNull()),
            stressAverage = AnalysisStats.average(stress.filterNotNull()),
            moodDelta = AnalysisStats.halfSplitDelta(mood),
            moodRows = buildMoodRows(start, end, days, entries.mapValues { it.value.mood }),
            moodDistribution = distribution,
            moodOnGoodSleep = AnalysisStats.average(goodSleepMoods),
            moodOnPoorSleep = AnalysisStats.average(poorSleepMoods),
            goodSleepThresholdHours = GOOD_SLEEP_HOURS,
            sleepRatingOnGoodSleep = AnalysisStats.average(goodSleepRatings),
            sleepRatingOnPoorSleep = AnalysisStats.average(poorSleepRatings),
            moodOnTrainingDays = AnalysisStats.average(trainingMoods),
            moodOnRestDays = AnalysisStats.average(restMoods),
            moodDayAfterTraining = AnalysisStats.average(dayAfterTrainingMoods),
            moodOnHighHabitDays = AnalysisStats.average(highHabitMoods),
            moodOnLowHabitDays = AnalysisStats.average(lowHabitMoods),
            habitThresholdPercent = HIGH_HABIT_PERCENT,
            intentionDays = withIntention.size,
            moodWithIntention = AnalysisStats.average(withIntention.mapNotNull { it.mood?.toDouble() }),
            moodWithoutIntention = AnalysisStats.average(
                withoutIntention.mapNotNull { it.mood?.toDouble() }
            ),
            loggedDays = logged.size,
            journalDays = reflected.size,
            highlightDays = logged.count { !it.highlight.isNullOrBlank() },
            lowlightDays = logged.count { !it.lowlight.isNullOrBlank() },
            gratitudeDays = logged.count { !it.gratitude.isNullOrBlank() },
            avgWordsPerJournalDay = AnalysisStats.average(words),
            journalRateOnLowMoodDays = reflectionRate(logged.filter { (it.mood ?: 0) in 1..2 }),
            journalRateOnHighMoodDays = reflectionRate(logged.filter { (it.mood ?: 0) in 4..5 }),
            photoCount = photoCount,
        )
    }

    private fun reflectionRate(entries: List<SelfEntry>): Double? {
        if (entries.isEmpty()) return null
        return 100.0 * entries.count { it.hasReflection() } / entries.size
    }

    private fun buildMoodRows(
        start: String,
        end: String,
        days: List<String>,
        moodByDate: Map<String, Int?>,
    ): List<MoodGridRow> {
        if (days.isEmpty()) return emptyList()
        val rangeStart = AnalysisStats.parseDate(start)
        val rangeEnd = AnalysisStats.parseDate(end)
        return AnalysisStats.weekStartsMondayInclusive(rangeStart, rangeEnd).map { monday ->
            val cells = (0 until 7).map { offset ->
                val d = monday.plusDays(offset.toLong())
                if (d.isBefore(rangeStart) || d.isAfter(rangeEnd)) {
                    null
                } else {
                    moodByDate[AnalysisStats.formatDate(d)]
                }
            }
            MoodGridRow(AnalysisStats.formatDate(monday), cells)
        }
    }

    // =============================================================== helpers

    private fun daysIn(start: String, end: String): List<String> =
        AnalysisStats.eachDayInclusive(AnalysisStats.parseDate(start), AnalysisStats.parseDate(end))

    /** Includes the night before [start] so day D can read sleep_entry(D−1). */
    private suspend fun loadSleepWithPrior(
        start: String,
        end: String,
    ): Map<String, SleepEntry> {
        val prior = AnalysisStats.formatDate(AnalysisStats.parseDate(start).minusDays(1))
        return sleepDao.getBetweenSync(prior, end).associateBy { it.date }
    }

    private fun previousDateString(date: String): String =
        AnalysisStats.formatDate(AnalysisStats.parseDate(date).minusDays(1))

    /**
     * Sleep hours for calendar day D: night keyed D−1.
     * Prefers actual bedtime, requires actual wake.
     */
    private fun actualSleepHoursForDay(
        sleepByDate: Map<String, SleepEntry>,
        date: String,
    ): Double? {
        val night = sleepByDate[previousDateString(date)] ?: return null
        val wake = night.actualWakeUp ?: return null
        val bed = SleepMath.effectiveBedtime(night) ?: return null
        return SleepMath.durationHours(bed, wake)
    }

    private fun plannedSleepHoursForDay(
        sleepByDate: Map<String, SleepEntry>,
        date: String,
    ): Double? {
        val night = sleepByDate[previousDateString(date)] ?: return null
        val wake = night.plannedWakeUp ?: return null
        val bed = night.plannedBedtime ?: return null
        return SleepMath.durationHours(bed, wake)
    }

    private companion object {
        const val GOOD_SLEEP_HOURS = 7.0
        const val HIGH_HABIT_PERCENT = 80.0
        val UNLABELLED_COLOR = 0xFF90A4AE.toInt()
        val ADHERENCE_PLANNED_COLOR = 0xFF0074D9.toInt()
        val ADHERENCE_ADJUSTED_COLOR = 0xFF003366.toInt()
        val ADHERENCE_UNPLANNED_COLOR = 0xFFFF9800.toInt()
        val ADHERENCE_SKIPPED_COLOR = 0xFFD32F2F.toInt()
    }
}
