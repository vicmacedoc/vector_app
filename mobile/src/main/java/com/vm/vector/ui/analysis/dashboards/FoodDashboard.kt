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
import com.vm.vector.data.analysis.FoodData
import com.vm.vector.ui.analysis.AnalysisEmptyState
import com.vm.vector.ui.analysis.AnalysisLegendRow
import com.vm.vector.ui.analysis.AnalysisSection
import com.vm.vector.ui.analysis.AnalysisStatRow
import com.vm.vector.ui.analysis.AnalysisTheme
import com.vm.vector.ui.analysis.formatPercentOrDash
import com.vm.vector.ui.analysis.formatStat
import com.vm.vector.ui.analysis.charts.AnalysisBarChart
import com.vm.vector.ui.analysis.charts.AnalysisDonutChart
import com.vm.vector.ui.analysis.charts.AnalysisDotStrip
import com.vm.vector.ui.analysis.charts.AnalysisStackedBarChart
import com.vm.vector.ui.analysis.charts.DonutSlice
import com.vm.vector.ui.analysis.charts.DotStripRow
import com.vm.vector.ui.analysis.charts.StackSegment
import com.vm.vector.ui.analysis.charts.shortDate
import com.vm.vector.data.analysis.AnalysisStats

/** Most recent days shown in the eating-window strip; older lanes add noise, not insight. */
private const val TIMING_LANES = 21

private val ProteinColor = Color(0xFF0074D9)
private val CarbsColor = Color(0xFFFF9800)
private val FatsColor = Color(0xFF003366)

@Composable
fun FoodDashboard(data: FoodData, modifier: Modifier = Modifier) {
    if (data.daysLogged == 0) {
        AnalysisEmptyState("No food logged in this range.", modifier)
        return
    }

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {

        AnalysisSection(
            title = "Macro split",
            trailing = "${formatStat(data.avgKcal)} kcal / day",
            subtitle = "Share of calories from each macro, averaged over logged days."
        ) {
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                AnalysisDonutChart(
                    slices = listOf(
                        DonutSlice("Protein", data.proteinKcalShare, ProteinColor),
                        DonutSlice("Carbs", data.carbsKcalShare, CarbsColor),
                        DonutSlice("Fat", data.fatsKcalShare, FatsColor),
                    ),
                    centerValue = "${data.avgKcal.toInt()}",
                    centerCaption = "kcal / day"
                )
            }
            AnalysisLegendRow(
                entries = listOf(
                    "Protein ${data.proteinKcalShare.toInt()}%" to ProteinColor,
                    "Carbs ${data.carbsKcalShare.toInt()}%" to CarbsColor,
                    "Fat ${data.fatsKcalShare.toInt()}%" to FatsColor,
                ),
                modifier = Modifier.padding(top = 12.dp)
            )
            AnalysisStatRow(
                stats = listOf(
                    "Protein" to "${formatStat(data.avgProteinG)}g",
                    "Carbs" to "${formatStat(data.avgCarbsG)}g",
                    "Fat" to "${formatStat(data.avgFatsG)}g",
                ),
                modifier = Modifier.padding(top = 14.dp)
            )
        }

        AnalysisSection(
            title = "Calories",
            subtitle = "Daily intake with a 7-day rolling average."
        ) {
            AnalysisBarChart(
                labels = data.dates,
                values = data.kcal,
                yLabel = "kcal",
                overlay = data.kcalRolling
            )
            AnalysisLegendRow(
                entries = listOf(
                    "Daily" to AnalysisTheme.AccentLine,
                    "7-day average" to AnalysisTheme.NavyAccent,
                ),
                modifier = Modifier.padding(top = 10.dp)
            )
            AnalysisStatRow(
                stats = listOf(
                    "Highest" to (data.kcalStat?.let { formatStat(it.high) } ?: "—"),
                    "Average" to (data.kcalStat?.let { formatStat(it.avg) } ?: "—"),
                    "Lowest" to (data.kcalStat?.let { formatStat(it.low) } ?: "—"),
                ),
                modifier = Modifier.padding(top = 14.dp)
            )
        }

        if (data.adherence.isNotEmpty() && data.weeks.isNotEmpty()) {
            AnalysisSection(
                title = "Plan adherence",
                trailing = formatPercentOrDash(data.compliancePercent),
                subtitle = "Food items each week by how they matched the plan."
            ) {
                AnalysisStackedBarChart(
                    labels = data.weeks,
                    segments = data.adherence.map {
                        StackSegment(it.name, Color(it.colorArgb), it.values)
                    },
                    yLabel = "items"
                )
                AnalysisLegendRow(
                    entries = data.adherence.map { it.name to Color(it.colorArgb) },
                    modifier = Modifier.padding(top = 10.dp)
                )
            }
        }

        if (data.mealTimes.isNotEmpty()) {
            AnalysisSection(
                title = "Eating window",
                trailing = data.avgEatingWindowHours?.let { "${formatStat(it)}h" },
                subtitle = "Every meal you ate, placed at the time you ate it."
            ) {
                AnalysisDotStrip(
                    rows = data.mealTimes.takeLast(TIMING_LANES).map {
                        DotStripRow(shortDate(it.date), it.minutes)
                    }
                )
                AnalysisStatRow(
                    stats = listOf(
                        "First meal" to clock(data.avgFirstMealMinutes),
                        "Last meal" to clock(data.avgLastMealMinutes),
                        "Window" to (data.avgEatingWindowHours?.let { "${formatStat(it)}h" } ?: "—"),
                    ),
                    modifier = Modifier.padding(top = 14.dp)
                )
                if (data.mealTimes.size > TIMING_LANES) {
                    Text(
                        text = "Showing the most recent $TIMING_LANES logged days.",
                        color = AnalysisTheme.TextSecondary,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
        }
    }
}

private fun clock(minutes: Int?): String =
    minutes?.let { AnalysisStats.formatMinutesAsClock(it) } ?: "—"
