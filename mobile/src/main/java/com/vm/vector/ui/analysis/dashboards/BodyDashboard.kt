package com.vm.vector.ui.analysis.dashboards

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.vm.vector.data.analysis.BodyData
import com.vm.vector.ui.analysis.AnalysisEmptyState
import com.vm.vector.ui.analysis.AnalysisHeroStat
import com.vm.vector.ui.analysis.AnalysisLegendRow
import com.vm.vector.ui.analysis.AnalysisSection
import com.vm.vector.ui.analysis.AnalysisStatRow
import com.vm.vector.ui.analysis.AnalysisTheme
import com.vm.vector.ui.analysis.formatStat
import com.vm.vector.ui.analysis.charts.AnalysisLineChart
import com.vm.vector.ui.analysis.charts.LineLayer

@Composable
fun BodyDashboard(data: BodyData, modifier: Modifier = Modifier) {
    val hasComposition = data.weight.any { it != null } || data.bodyFat.any { it != null }
    if (!hasComposition) {
        AnalysisEmptyState("No body composition data in this range.", modifier)
        return
    }

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        AnalysisSection(
            title = "Body composition",
            subtitle = "Lean mass is derived from weight and body fat."
        ) {
            AnalysisHeroStat(
                value = data.latestWeight?.let { formatStat(it) } ?: "—",
                unit = "kg",
                caption = data.latestBodyFat?.let { "${formatStat(it)}% body fat" },
                delta = data.weightDelta,
                deltaUnit = "kg",
                higherIsBetter = false
            )
            AnalysisLineChart(
                dates = data.dates,
                layers = listOfNotNull(
                    LineLayer("Weight", data.weight, AnalysisTheme.AccentLine, filled = true),
                    LineLayer("Lean mass", data.leanMass, AnalysisTheme.NavyAccent),
                    LineLayer(
                        "Trend",
                        data.weightTrend,
                        AnalysisTheme.TextSecondary,
                        dashed = true,
                        strokeWidth = 2f
                    ),
                ),
                yLabel = "kg",
                modifier = Modifier.padding(top = 14.dp)
            )
            AnalysisLegendRow(
                entries = listOf(
                    "Weight" to AnalysisTheme.AccentLine,
                    "Lean mass" to AnalysisTheme.NavyAccent,
                    "Trend" to AnalysisTheme.TextSecondary,
                ),
                modifier = Modifier.padding(top = 10.dp)
            )
            AnalysisStatRow(
                stats = listOf(
                    "Weight Δ" to signed(data.weightDelta, "kg"),
                    "Body fat Δ" to signed(data.bodyFatDelta, "%"),
                    "Lean Δ" to signed(data.leanMassDelta, "kg"),
                ),
                modifier = Modifier.padding(top = 14.dp)
            )
        }
    }
}

private fun signed(value: Double?, unit: String): String {
    if (value == null) return "—"
    val sign = if (value >= 0) "+" else "−"
    return "$sign${formatStat(kotlin.math.abs(value))}$unit"
}
