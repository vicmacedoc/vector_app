package com.vm.vector.data.analysis

import com.vm.core.models.HabitStatus

data class TripleStat(val high: Double, val avg: Double, val low: Double)

/** One coloured band of a stacked bar chart; [values] is aligned to the chart's label list. */
data class StackedSeries(
    val name: String,
    val colorArgb: Int,
    val values: List<Double>,
)

// ---------------------------------------------------------------- Overview

/** Daily completion (0..100, null = nothing logged) for one tracked domain. */
data class DomainSeries(
    val name: String,
    val values: List<Double?>,
    val daysWithData: Int,
    val currentStreak: Int,
    val bestStreak: Int,
    val average: Double?,
    /** Recent-half average minus previous-half average, in percentage points. */
    val delta: Double?,
)

data class OverviewData(
    val dates: List<String>,
    val domains: List<DomainSeries>,
    /** Composite of the domain values for each day. */
    val score: List<Double?>,
    val scoreAverage: Double?,
    val scoreDelta: Double?,
    val daysLogged: Int,
)

// ---------------------------------------------------------------- Routine

/** Average minutes per day attributed to one activity label. */
data class LabelMinutes(
    val name: String,
    val colorArgb: Int,
    val plannedMinutes: Double,
    val doneMinutes: Double,
)

data class HabitRow(
    val name: String,
    /** Aligned to the dashboard date list; null = not scheduled that day. */
    val cells: List<HabitStatus?>,
    val scheduledDays: Int,
    /** Share of scheduled days scored as done, counting PARTIAL as a half. */
    val completionRate: Double?,
    val currentStreak: Int,
    val bestStreak: Int,
)

data class RoutineData(
    val dates: List<String>,
    val labels: List<LabelMinutes>,
    val avgPlannedHours: Double,
    val avgDoneHours: Double,
    val planCompletionPercent: Int,
    val weeks: List<String>,
    val weeklyLabelHours: List<StackedSeries>,
    val habits: List<HabitRow>,
    val habitOverallRate: Double?,
    val activitiesLogged: Int,
    val adjustedPercent: Double?,
    /** Mean absolute minutes between planned and effective start time. */
    val avgDriftMinutes: Double?,
)

// ---------------------------------------------------------------- Food

/** Minutes-of-day at which food was eaten on one date. */
data class DayTimes(val date: String, val minutes: List<Int>)

data class FoodData(
    val dates: List<String>,
    val kcal: List<Double?>,
    val kcalRolling: List<Double?>,
    val kcalStat: TripleStat?,
    val proteinStat: TripleStat?,
    val avgKcal: Double,
    val avgProteinG: Double,
    val avgCarbsG: Double,
    val avgFatsG: Double,
    val proteinKcalShare: Double,
    val carbsKcalShare: Double,
    val fatsKcalShare: Double,
    val weeks: List<String>,
    val adherence: List<StackedSeries>,
    val compliancePercent: Double?,
    val mealTimes: List<DayTimes>,
    val avgFirstMealMinutes: Int?,
    val avgLastMealMinutes: Int?,
    val avgEatingWindowHours: Double?,
    val daysLogged: Int,
)

/** Calendar Food day strip: eaten (checked) vs planned baseline. */
data class FoodDayMacroTotals(
    val gotKcal: Double,
    val gotProtein: Double,
    val gotCarbs: Double,
    val gotFats: Double,
    val plannedKcal: Double,
    val plannedProtein: Double,
    val plannedCarbs: Double,
    val plannedFats: Double,
)

// ---------------------------------------------------------------- Training

data class MuscleVolume(val name: String, val setEquivalents: Double)

data class TrainingData(
    val weeks: List<String>,
    val muscleRanking: List<MuscleVolume>,
    val selectedMuscle: String?,
    val muscleWeekly: List<Double?>,
    val muscleStat: TripleStat?,
    val tonnageWeekly: List<Double?>,
    val totalTonnage: Double,
    val exercises: List<String>,
    val selectedExercise: String?,
    /** Session dates on which the selected exercise was performed. */
    val e1rmDates: List<String>,
    val e1rm: List<Double?>,
    val bestE1rm: Double?,
    val e1rmDelta: Double?,
    val avgRpe: Double?,
    val avgRir: Double?,
    val setsCompleted: Int,
    val setsLogged: Int,
    val completionPercent: Double?,
    /** Mean actual load minus target load across sets that define both. */
    val avgLoadDelta: Double?,
    val sessionDays: Int,
)

