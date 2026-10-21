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
 * One plotted series. [values] must be the same length as the chart's label list;
 * nulls break the line into segments rather than interpolating across missing days.
 */
data class LineLayer(
    val name: String,
    val values: List<Double?>,
    val color: Color,
    val filled: Boolean = false,
    val dashed: Boolean = false,
    val strokeWidth: Float = 3f,
)

@Composable
fun AnalysisLineChart(
    dates: List<String>,
    layers: List<LineLayer>,
    yLabel: String,
    modifier: Modifier = Modifier,
    heightDp: Dp = 190.dp,
) {
    if (dates.isEmpty() || layers.isEmpty()) return
    val allValues = layers.flatMap { it.values }
    if (allValues.none { it != null }) return

    val (y0, y1) = valueBounds(allValues, includeZero = false)
    val span = max(y1 - y0, 1e-6)
    val ticks = axisTicks(y0, y1)

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
                    val n = dates.size

                    fun xAt(i: Int): Float =
                        if (n <= 1) chartW / 2f else chartW * i / (n - 1).toFloat()

                    fun yAt(v: Double): Float =
                        (chartH - ((v - y0) / span * chartH).toFloat()).coerceIn(0f, chartH)

                    for (gi in 0..4) {
                        val gy = chartH * gi / 4f
                        drawLine(AnalysisTheme.Grid, Offset(0f, gy), Offset(chartW, gy), 1f)
                    }

                    layers.forEach { layer ->
                        val ys = layer.values
                        val effect = if (layer.dashed) {
                            PathEffect.dashPathEffect(floatArrayOf(10f, 8f))
                        } else null
                        var i = 0
                        while (i < n) {
                            while (i < n && (i >= ys.size || ys[i] == null)) i++
                            val seg = mutableListOf<Pair<Int, Double>>()
                            while (i < n && i < ys.size && ys[i] != null) {
                                seg.add(i to ys[i]!!)
                                i++
                            }
                            if (seg.size >= 2) {
                                val linePath = Path()
                                linePath.moveTo(xAt(seg.first().first), yAt(seg.first().second))
                                for (k in 1 until seg.size) {
                                    linePath.lineTo(xAt(seg[k].first), yAt(seg[k].second))
                                }
                                if (layer.filled) {
                                    val fillPath = Path()
                                    fillPath.moveTo(xAt(seg.first().first), chartH)
                                    fillPath.lineTo(xAt(seg.first().first), yAt(seg.first().second))
                                    for (k in 1 until seg.size) {
                                        fillPath.lineTo(xAt(seg[k].first), yAt(seg[k].second))
                                    }
                                    fillPath.lineTo(xAt(seg.last().first), chartH)
                                    fillPath.close()
                                    drawPath(fillPath, layer.color.copy(alpha = 0.18f))
                                }
                                drawPath(
                                    linePath,
                                    color = layer.color,
                                    style = Stroke(width = layer.strokeWidth, pathEffect = effect)
                                )
                            } else if (seg.size == 1) {
                                drawCircle(
                                    layer.color,
                                    layer.strokeWidth + 1f,
                                    Offset(xAt(seg[0].first), yAt(seg[0].second))
                                )
                            }
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
                    text = shortDate(dates.first()),
                    color = AnalysisTheme.TextSecondary,
                    fontSize = 10.sp,
                    modifier = Modifier.align(Alignment.BottomStart)
                )
                Text(
                    text = shortDate(dates.last()),
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
