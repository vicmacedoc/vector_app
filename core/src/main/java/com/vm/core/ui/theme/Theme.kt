package com.vm.core.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Phone: Light Color Scheme (Navy/Blue/White)
private val VectorLightColorScheme = lightColorScheme(
    primary = NavyDeep,       // Navy for buttons
    onPrimary = PureWhite,    // White text on buttons
    background = PureWhite,   // White screen background
    surface = PureWhite,      // White cards/menus
    onBackground = Color.Black, // Mostly black text
    onSurface = Color.Black,
    // Navy blue for the bottom menu (using inverse roles)
    inverseSurface = NavyDeep, 
    inverseOnSurface = PureWhite
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
        typography = Typography,
        content = content
    )
}