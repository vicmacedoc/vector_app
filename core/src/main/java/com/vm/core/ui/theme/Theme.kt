package com.vm.core.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Phone: Light Color Scheme (Navy/Blue/White)
private val VectorLightColorScheme = lightColorScheme(
    primary = NavyDeep,
    onPrimary = PureWhite,
    primaryContainer = NavyLight,
    onPrimaryContainer = PureWhite,
    secondary = ElectricBlue,
    onSecondary = PureWhite,
    background = PureWhite,
    surface = OffWhite,
    onBackground = NavyDeep,
    onSurface = NavyDeep
)

// Phone: Dark Color Scheme (Optional, for System Dark Mode)
private val VectorDarkColorScheme = darkColorScheme(
    primary = ElectricBlue,
    onPrimary = NavyDeep,
    background = NavyDeep,
    surface = Color(0xFF121212),
    onBackground = PureWhite,
    onSurface = PureWhite
)

@Composable
fun VectorTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) VectorDarkColorScheme else VectorLightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        // Typography = Typography, // Ensure you have a Typography.kt defined or use default
        content = content
    )
}