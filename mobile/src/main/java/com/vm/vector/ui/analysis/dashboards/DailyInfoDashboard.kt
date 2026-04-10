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
import com.vm.vector.ui.analysis.AnalysisStatCard3
import com.vm.vector.ui.analysis.AnalysisTripleStatCard
import com.vm.vector.ui.analysis.AnalysisTheme

@Composable
fun DailyInfoDashboard(
    dailyPlanStats: CountMaxStreak?,
    dailyPlanLine: LineSeries?,
    dailyInfo: Map<String, Pair<TripleStat?, LineSeries>>,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Text(
            text = "Daily plan",
            color = AnalysisTheme.TextPrimary,
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(modifier = Modifier.height(12.dp))
        AnalysisStatCard3(
            leftLabel = "Days logged",
            leftValue = dailyPlanStats?.countWithData?.toString() ?: "—",
            midLabel = "Highest %",
            midValue = dailyPlanStats?.let { "%.0f".format(it.maxValue) }?.let { "$it%" } ?: "—",
            rightLabel = "Best streak",
            rightValue = dailyPlanStats?.longestStreak?.toString() ?: "—",
        )
        Spacer(modifier = Modifier.height(16.dp))
        dailyPlanLine?.let { AnalysisLineChart(series = it) }

        Spacer(modifier = Modifier.height(28.dp))

        InfoBlock("Sleep (hours)", dailyInfo["sleep"])
        Spacer(modifier = Modifier.height(20.dp))
        InfoBlock("Body weight (kg)", dailyInfo["weight"])
        Spacer(modifier = Modifier.height(20.dp))
        InfoBlock("Body fat (%)", dailyInfo["bodyFat"])
    }
}

@Composable
private fun InfoBlock(title: String, data: Pair<TripleStat?, LineSeries>?) {
    Text(
        text = title,
        color = AnalysisTheme.TextPrimary,
        fontSize = 16.sp,
        fontWeight = FontWeight.SemiBold
    )
    Spacer(modifier = Modifier.height(8.dp))
    AnalysisTripleStatCard(stat = data?.first)
    Spacer(modifier = Modifier.height(8.dp))
    data?.second?.let { AnalysisLineChart(series = it) }
}
