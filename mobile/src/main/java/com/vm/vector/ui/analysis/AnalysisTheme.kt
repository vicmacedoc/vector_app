package com.vm.vector.ui.analysis

import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.vm.core.ui.theme.ElectricBlue
import com.vm.core.ui.theme.NavyDeep
import com.vm.core.ui.theme.OffWhite
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
