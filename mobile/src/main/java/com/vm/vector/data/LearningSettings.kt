package com.vm.vector.data

import java.util.concurrent.TimeUnit

/** Learn tile rules edited in Settings; defaults follow spaced-repetition practice. */
data class LearningSettings(
    val onHoldDays: Int = DEFAULT_ON_HOLD_DAYS,
    /** Expanding review ladder; its length is the number of reviews before a tile is mastered. */
    val reviewIntervalsDays: List<Int> = DEFAULT_REVIEW_INTERVALS,
    val quizSize: Int = DEFAULT_QUIZ_SIZE,
    val passPercent: Int = DEFAULT_PASS_PERCENT,
) {
    val onHoldMillis: Long get() = TimeUnit.DAYS.toMillis(onHoldDays.toLong())

    /** Days until the review at [step], or null once the ladder is finished (mastered). */
    fun intervalDays(step: Int): Int? = reviewIntervalsDays.getOrNull(step)

    companion object {
        const val DEFAULT_ON_HOLD_DAYS = 14
        val DEFAULT_REVIEW_INTERVALS = listOf(3, 7, 21, 60, 180)
        const val DEFAULT_QUIZ_SIZE = 5
        const val DEFAULT_PASS_PERCENT = 80

        fun sanitized(
            onHoldDays: Int?,
            reviewIntervalsDays: List<Int>?,
            quizSize: Int?,
            passPercent: Int?,
        ): LearningSettings = LearningSettings(
            onHoldDays = onHoldDays?.coerceIn(1, 365) ?: DEFAULT_ON_HOLD_DAYS,
            reviewIntervalsDays = reviewIntervalsDays
                ?.filter { it in 1..3650 }
                ?.take(12)
                ?.takeIf { it.isNotEmpty() }
                ?: DEFAULT_REVIEW_INTERVALS,
            quizSize = quizSize?.coerceIn(1, 20) ?: DEFAULT_QUIZ_SIZE,
            passPercent = passPercent?.coerceIn(10, 100) ?: DEFAULT_PASS_PERCENT,
        )
    }
}
