package com.vm.vector.ui.analysis

import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.vm.core.ui.theme.ElectricBlue
import com.vm.core.ui.theme.NavyDeep
import com.vm.core.ui.theme.NavyLight
import com.vm.core.ui.theme.OffWhite
import com.vm.core.ui.theme.PriorityHigh
import com.vm.core.ui.theme.PriorityLow
import com.vm.core.ui.theme.PriorityMid
import com.vm.core.ui.theme.PureWhite
import com.vm.core.ui.theme.SlateGray

/** Light Analysis UI aligned with app Navy branding. */
object AnalysisTheme {
    val Background = PureWhite
    val Surface = OffWhite
    val TextPrimary = NavyDeep
    val TextSecondary = SlateGray
    val AccentLine = ElectricBlue
    val NavyAccent = NavyDeep
    val Grid = SlateGray.copy(alpha = 0.28f)
    val Border = NavyDeep.copy(alpha = 0.10f)

    /** Cells and bars with no data behind them. */
    val Empty = SlateGray.copy(alpha = 0.12f)

    val Positive = PriorityLow
    val Negative = PriorityHigh
    val Warning = PriorityMid

    /** Categorical palette for multi-series charts, ordered by visual weight. */
    val Series: List<Color> = listOf(
        ElectricBlue,
        NavyDeep,
        PriorityMid,
        PriorityLow,
        NavyLight,
        PriorityHigh,
    )

    fun series(index: Int): Color = Series[index.mod(Series.size)]

    /** Intensity ramp for heatmap cells; [fraction] is clamped to 0..1. */
    fun heat(fraction: Double): Color {
        val f = fraction.coerceIn(0.0, 1.0)
        if (f <= 0.0) return Empty
        return ElectricBlue.copy(alpha = (0.16f + 0.84f * f.toFloat()).coerceIn(0f, 1f))
    }
}

@Composable
fun analysisOutlinedTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = AnalysisTheme.TextPrimary,
    unfocusedTextColor = AnalysisTheme.TextPrimary,
    focusedContainerColor = PureWhite,
    unfocusedContainerColor = PureWhite,
    cursorColor = AnalysisTheme.NavyAccent,
    focusedBorderColor = AnalysisTheme.NavyAccent,
    unfocusedBorderColor = AnalysisTheme.TextSecondary.copy(alpha = 0.6f),
    focusedLabelColor = AnalysisTheme.TextSecondary,
    unfocusedLabelColor = AnalysisTheme.TextSecondary,
)

@Composable
fun analysisFilterChipColors() = FilterChipDefaults.filterChipColors(
    selectedContainerColor = AnalysisTheme.NavyAccent,
    selectedLabelColor = PureWhite,
    containerColor = AnalysisTheme.Surface,
    labelColor = AnalysisTheme.TextPrimary,
    iconColor = AnalysisTheme.TextSecondary,
    selectedLeadingIconColor = PureWhite,
)
