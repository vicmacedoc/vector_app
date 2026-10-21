package com.vm.vector.ui.analysis.charts

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vm.vector.ui.analysis.AnalysisTheme

data class DonutSlice(val name: String, val value: Double, val color: Color)

/** Single proportional ring with a headline value in the middle. */
@Composable
fun AnalysisDonutChart(
    slices: List<DonutSlice>,
    centerValue: String,
    modifier: Modifier = Modifier,
    centerCaption: String? = null,
    diameter: Dp = 176.dp,
) {
    val total = slices.sumOf { it.value }
    Box(
        modifier = modifier.size(diameter),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(diameter)) {
            val stroke = size.minDimension * 0.17f
            val radius = (size.minDimension - stroke) / 2f
            if (total <= 0.0) {
                drawRing(radius, stroke, AnalysisTheme.Empty, -90f, 360f)
            } else {
                var angle = -90f
                slices.forEach { slice ->
                    if (slice.value <= 0.0) return@forEach
                    val sweep = (slice.value / total * 360.0).toFloat()
                    drawRing(radius, stroke, slice.color, angle, sweep)
                    angle += sweep
                }
            }
        }
        DonutCenter(centerValue, centerCaption)
    }
}

/**
 * Two concentric proportional rings sharing one scale, for planned (inner) versus
 * done (outer). Whatever is missing from [totalValue] is drawn as unlogged.
 */
@Composable
fun AnalysisDualRingDonut(
    innerSlices: List<DonutSlice>,
    outerSlices: List<DonutSlice>,
    centerValue: String,
    modifier: Modifier = Modifier,
    centerCaption: String? = null,
    totalValue: Double? = null,
    diameter: Dp = 196.dp,
) {
    Box(modifier = modifier.size(diameter), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.size(diameter)) {
            val stroke = size.minDimension * 0.12f
            val gap = size.minDimension * 0.035f
            val outerRadius = (size.minDimension - stroke) / 2f
            val innerRadius = outerRadius - stroke - gap

            fun ring(slices: List<DonutSlice>, radius: Float) {
                val sum = slices.sumOf { it.value }
                val scale = totalValue?.takeIf { it > 0.0 } ?: sum
                if (scale <= 0.0) {
                    drawRing(radius, stroke, AnalysisTheme.Empty, -90f, 360f)
                    return
                }
                drawRing(radius, stroke, AnalysisTheme.Empty, -90f, 360f)
                var angle = -90f
                slices.forEach { slice ->
                    if (slice.value <= 0.0) return@forEach
                    val sweep = (slice.value / scale * 360.0).toFloat().coerceAtMost(360f)
                    drawRing(radius, stroke, slice.color, angle, sweep)
                    angle += sweep
                }
            }

            ring(outerSlices, outerRadius)
            ring(innerSlices, innerRadius)
        }
        DonutCenter(centerValue, centerCaption)
    }
}

@Composable
private fun DonutCenter(value: String, caption: String?) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth(0.5f)
    ) {
        Text(
            text = value,
            color = AnalysisTheme.TextPrimary,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            fontSize = 20.sp,
            textAlign = TextAlign.Center
        )
        if (caption != null) {
            Text(
                text = caption,
                color = AnalysisTheme.TextSecondary,
                fontSize = 11.sp,
                textAlign = TextAlign.Center
            )
        }
    }
}

private fun DrawScope.drawRing(
    radius: Float,
    stroke: Float,
    color: Color,
    startAngle: Float,
    sweep: Float,
) {
    if (sweep <= 0f || radius <= 0f) return
    val center = Offset(size.width / 2f, size.height / 2f)
    drawArc(
        color = color,
        startAngle = startAngle,
        sweepAngle = sweep,
        useCenter = false,
        topLeft = Offset(center.x - radius, center.y - radius),
        size = Size(radius * 2f, radius * 2f),
        style = Stroke(width = stroke, cap = StrokeCap.Butt)
    )
}
