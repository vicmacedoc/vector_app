package com.vm.vector.ui.analysis.dashboards

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.vm.vector.data.analysis.AnalysisStats
import com.vm.vector.data.analysis.SleepData
import com.vm.vector.ui.analysis.AnalysisEmptyState
import com.vm.vector.ui.analysis.AnalysisLegendRow
import com.vm.vector.ui.analysis.AnalysisSection
import com.vm.vector.ui.analysis.AnalysisStatRow
import com.vm.vector.ui.analysis.AnalysisTheme
import com.vm.vector.ui.analysis.formatStat
import com.vm.vector.ui.analysis.charts.AnalysisBarChart
import com.vm.vector.ui.analysis.charts.AnalysisDotStrip
import com.vm.vector.ui.analysis.charts.AnalysisLineChart
import com.vm.vector.ui.analysis.charts.AnalysisRankBars
import com.vm.vector.ui.analysis.charts.AnalysisStackedBarChart
import com.vm.vector.ui.analysis.charts.DotStripRow
import com.vm.vector.ui.analysis.charts.LineLayer
import com.vm.vector.ui.analysis.charts.RankBarItem
import com.vm.vector.ui.analysis.charts.StackSegment
import com.vm.vector.ui.analysis.charts.shortDate
import kotlin.math.abs

@Composable
fun SleepDashboard(data: SleepData, modifier: Modifier = Modifier) {
    val hasHours = data.sleepHours.any { it != null }
    val hasStages = data.stageSeries.any { series -> series.values.any { it > 0.0 } }
    if (!hasHours && !hasStages) {
        AnalysisEmptyState("No sleep data in this range.", modifier)
        return
    }

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (hasHours) {
            AnalysisSection(
                title = "Duration",
                trailing = data.sleepStat?.let { "${formatStat(it.avg)}h avg" },
                subtitle = "Nightly sleep against your planned window. Attributed to the morning it ended."
            ) {
                AnalysisBarChart(
                    labels = data.dates,
                    values = data.sleepHours,
                    yLabel = "hours",
                    targetValue = data.sleepTargetHours
                )
                AnalysisStatRow(
                    stats = listOf(
                        "Avg bedtime" to clock(data.avgBedtimeMinutes),
                        "Avg wake" to clock(data.avgWakeMinutes),
                        "Bedtime spread" to (
                            data.bedtimeVarianceMinutes?.let { "±${formatStat(it)}m" } ?: "—"
                            ),
                    ),
                    modifier = Modifier.padding(top = 14.dp)
                )
            }
        }

        if (hasStages) {
            AnalysisSection(
                title = "Stages",
                subtitle = "Hours in deep, light, REM and awake across the night."
            ) {
                AnalysisStackedBarChart(
                    labels = data.dates,
                    segments = data.stageSeries.map {
                        StackSegment(it.name, Color(it.colorArgb), it.values)
                    },
                    yLabel = "hours"
                )
                AnalysisLegendRow(
                    entries = data.stageSeries.map { it.name to Color(it.colorArgb) },
                    modifier = Modifier.padding(top = 10.dp)
                )
            }
        }

        if (data.efficiency.any { it != null } || data.latencyMinutes.any { it != null }) {
            AnalysisSection(
                title = "Efficiency and latency",
                subtitle = "Asleep share of time in bed, and minutes to fall asleep."
            ) {
                AnalysisLineChart(
                    dates = data.dates,
                    layers = listOfNotNull(
                        LineLayer("Efficiency %", data.efficiency, AnalysisTheme.AccentLine, filled = true),
                        LineLayer("Latency min", data.latencyMinutes, AnalysisTheme.NavyAccent),
                    ),
                    yLabel = "% / min",
                    modifier = Modifier.padding(top = 8.dp)
                )
                AnalysisStatRow(
                    stats = listOf(
                        "Avg efficiency" to (data.avgEfficiency?.let { "${formatStat(it)}%" } ?: "—"),
                        "Avg latency" to (data.avgLatencyMinutes?.let { "${formatStat(it)}m" } ?: "—"),
                    ),
                    modifier = Modifier.padding(top = 14.dp)
                )
            }
        }

        if (data.bedtimeRows.isNotEmpty() || data.wakeRows.isNotEmpty()) {
            AnalysisSection(
                title = "Consistency",
                subtitle = "Bedtimes and wakes on the 24h clock."
            ) {
                if (data.bedtimeRows.isNotEmpty()) {
                    AnalysisDotStrip(
                        rows = data.bedtimeRows.map { row ->
                            DotStripRow(
                                shortDate(row.date),
                                row.minutes.map { if (it < 12 * 60) it + 24 * 60 else it },
                            )
                        },
                        startMinute = 18 * 60,
                        endMinute = 30 * 60,
                    )
                }
                if (data.wakeRows.isNotEmpty()) {
                    AnalysisDotStrip(
                        rows = data.wakeRows.map {
                            DotStripRow(shortDate(it.date), it.minutes)
                        },
                        startMinute = 4 * 60,
                        endMinute = 12 * 60,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
        }

        if (data.correlations.isNotEmpty()) {
            AnalysisSection(
                title = "Correlations",
                subtitle = "Pearson r against mood, energy and training volume."
            ) {
                AnalysisRankBars(
                    items = data.correlations.map {
                        RankBarItem(
                            label = it.label,
                            value = abs(it.r),
                            valueLabel = formatStat(it.r),
                        )
                    }
                )
            }
        }
    }
}

private fun clock(minutes: Int?): String =
    minutes?.let { AnalysisStats.formatMinutesAsClock(it) } ?: "—"
