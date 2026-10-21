package com.vm.vector.ui.analysis.dashboards

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vm.vector.data.analysis.DomainSeries
import com.vm.vector.data.analysis.OverviewData
import com.vm.vector.ui.analysis.AnalysisDeltaPill
import com.vm.vector.ui.analysis.AnalysisEmptyState
import com.vm.vector.ui.analysis.AnalysisSection
import com.vm.vector.ui.analysis.AnalysisStatRow
import com.vm.vector.ui.analysis.AnalysisTheme
import com.vm.vector.ui.analysis.formatPercentOrDash
import com.vm.vector.ui.analysis.charts.AnalysisDonutChart
import com.vm.vector.ui.analysis.charts.AnalysisHeatmap
import com.vm.vector.ui.analysis.charts.AnalysisLineChart
import com.vm.vector.ui.analysis.charts.DonutSlice
import com.vm.vector.ui.analysis.charts.HeatmapRow
import com.vm.vector.ui.analysis.charts.LineLayer
import com.vm.vector.ui.analysis.charts.shortDate

@Composable
fun OverviewDashboard(data: OverviewData, modifier: Modifier = Modifier) {
    if (data.daysLogged == 0) {
        AnalysisEmptyState("Nothing logged in this range yet.", modifier)
        return
    }

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {

        AnalysisSection(
            title = "Vector score",
            trailing = "${data.daysLogged} / ${data.dates.size} days",
            subtitle = "Average completion across Routine, Food, Exercise and Self."
        ) {
            val score = data.scoreAverage ?: 0.0
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                AnalysisDonutChart(
                    slices = listOf(
                        DonutSlice("Score", score, AnalysisTheme.AccentLine),
                        DonutSlice("Remaining", (100.0 - score).coerceAtLeast(0.0), AnalysisTheme.Empty),
                    ),
                    centerValue = "${score.toInt()}%",
                    centerCaption = "period avg"
                )
            }
            if (data.scoreDelta != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "vs first half of range",
                        color = AnalysisTheme.TextSecondary,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(end = 8.dp)
                    )
                    AnalysisDeltaPill(data.scoreDelta, "pt", higherIsBetter = true)
                }
            }
            AnalysisLineChart(
                dates = data.dates,
                layers = listOf(
                    LineLayer("Score", data.score, AnalysisTheme.AccentLine, filled = true)
                ),
                yLabel = "% complete",
                modifier = Modifier.padding(top = 12.dp)
            )
        }

        AnalysisSection(
            title = "Consistency",
            subtitle = "One column per day; darker means a higher share completed."
        ) {
            AnalysisHeatmap(
                rows = data.domains.map { domain ->
                    HeatmapRow(
                        label = domain.name,
                        cells = domain.values.map { v ->
                            if (v == null) AnalysisTheme.Empty else AnalysisTheme.heat(v / 100.0)
                        },
                        trailing = formatPercentOrDash(domain.average)
                    )
                },
                startCaption = data.dates.firstOrNull()?.let(::shortDate),
                endCaption = data.dates.lastOrNull()?.let(::shortDate)
            )
        }

        AnalysisSection(
            title = "Streaks",
            subtitle = "Consecutive days with something logged in each domain."
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                data.domains.forEach { StreakRow(it) }
            }
        }

        AnalysisSection(title = "Momentum", subtitle = "Second half of the range versus the first.") {
            AnalysisStatRow(
                stats = data.domains.map { domain ->
                    domain.name to (domain.delta?.let { formatSignedPoints(it) } ?: "—")
                }
            )
        }
    }
}

@Composable
private fun StreakRow(domain: DomainSeries) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = domain.name,
            color = AnalysisTheme.TextPrimary,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f)
        )
        StreakCell("Now", domain.currentStreak)
        StreakCell("Best", domain.bestStreak)
        StreakCell("Days", domain.daysWithData)
    }
}

@Composable
private fun StreakCell(label: String, value: Int) {
    Column(
        horizontalAlignment = Alignment.End,
        modifier = Modifier.padding(start = 16.dp)
    ) {
        Text(text = label, color = AnalysisTheme.TextSecondary, fontSize = 9.sp)
        Text(
            text = value.toString(),
            color = AnalysisTheme.TextPrimary,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.SemiBold,
            fontSize = 14.sp
        )
    }
}

private fun formatSignedPoints(value: Double): String {
    val sign = if (value >= 0) "+" else "−"
    return "$sign${kotlin.math.abs(value).toInt()}pt"
}
