package com.vm.vector.ui.analysis.dashboards

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vm.core.models.HabitStatus
import com.vm.vector.data.analysis.RoutineData
import com.vm.vector.ui.analysis.AnalysisEmptyState
import com.vm.vector.ui.analysis.AnalysisLegendRow
import com.vm.vector.ui.analysis.AnalysisSection
import com.vm.vector.ui.analysis.AnalysisStatRow
import com.vm.vector.ui.analysis.AnalysisTheme
import com.vm.vector.ui.analysis.formatPercentOrDash
import com.vm.vector.ui.analysis.formatStat
import com.vm.vector.ui.analysis.charts.AnalysisDualRingDonut
import com.vm.vector.ui.analysis.charts.AnalysisHeatmap
import com.vm.vector.ui.analysis.charts.AnalysisStackedBarChart
import com.vm.vector.ui.analysis.charts.DonutSlice
import com.vm.vector.ui.analysis.charts.HeatmapRow
import com.vm.vector.ui.analysis.charts.StackSegment
import com.vm.vector.ui.analysis.charts.shortDate
import com.vm.vector.ui.screens.routine.habitStatusColor

private const val HOURS_PER_DAY = 24.0

@Composable
fun RoutineDashboard(data: RoutineData, modifier: Modifier = Modifier) {
    if (data.labels.isEmpty() && data.habits.isEmpty()) {
        AnalysisEmptyState("No habits or activities logged in this range.", modifier)
        return
    }

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {

        if (data.labels.isNotEmpty()) {
            AnalysisSection(
                title = "Where the day goes",
                trailing = "${data.planCompletionPercent}% of plan",
                subtitle = "Average day. Inner ring is planned, outer ring is done."
            ) {
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    AnalysisDualRingDonut(
                        innerSlices = data.labels.map {
                            DonutSlice(it.name, it.plannedMinutes, Color(it.colorArgb))
                        },
                        outerSlices = data.labels.map {
                            DonutSlice(it.name, it.doneMinutes, Color(it.colorArgb))
                        },
                        centerValue = "${formatStat(data.avgDoneHours)}h",
                        centerCaption = "done / day",
                        totalValue = HOURS_PER_DAY * 60
                    )
                }
                AnalysisLegendRow(
                    entries = data.labels.map { it.name to Color(it.colorArgb) },
                    modifier = Modifier.padding(top = 12.dp)
                )
                AnalysisStatRow(
                    stats = listOf(
                        "Planned / day" to "${formatStat(data.avgPlannedHours)}h",
                        "Done / day" to "${formatStat(data.avgDoneHours)}h",
                        "Unlogged" to "${formatStat(
                            (HOURS_PER_DAY - data.avgDoneHours).coerceAtLeast(0.0)
                        )}h",
                    ),
                    modifier = Modifier.padding(top = 14.dp)
                )
            }
        }

        if (data.weeklyLabelHours.isNotEmpty() && data.weeks.isNotEmpty()) {
            AnalysisSection(
                title = "Weekly allocation",
                subtitle = "Hours completed each week, split by activity label."
            ) {
                AnalysisStackedBarChart(
                    labels = data.weeks,
                    segments = data.weeklyLabelHours.map {
                        StackSegment(it.name, Color(it.colorArgb), it.values)
                    },
                    yLabel = "hours"
                )
                AnalysisLegendRow(
                    entries = data.weeklyLabelHours.map { it.name to Color(it.colorArgb) },
                    modifier = Modifier.padding(top = 10.dp)
                )
            }
        }

        if (data.habits.isNotEmpty()) {
            AnalysisSection(
                title = "Habits",
                trailing = formatPercentOrDash(data.habitOverallRate),
                subtitle = "One column per day. A gap means the habit was not scheduled."
            ) {
                AnalysisHeatmap(
                    rows = data.habits.map { habit ->
                        HeatmapRow(
                            label = habit.name,
                            cells = habit.cells.map(::heatmapStatusColor),
                            trailing = formatPercentOrDash(habit.completionRate)
                        )
                    },
                    labelWidth = 78.dp,
                    startCaption = data.dates.firstOrNull()?.let(::shortDate),
                    endCaption = data.dates.lastOrNull()?.let(::shortDate)
                )
                AnalysisLegendRow(
                    entries = listOf(
                        "Done" to habitStatusColor(HabitStatus.DONE),
                        "Exceeded" to habitStatusColor(HabitStatus.EXCEEDED),
                        "Partial" to habitStatusColor(HabitStatus.PARTIAL),
                        "Not done" to habitStatusColor(HabitStatus.NOT_DONE),
                        "N/A" to habitStatusColor(HabitStatus.NA),
                        "Unanswered" to AnalysisTheme.Empty,
                    ),
                    modifier = Modifier.padding(top = 12.dp)
                )
                val best = data.habits.maxByOrNull { it.currentStreak }
                if (best != null && best.currentStreak > 0) {
                    Text(
                        text = "Longest live streak: ${best.name} at ${best.currentStreak} days.",
                        color = AnalysisTheme.TextSecondary,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(top = 10.dp)
                    )
                }
            }
        }

        AnalysisSection(
            title = "Execution",
            subtitle = "How closely activities ran to the time they were planned for."
        ) {
            AnalysisStatRow(
                stats = listOf(
                    "Activities" to data.activitiesLogged.toString(),
                    "Adjusted" to formatPercentOrDash(data.adjustedPercent),
                    "Start drift" to (
                        data.avgDriftMinutes?.let { "${formatStat(it)}m" } ?: "—"
                        ),
                )
            )
        }
    }
}

/** Days the habit was not scheduled read as gaps; scheduled-but-unanswered stays visible. */
private fun heatmapStatusColor(status: HabitStatus?): Color = when (status) {
    null -> Color.Transparent
    HabitStatus.NONE -> AnalysisTheme.Empty
    else -> habitStatusColor(status)
}
