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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vm.vector.ui.analysis.AnalysisTheme
import kotlin.math.max

/** One band of a stacked bar; [values] is aligned to the chart's label list. */
data class StackSegment(
    val name: String,
    val color: Color,
    val values: List<Double>,
)

/** Composition over time: how a total splits between categories, bucket by bucket. */
@Composable
fun AnalysisStackedBarChart(
    labels: List<String>,
    segments: List<StackSegment>,
    yLabel: String,
    modifier: Modifier = Modifier,
    heightDp: Dp = 170.dp,
) {
    if (labels.isEmpty() || segments.isEmpty()) return

    val totals = labels.indices.map { i ->
        segments.sumOf { it.values.getOrElse(i) { 0.0 } }
    }
    if (totals.all { it <= 0.0 }) return
    val high = max(totals.max(), 1e-6)
    val ticks = axisTicks(0.0, high)

    Column(modifier = modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            Column(
                modifier = Modifier
                    .width(44.dp)
                    .height(heightDp)
                    .padding(top = 6.dp, bottom = 22.dp),
                verticalArrangement = Arrangement.SpaceBetween,
                horizontalAlignment = Alignment.End
            ) {
                ticks.forEach { v ->
                    Text(
                        text = formatAxisValue(v),
                        color = AnalysisTheme.TextSecondary,
                        fontSize = 9.sp,
                        maxLines = 1,
                        modifier = Modifier.padding(end = 4.dp)
                    )
                }
            }
            Box(modifier = Modifier.weight(1f)) {
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(heightDp)
                        .padding(end = 4.dp, top = 6.dp, bottom = 22.dp)
                ) {
                    val chartW = size.width
                    val chartH = size.height
                    val slot = chartW / labels.size
                    val barWidth = (slot * 0.62f).coerceAtLeast(1f)

                    for (gi in 0..4) {
                        val gy = chartH * gi / 4f
                        drawLine(AnalysisTheme.Grid, Offset(0f, gy), Offset(chartW, gy), 1f)
                    }

                    labels.indices.forEach { i ->
                        var cursor = chartH
                        val x = slot * i + (slot - barWidth) / 2f
                        segments.forEach { segment ->
                            val v = segment.values.getOrElse(i) { 0.0 }
                            if (v <= 0.0) return@forEach
                            val h = (v / high * chartH).toFloat()
                            cursor -= h
                            drawRect(
                                color = segment.color,
                                topLeft = Offset(x, cursor),
                                size = Size(barWidth, h)
                            )
                        }
                    }
                }

                Text(
                    text = yLabel,
                    color = AnalysisTheme.TextSecondary,
                    fontSize = 9.sp,
                    modifier = Modifier.align(Alignment.TopStart)
                )
                Text(
                    text = shortDate(labels.first()),
                    color = AnalysisTheme.TextSecondary,
                    fontSize = 10.sp,
                    modifier = Modifier.align(Alignment.BottomStart)
                )
                Text(
                    text = shortDate(labels.last()),
                    color = AnalysisTheme.TextSecondary,
                    fontSize = 10.sp,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 4.dp)
                )
            }
        }
    }
}
