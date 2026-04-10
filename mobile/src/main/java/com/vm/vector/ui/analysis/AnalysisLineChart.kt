package com.vm.vector.ui.analysis

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vm.vector.data.analysis.LineSeries
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.abs
import kotlin.math.max

private fun formatYTick(value: Double, yLabel: String): String {
    val isPercent = yLabel.contains('%')
    return when {
        isPercent && abs(value - value.toInt()) < 1e-6 -> "${value.toInt()}"
        abs(value - value.toInt()) < 1e-6 -> "${value.toInt()}"
        abs(value) >= 100 -> "%.0f".format(value)
        else -> "%.1f".format(value)
    }
}

@Composable
fun AnalysisLineChart(
    series: LineSeries,
    modifier: Modifier = Modifier,
    heightDp: androidx.compose.ui.unit.Dp = 200.dp,
) {
    val dates = series.dates
    val ys = series.yValues
    if (dates.isEmpty()) return

    val parsed = dates.map { LocalDate.parse(it, DateTimeFormatter.ISO_LOCAL_DATE) }
    val startLabel = "${parsed.first().month.name.take(3)} ${parsed.first().dayOfMonth}"
    val endLabel = "${parsed.last().month.name.take(3)} ${parsed.last().dayOfMonth}"

    val nonNull = ys.mapNotNull { it }
    val (minY, maxY) = if (nonNull.isEmpty()) {
        0.0 to 1.0
    } else {
        val lo = nonNull.minOrNull()!!
        val hi = nonNull.maxOrNull()!!
        if (lo == hi) lo - 1.0 to hi + 1.0 else lo to hi
    }
    val pad = (maxY - minY) * 0.08
    val y0 = minY - pad
    val y1 = maxY + pad
    val span = max(y1 - y0, 1e-6)
    val tickValues = listOf(
        y1,
        y1 - 0.25 * span,
        y1 - 0.5 * span,
        y1 - 0.75 * span,
        y0
    )

    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = series.yLabel,
                color = AnalysisTheme.TextSecondary,
                fontSize = 11.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 4.dp)
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top
            ) {
                Column(
                    modifier = Modifier
                        .width(52.dp)
                        .height(heightDp)
                        .padding(top = 8.dp, bottom = 28.dp),
                    verticalArrangement = Arrangement.SpaceBetween,
                    horizontalAlignment = Alignment.End
                ) {
                    tickValues.forEach { v ->
                        Text(
                            text = formatYTick(v, series.yLabel),
                            color = AnalysisTheme.TextSecondary,
                            fontSize = 10.sp,
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
                            .padding(start = 0.dp, end = 8.dp, top = 8.dp, bottom = 28.dp)
                    ) {
                        val chartW = size.width
                        val chartH = size.height
                        val n = dates.size
                        fun xAt(i: Int): Float = if (n <= 1) chartW / 2f else chartW * i / (n - 1).toFloat()
                        fun yAt(v: Double): Float =
                            (chartH - ((v - y0) / span * chartH).toFloat()).coerceIn(0f, chartH)

                        for (gi in 0..4) {
                            val gy = chartH * gi / 4f
                            drawLine(
                                AnalysisTheme.Grid,
                                Offset(0f, gy),
                                Offset(chartW, gy),
                                strokeWidth = 1f
                            )
                        }

                        var i = 0
                        while (i < n) {
                            while (i < n && ys[i] == null) i++
                            val seg = mutableListOf<Pair<Int, Double>>()
                            while (i < n && ys[i] != null) {
                                seg.add(i to ys[i]!!)
                                i++
                            }
                            if (seg.size >= 2) {
                                val fillPath = Path()
                                val linePath = Path()
                                val x0 = xAt(seg.first().first)
                                val y0f = yAt(seg.first().second)
                                fillPath.moveTo(x0, chartH)
                                fillPath.lineTo(x0, y0f)
                                linePath.moveTo(x0, y0f)
                                for (k in 1 until seg.size) {
                                    val x = xAt(seg[k].first)
                                    val y = yAt(seg[k].second)
                                    fillPath.lineTo(x, y)
                                    linePath.lineTo(x, y)
                                }
                                fillPath.lineTo(xAt(seg.last().first), chartH)
                                fillPath.close()
                                drawPath(fillPath, color = AnalysisTheme.AccentLine.copy(alpha = 0.22f))
                                drawPath(linePath, color = AnalysisTheme.AccentLine, style = Stroke(width = 3f))
                            } else if (seg.size == 1) {
                                drawCircle(AnalysisTheme.AccentLine, 4f, Offset(xAt(seg[0].first), yAt(seg[0].second)))
                            }
                        }
                    }

                    Text(
                        text = startLabel,
                        color = AnalysisTheme.TextSecondary,
                        fontSize = 11.sp,
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(start = 0.dp, bottom = 4.dp)
                    )
                    Text(
                        text = endLabel,
                        color = AnalysisTheme.TextSecondary,
                        fontSize = 11.sp,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(end = 8.dp, bottom = 4.dp)
                    )
                }
            }
        }
    }
}
