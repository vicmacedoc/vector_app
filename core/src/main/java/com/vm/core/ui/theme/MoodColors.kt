package com.vm.core.ui.theme

import androidx.compose.ui.graphics.Color

/** Mood scale 1–5 (matches Calendar diary). 1 = lowest, 5 = highest. */
val MoodColor1 = Color(0xFFE53935)
val MoodColor2 = Color(0xFFFF9800)
val MoodColor3 = Color(0xFFFFEB3B)
val MoodColor4 = Color(0xFF8BC34A)
val MoodColor5 = Color(0xFF4CAF50)

val MoodPalette = listOf(MoodColor1, MoodColor2, MoodColor3, MoodColor4, MoodColor5)

/** No mood / out of range (Analysis grid). */
val MoodColorNa = Color(0xFF757575)

fun moodColorForLevel(level: Int): Color =
    if (level in 1..5) MoodPalette[level - 1] else MoodColorNa