// ---------------------------------------------------------------- Body

data class MoodGridRow(
    val mondayIso: String,
    /** Seven entries Mon..Sun; null = out of range or no mood logged. */
    val cells: List<Int?>,
)

data class BodyData(
    val dates: List<String>,
    val weight: List<Double?>,
    val bodyFat: List<Double?>,
    val leanMass: List<Double?>,
    val weightTrend: List<Double?>,
    val latestWeight: Double?,
    val latestBodyFat: Double?,
    val weightDelta: Double?,
    val bodyFatDelta: Double?,
    val leanMassDelta: Double?,
)

// ---------------------------------------------------------------- Self

/** One 1–5 scale over the range, aligned to [SelfData.dates]. */
data class SelfScaleSeries(
    val label: String,
    val lowLabel: String,
    val highLabel: String,
    val values: List<Double?>,
    val average: Double?,
    /** Recent-half average minus previous-half average. */
    val delta: Double?,
    val daysRated: Int,
)

/** How closely one scale moved with mood over the range. */
data class SelfScaleCorrelation(
    val label: String,
    /** Pearson r in −1..1 across the days both were rated. */
    val r: Double,
    val pairedDays: Int,
)

data class SelfData(
    val dates: List<String>,
    val scales: List<SelfScaleSeries>,
    /** Scales ranked by how strongly they track mood, strongest first. */
    val moodCorrelations: List<SelfScaleCorrelation>,
    val mood: List<Double?>,
    val energy: List<Double?>,
    val stress: List<Double?>,
    val moodTrend: List<Double?>,
    val moodAverage: Double?,
    val energyAverage: Double?,
    val stressAverage: Double?,
    /** Recent-half average minus previous-half average. */
    val moodDelta: Double?,
    val moodRows: List<MoodGridRow>,
    /** Five buckets counting how often each mood level was logged. */
    val moodDistribution: List<Int>,
    /** Mood the morning after a long night versus a short one. */
    val moodOnGoodSleep: Double?,
    val moodOnPoorSleep: Double?,
    val goodSleepThresholdHours: Double,
    /** Rated sleep quality against the measured duration of the same night. */
    val sleepRatingOnGoodSleep: Double?,
    val sleepRatingOnPoorSleep: Double?,
    val moodOnTrainingDays: Double?,
    val moodOnRestDays: Double?,
    /** Mood the day after training, where recovery or soreness shows up. */
    val moodDayAfterTraining: Double?,
    val moodOnHighHabitDays: Double?,
    val moodOnLowHabitDays: Double?,
    val habitThresholdPercent: Double,
    val intentionDays: Int,
    val moodWithIntention: Double?,
    val moodWithoutIntention: Double?,
    val loggedDays: Int,
    val journalDays: Int,
    val highlightDays: Int,
    val lowlightDays: Int,
    val gratitudeDays: Int,
    val avgWordsPerJournalDay: Double?,
    /** Share of low-mood (1–2) days that got a reflection; the avoidance check. */
    val journalRateOnLowMoodDays: Double?,
    /** Share of high-mood (4–5) days that got a reflection. */
    val journalRateOnHighMoodDays: Double?,
    val photoCount: Int,
)

data class SleepCorrelation(
    val label: String,
    val r: Double,
)

data class SleepData(
    val dates: List<String>,
    val sleepHours: List<Double?>,
    val sleepStat: TripleStat?,
    val sleepTargetHours: Double?,
    val avgBedtimeMinutes: Int?,
    val avgWakeMinutes: Int?,
    val bedtimeVarianceMinutes: Double?,
    val stageSeries: List<StackedSeries>,
    val efficiency: List<Double?>,
    val latencyMinutes: List<Double?>,
    val avgEfficiency: Double?,
    val avgLatencyMinutes: Double?,
    val bedtimeRows: List<DayTimes>,
    val wakeRows: List<DayTimes>,
    val correlations: List<SleepCorrelation>,
)

