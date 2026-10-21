package com.vm.vector.presentation.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.wear.compose.material.Colors
import androidx.wear.compose.material.MaterialTheme
import com.vm.core.ui.theme.DeleteRed
import com.vm.core.ui.theme.ElectricBlue
import com.vm.core.ui.theme.NavyDeep
import com.vm.core.ui.theme.PureBlack
import com.vm.core.ui.theme.PureWhite

/**
 * Wear MaterialTheme mapped to the Vector core palette.
 * Background stays black for OLED battery; primary actions use NavyDeep / ElectricBlue.
 */
private val VectorWearColors = Colors(
    primary = ElectricBlue,
    primaryVariant = NavyDeep,
    secondary = ElectricBlue,
    secondaryVariant = NavyDeep,
    background = PureBlack,
    surface = Color(0xFF1A1A1A),
    error = DeleteRed,
    onPrimary = PureWhite,
    onSecondary = PureWhite,
    onBackground = PureWhite,
    onSurface = PureWhite,
    onError = PureWhite
)

@Composable
fun VectorTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colors = VectorWearColors,
        content = content
    )
}
