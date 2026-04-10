package com.vm.vector.data.analysis

import com.vm.core.models.RoutineStatus
import com.vm.core.models.RoutineType

data class LineSeries(
    val dates: List<String>,
    /** Same length as dates; null = N/A (gap in line). */
    val yValues: List<Double?>,
    val yLabel: String,
)

data class TripleStat(val high: Double, val avg: Double, val low: Double)

data class CountMaxStreak(
    val countWithData: Int,
    val maxValue: Double,
    val longestStreak: Int,
)

data class RoutineHabitKey(
    val category: String,
    val title: String,
    val type: RoutineType,
) {
    val label: String get() = "$category — $title"
}

data class HabitStatusBreakdown(
    val counts: Map<RoutineStatus, Int>,
    val numericalLine: LineSeries?,
    val tripleStat: TripleStat?,
)

data class WeekVolumePoint(
    val weekMondayIso: String,
    /** Fractional set-equivalents (primary 1.0×, secondary 0.5× per completed set). */
    val volumeLoad: Double,
)
