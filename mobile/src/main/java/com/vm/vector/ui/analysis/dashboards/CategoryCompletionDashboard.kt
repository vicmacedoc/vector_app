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
import com.vm.vector.ui.analysis.AnalysisLineChart
import com.vm.vector.ui.analysis.AnalysisStatCard3
import com.vm.vector.ui.analysis.AnalysisTheme

@Composable
fun CategoryCompletionBody(
    stats: CountMaxStreak?,
    line: LineSeries?,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        AnalysisStatCard3(
            leftLabel = "Days w/ data",
            leftValue = stats?.countWithData?.toString() ?: "—",
            midLabel = "Highest %",
            midValue = stats?.let { "%.0f".format(it.maxValue) }?.let { "$it%" } ?: "—",
            rightLabel = "Best streak",
            rightValue = stats?.longestStreak?.toString() ?: "—",
        )
        Spacer(modifier = Modifier.height(16.dp))
        line?.let { AnalysisLineChart(series = it) }
    }
}

@Composable
fun CategoryCompletionDashboard(
    title: String,
    stats: CountMaxStreak?,
    line: LineSeries?,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Text(
            text = title,
            color = AnalysisTheme.TextPrimary,
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(modifier = Modifier.height(12.dp))
        CategoryCompletionBody(stats = stats, line = line)
    }
}
