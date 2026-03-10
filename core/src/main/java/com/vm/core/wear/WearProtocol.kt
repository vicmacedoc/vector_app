package com.vm.core.wear

import com.vm.core.models.WorkoutSet
import kotlinx.serialization.Serializable

object WearPaths {
    const val WORKOUTS_REQUEST = "/vector/workouts_request"
    const val WORKOUTS_RESPONSE = "/vector/workouts_response"
    const val WORKOUT_COMPLETED = "/vector/workout_completed"
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
