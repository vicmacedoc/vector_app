package com.vm.vector.ui.analysis

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vm.vector.data.analysis.TripleStat

@Composable
fun AnalysisStatCard3(
    leftLabel: String,
    leftValue: String,
    midLabel: String,
    midValue: String,
    rightLabel: String,
    rightValue: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(AnalysisTheme.Surface, RoundedCornerShape(12.dp))
            .padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        StatCell(leftLabel, leftValue, Modifier.weight(1f))
        StatCell(midLabel, midValue, Modifier.weight(1f))
        StatCell(rightLabel, rightValue, Modifier.weight(1f))
    }
}

@Composable
private fun StatCell(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier.padding(horizontal = 4.dp)) {
        Text(
            text = label,
            color = AnalysisTheme.TextSecondary,
            fontSize = 12.sp
        )
        Text(
            text = value,
            color = AnalysisTheme.TextPrimary,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun AnalysisTripleStatCard(
    stat: TripleStat?,
    modifier: Modifier = Modifier,
) {
    val fmt = { v: Double -> "%.1f".format(v) }
    AnalysisStatCard3(
        modifier = modifier,
        leftLabel = "Highest",
        leftValue = stat?.let { fmt(it.high) } ?: "—",
        midLabel = "Average",
        midValue = stat?.let { fmt(it.avg) } ?: "—",
        rightLabel = "Lowest",
        rightValue = stat?.let { fmt(it.low) } ?: "—",
    )
}
