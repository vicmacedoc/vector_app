package com.vm.vector.data

import com.vm.core.models.MotionScoring
import java.time.DayOfWeek

/** Motion score rules edited in Settings. */
data class MotionSettings(
    val dailyTarget: Int = MotionScoring.DEFAULT_DAILY_TARGET,
    val supermarketDay: DayOfWeek = MotionScoring.DEFAULT_SUPERMARKET_DAY,
) {
    companion object {
        fun sanitized(dailyTarget: Int?, supermarketDay: String?): MotionSettings = MotionSettings(
            dailyTarget = dailyTarget?.coerceIn(5, 1000) ?: MotionScoring.DEFAULT_DAILY_TARGET,
            supermarketDay = supermarketDay
                ?.let { runCatching { DayOfWeek.valueOf(it) }.getOrNull() }
                ?: MotionScoring.DEFAULT_SUPERMARKET_DAY,
        )
    }
}
