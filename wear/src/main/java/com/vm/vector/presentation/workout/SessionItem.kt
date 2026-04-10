package com.vm.vector.presentation.workout

import com.vm.core.models.WorkoutSet

/**
 * One session (workout) for the day: title and its sets.
 */
data class SessionItem(
    val title: String,
    val sets: List<WorkoutSet>,
    val description: String? = null
) {
    val isResistance: Boolean =
        sets.any { it.exerciseType.equals("RESISTANCE", ignoreCase = true) }
}
