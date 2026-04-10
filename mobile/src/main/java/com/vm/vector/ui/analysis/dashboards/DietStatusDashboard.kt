package com.vm.vector.ui.analysis.dashboards

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vm.vector.data.analysis.CountMaxStreak
import com.vm.vector.data.analysis.LineSeries
import com.vm.vector.data.analysis.TripleStat
import com.vm.vector.ui.analysis.AnalysisLineChart
import com.vm.vector.ui.analysis.AnalysisTripleStatCard
import com.vm.vector.ui.analysis.AnalysisTheme

@Composable
fun DietStatusDashboard(
    planComplianceStats: CountMaxStreak?,
    planComplianceLine: LineSeries?,
    macros: Map<String, Pair<TripleStat?, LineSeries>>,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Text(
            text = "Plan compliance",
            color = AnalysisTheme.TextPrimary,
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Share of items that are planned or adjusted (not unplanned), marked eaten, and not skipped — over all items logged that day.",
            color = AnalysisTheme.TextSecondary,
            fontSize = 12.sp
        )
        Spacer(modifier = Modifier.height(12.dp))
        CategoryCompletionBody(stats = planComplianceStats, line = planComplianceLine)

        Spacer(modifier = Modifier.height(28.dp))
        Text(
            text = "Macros",
            color = AnalysisTheme.TextPrimary,
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(modifier = Modifier.height(12.dp))

        MacroBlock("Calories", "kcal", macros["kcal"])
        Spacer(modifier = Modifier.height(20.dp))
        MacroBlock("Protein", "g", macros["protein"])
        Spacer(modifier = Modifier.height(20.dp))
        MacroBlock("Carbs", "g", macros["carbs"])
        Spacer(modifier = Modifier.height(20.dp))
        MacroBlock("Fat", "g", macros["fats"])
    }
}

@Composable
private fun MacroBlock(
    title: String,
    yLabel: String,
    data: Pair<TripleStat?, LineSeries>?,
) {
    Text(
        text = title,
        color = AnalysisTheme.TextPrimary,
        fontSize = 16.sp,
        fontWeight = FontWeight.SemiBold
    )
    Spacer(modifier = Modifier.height(8.dp))
    AnalysisTripleStatCard(stat = data?.first)
    Spacer(modifier = Modifier.height(8.dp))
    data?.second?.let { series ->
        AnalysisLineChart(series = series.copy(yLabel = yLabel))
    }
}
