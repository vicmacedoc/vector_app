package com.vm.vector.ui.analysis.charts

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vm.vector.ui.analysis.AnalysisTheme
import java.util.Locale

/** One lane of the strip: a day label and the times-of-day something happened. */
data class DotStripRow(val label: String, val minutes: List<Int>)

/**
 * Times-of-day plotted as dots, one lane per day. Makes drifting schedules
 * (late first meal, late-night eating) visible in a way daily totals cannot.
 */
@Composable
fun AnalysisDotStrip(
    rows: List<DotStripRow>,
    modifier: Modifier = Modifier,
    startMinute: Int = 4 * 60,
    endMinute: Int = 24 * 60,
    labelWidth: Dp = 52.dp,
    laneHeight: Dp = 14.dp,
    dotColor: Color = AnalysisTheme.AccentLine,
) {
    if (rows.isEmpty()) return
    val span = (endMinute - startMinute).coerceAtLeast(1)

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        rows.forEach { row ->
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = row.label,
                    color = AnalysisTheme.TextSecondary,
                    fontSize = 9.sp,
                    maxLines = 1,
                    modifier = Modifier
                        .width(labelWidth)
                        .padding(end = 6.dp)
                )
                Canvas(
                    modifier = Modifier
                        .weight(1f)
                        .height(laneHeight)
                ) {
                    val midY = size.height / 2f
                    drawLine(
                        AnalysisTheme.Grid,
                        Offset(0f, midY),
                        Offset(size.width, midY),
                        1f
                    )
                    row.minutes.forEach { minute ->
                        val clamped = minute.coerceIn(startMinute, endMinute)
                        val x = (clamped - startMinute).toFloat() / span * size.width
                        drawCircle(dotColor, size.height * 0.28f, Offset(x, midY))
                    }
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = labelWidth, top = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            listOf(startMinute, (startMinute + endMinute) / 2, endMinute).forEach { m ->
                Text(
                    text = String.format(Locale.US, "%02d:00", (m / 60) % 24),
                    color = AnalysisTheme.TextSecondary,
                    fontSize = 9.sp
                )
            }
        }
    }
}
