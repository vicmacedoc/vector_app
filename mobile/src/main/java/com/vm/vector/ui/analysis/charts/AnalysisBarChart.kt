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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vm.vector.ui.analysis.AnalysisTheme
import kotlin.math.max

/**
 * Daily bars with an optional target line and an optional overlaid trend series
 * (typically a rolling average) drawn on the same scale.
 */
@Composable
fun AnalysisBarChart(
    labels: List<String>,
    values: List<Double?>,
    yLabel: String,
    modifier: Modifier = Modifier,
    targetValue: Double? = null,
    overlay: List<Double?>? = null,
    barColor: Color = AnalysisTheme.AccentLine,
    overlayColor: Color = AnalysisTheme.NavyAccent,
    heightDp: Dp = 180.dp,
) {
    if (labels.isEmpty()) return
    if (values.none { it != null }) return

    val scaleSource = values + (overlay.orEmpty()) + listOfNotNull(targetValue)
    val (rawLow, high) = valueBounds(scaleSource, includeZero = true)
    val y0 = minOf(rawLow, 0.0)
    val span = max(high - y0, 1e-6)
    val ticks = axisTicks(y0, high)

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
                    val n = labels.size
                    val slot = chartW / n
                    val barWidth = (slot * 0.68f).coerceAtLeast(1f)

                    fun yAt(v: Double): Float =
                        (chartH - ((v - y0) / span * chartH).toFloat()).coerceIn(0f, chartH)

                    for (gi in 0..4) {
                        val gy = chartH * gi / 4f
                        drawLine(AnalysisTheme.Grid, Offset(0f, gy), Offset(chartW, gy), 1f)
                    }

                    val baseline = yAt(0.0)
                    values.forEachIndexed { i, v ->
                        if (v == null) return@forEachIndexed
                        val top = yAt(v)
                        val x = slot * i + (slot - barWidth) / 2f
                        drawRect(
                            color = barColor,
                            topLeft = Offset(x, minOf(top, baseline)),
                            size = Size(barWidth, kotlin.math.abs(baseline - top).coerceAtLeast(1f))
                        )
                    }

                    if (targetValue != null) {
                        val ty = yAt(targetValue)
                        drawLine(
                            color = AnalysisTheme.Warning,
                            start = Offset(0f, ty),
                            end = Offset(chartW, ty),
                            strokeWidth = 2f,
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f))
                        )
                    }

                    if (overlay != null) {
                        val path = Path()
                        var started = false
                        overlay.forEachIndexed { i, v ->
                            if (v == null) return@forEachIndexed
                            val x = slot * i + slot / 2f
                            val y = yAt(v)
                            if (!started) {
                                path.moveTo(x, y)
                                started = true
                            } else {
                                path.lineTo(x, y)
                            }
                        }
                        if (started) {
                            drawPath(path, color = overlayColor, style = Stroke(width = 3f))
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
