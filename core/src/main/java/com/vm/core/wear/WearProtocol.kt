package com.vm.core.wear

import com.vm.core.models.WorkoutSet
import kotlinx.serialization.Serializable

object WearPaths {
    const val WORKOUTS_REQUEST = "/vector/workouts_request"
    const val WORKOUTS_RESPONSE = "/vector/workouts_response"
    const val WORKOUT_COMPLETED = "/vector/workout_completed"
    const val ROUTINE_REQUEST = "/vector/routine_request"
    const val ROUTINE_RESPONSE = "/vector/routine_response"
    /** Watch sends completed routine value; merged into Calendar like [WORKOUT_COMPLETED]. */
    const val ROUTINE_COMPLETED = "/vector/routine_completed"
}

@Serializable
data class WorkoutsForDatePayload(
    val date: String,
    val sets: List<WorkoutSet> = emptyList()
)

@Serializable
data class WorkoutCompletedPayload(
    val date: String,
    val sets: List<WorkoutSet> = emptyList()
)

/** Numerical routine row for Wear (time-based units only), synced from mobile preset + Room. */
@Serializable
data class RoutineWearEntry(
    val id: String,
    val title: String,
    val category: String,
    val unit: String,
    val goalValue: Double,
    val partialThreshold: Double,
    val currentValue: Double? = null,
    val directionBetter: Int = 1,
    val minValue: Double? = null,
    val maxValue: Double? = null
)

@Serializable
data class RoutineForDatePayload(
    val date: String,
    val entries: List<RoutineWearEntry> = emptyList()
)

@Serializable
data class RoutineCompletedPayload(
    val date: String,
    val entryId: String,
    val currentValue: Double
)
