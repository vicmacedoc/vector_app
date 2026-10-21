package com.vm.vector.ui.analysis

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale
import kotlin.math.abs

/** Card that every dashboard block sits in: titled, bordered, on the Analysis surface. */
@Composable
fun AnalysisSection(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    trailing: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(AnalysisTheme.Surface, RoundedCornerShape(16.dp))
            .border(1.dp, AnalysisTheme.Border, RoundedCornerShape(16.dp))
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title.uppercase(Locale.US),
                color = AnalysisTheme.TextSecondary,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Medium,
                fontSize = 11.sp,
                letterSpacing = 1.sp
            )
            if (trailing != null) {
                Text(
                    text = trailing,
                    color = AnalysisTheme.TextPrimary,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp
                )
            }
        }
        if (subtitle != null) {
            Text(
                text = subtitle,
                color = AnalysisTheme.TextSecondary,
                fontSize = 11.sp,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
        Column(modifier = Modifier.padding(top = 12.dp)) { content() }
    }
}

/**
 * Headline number for a section, with an optional signed delta pill.
 * [higherIsBetter] flips the pill colour so falling weight reads as progress.
 */
@Composable
fun AnalysisHeroStat(
    value: String,
    modifier: Modifier = Modifier,
    unit: String? = null,
    caption: String? = null,
    delta: Double? = null,
    deltaUnit: String = "",
    higherIsBetter: Boolean = true,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = value,
                color = AnalysisTheme.TextPrimary,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 32.sp
            )
            if (unit != null) {
                Text(
                    text = unit,
                    color = AnalysisTheme.TextSecondary,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(start = 6.dp, bottom = 5.dp)
                )
            }
            if (delta != null) {
                Box(modifier = Modifier.padding(start = 10.dp, bottom = 6.dp)) {
                    AnalysisDeltaPill(delta, deltaUnit, higherIsBetter)
                }
            }
        }
        if (caption != null) {
            Text(
                text = caption,
                color = AnalysisTheme.TextSecondary,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
    }
}

@Composable
fun AnalysisDeltaPill(
    delta: Double,
    unit: String = "",
    higherIsBetter: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val improving = if (higherIsBetter) delta >= 0 else delta <= 0
    val color = when {
        abs(delta) < 1e-6 -> AnalysisTheme.TextSecondary
        improving -> AnalysisTheme.Positive
        else -> AnalysisTheme.Negative
    }
    val sign = if (delta >= 0) "+" else "−"
    Text(
        text = "$sign${formatStat(abs(delta))}$unit",
        color = color,
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.SemiBold,
        fontSize = 12.sp,
        modifier = modifier
            .background(color.copy(alpha = 0.12f), RoundedCornerShape(6.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    )
}

/** Evenly divided label/value cells; wraps to a scroll row when there are many. */
@Composable
fun AnalysisStatRow(
    stats: List<Pair<String, String>>,
    modifier: Modifier = Modifier,
) {
    if (stats.isEmpty()) return
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        stats.forEach { (label, value) ->
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label,
                    color = AnalysisTheme.TextSecondary,
                    fontSize = 10.sp,
                    maxLines = 1
                )
                Text(
                    text = value,
                    color = AnalysisTheme.TextPrimary,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
fun AnalysisLegendRow(
    entries: List<Pair<String, Color>>,
    modifier: Modifier = Modifier,
) {
    if (entries.isEmpty()) return
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        entries.forEach { (label, color) ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(modifier = Modifier.size(9.dp).background(color, CircleShape))
                Text(text = label, color = AnalysisTheme.TextSecondary, fontSize = 11.sp)
            }
        }
    }
}

@Composable
fun AnalysisEmptyState(message: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(AnalysisTheme.Surface, RoundedCornerShape(16.dp))
            .border(1.dp, AnalysisTheme.Border, RoundedCornerShape(16.dp))
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text = message, color = AnalysisTheme.TextSecondary, fontSize = 13.sp)
    }
}

/** One decimal below 100, none above, so axis and stat text stay narrow. */
fun formatStat(value: Double): String = when {
    abs(value) >= 1000 -> String.format(Locale.US, "%,.0f", value)
    abs(value) >= 100 -> String.format(Locale.US, "%.0f", value)
    else -> String.format(Locale.US, "%.1f", value)
}

fun formatStatOrDash(value: Double?): String = value?.let { formatStat(it) } ?: "—"

fun formatPercentOrDash(value: Double?): String =
    value?.let { "${String.format(Locale.US, "%.0f", it)}%" } ?: "—"
