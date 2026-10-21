package com.vm.vector.ui.analysis.charts

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vm.vector.ui.analysis.AnalysisTheme

data class RankBarItem(val label: String, val value: Double, val valueLabel: String)

/**
 * Horizontal bars sorted by magnitude. Answers "where is the imbalance" at a glance,
 * which a one-series-at-a-time dropdown cannot.
 */
@Composable
fun AnalysisRankBars(
    items: List<RankBarItem>,
    modifier: Modifier = Modifier,
    selected: String? = null,
    onSelect: ((String) -> Unit)? = null,
    maxRows: Int = 12,
) {
    if (items.isEmpty()) return
    val shown = items.take(maxRows)
    val peak = shown.maxOf { it.value }.coerceAtLeast(1e-6)

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        shown.forEach { item ->
            val isSelected = selected != null && item.label.equals(selected, ignoreCase = true)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(
                        if (onSelect != null) {
                            Modifier.clickable { onSelect(item.label) }
                        } else Modifier
                    ),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = item.label.replaceFirstChar { it.uppercase() },
                    color = if (isSelected) AnalysisTheme.TextPrimary else AnalysisTheme.TextSecondary,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                    fontSize = 11.sp,
                    maxLines = 1,
                    modifier = Modifier
                        .width(80.dp)
                        .padding(end = 6.dp)
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(16.dp)
                        .background(AnalysisTheme.Empty, RoundedCornerShape(4.dp))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth((item.value / peak).toFloat().coerceIn(0.02f, 1f))
                            .height(16.dp)
                            .background(
                                if (isSelected) AnalysisTheme.NavyAccent else AnalysisTheme.AccentLine,
                                RoundedCornerShape(4.dp)
                            )
                    )
                }
                Text(
                    text = item.valueLabel,
                    color = AnalysisTheme.TextPrimary,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    maxLines = 1,
                    modifier = Modifier
                        .width(44.dp)
                        .padding(start = 6.dp)
                )
            }
        }
    }
}
