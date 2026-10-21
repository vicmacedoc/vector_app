package com.vm.vector.ui.analysis.charts

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vm.vector.ui.analysis.AnalysisTheme

/** A labelled row of coloured cells; [trailing] shows a summary value on the right. */
data class HeatmapRow(
    val label: String,
    val cells: List<Color>,
    val trailing: String? = null,
)

/**
 * Grid of coloured cells, one row per series and one column per period.
 * Reads far better than a line chart when the question is "which days did I miss".
 */
@Composable
fun AnalysisHeatmap(
    rows: List<HeatmapRow>,
    modifier: Modifier = Modifier,
    labelWidth: Dp = 64.dp,
    cellHeight: Dp = 20.dp,
    trailingWidth: Dp = 44.dp,
    startCaption: String? = null,
    endCaption: String? = null,
) {
    if (rows.isEmpty()) return
    val hasTrailing = rows.any { it.trailing != null }

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(3.dp)) {
        rows.forEach { row ->
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = row.label,
                    color = AnalysisTheme.TextSecondary,
                    fontSize = 10.sp,
                    maxLines = 1,
                    modifier = Modifier
                        .width(labelWidth)
                        .padding(end = 6.dp)
                )
                Canvas(
                    modifier = Modifier
                        .weight(1f)
                        .height(cellHeight)
                ) {
                    val n = row.cells.size
                    if (n == 0) return@Canvas
                    val slot = size.width / n
                    val gap = (slot * 0.14f).coerceAtMost(2.5f)
                    val w = (slot - gap).coerceAtLeast(1f)
                    row.cells.forEachIndexed { i, color ->
                        drawRoundRect(
                            color = color,
                            topLeft = Offset(slot * i, 0f),
                            size = Size(w, size.height),
                            cornerRadius = CornerRadius(2.5f, 2.5f)
                        )
                    }
                }
                if (hasTrailing) {
                    Box(
                        modifier = Modifier
                            .width(trailingWidth)
                            .padding(start = 6.dp),
                        contentAlignment = Alignment.CenterEnd
                    ) {
                        Text(
                            text = row.trailing ?: "",
                            color = AnalysisTheme.TextPrimary,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 11.sp,
                            maxLines = 1
                        )
                    }
                }
            }
        }

        if (startCaption != null || endCaption != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = labelWidth, end = if (hasTrailing) trailingWidth else 0.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = startCaption.orEmpty(),
                    color = AnalysisTheme.TextSecondary,
                    fontSize = 10.sp
                )
                Text(
                    text = endCaption.orEmpty(),
                    color = AnalysisTheme.TextSecondary,
                    fontSize = 10.sp
                )
            }
        }
    }
}
