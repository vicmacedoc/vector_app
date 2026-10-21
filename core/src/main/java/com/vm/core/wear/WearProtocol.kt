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

    const val MOTION_REQUEST = "/vector/motion_request"
    const val MOTION_RESPONSE = "/vector/motion_response"
    const val MOTION_COMPLETE = "/vector/motion_complete"

    const val FOOD_REQUEST = "/vector/food_request"
    const val FOOD_RESPONSE = "/vector/food_response"
    const val FOOD_STATUS_SET = "/vector/food_status_set"

    const val ACTIVITIES_REQUEST = "/vector/activities_request"
    const val ACTIVITIES_RESPONSE = "/vector/activities_response"
    const val ACTIVITY_COMPLETION_SET = "/vector/activity_completion_set"

    const val SUPERMARKET_REQUEST = "/vector/supermarket_request"
    const val SUPERMARKET_RESPONSE = "/vector/supermarket_response"
    const val SUPERMARKET_TOGGLE = "/vector/supermarket_toggle"

    /** ChannelClient path prefix; file name is appended (e.g. `/vector/audio/watch_….m4a`). */
    const val AUDIO_CHANNEL_PREFIX = "/vector/audio/"
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

@Serializable
enum class WearMotionBucket {
    OVERDUE,
    TODAY,
    UPCOMING,
    SOMEDAY
}

@Serializable
data class WearMotionTask(
    val id: String,
    val title: String,
    val priority: String = "NONE",
    val dueAt: Long? = null,
    val bucket: WearMotionBucket = WearMotionBucket.SOMEDAY
)

@Serializable
data class MotionForWearPayload(
    val tasks: List<WearMotionTask> = emptyList()
)

@Serializable
data class MotionCompletePayload(
    val taskId: String
)

@Serializable
enum class WearFoodState {
    NONE,
    EATEN,
    SKIPPED
}

@Serializable
data class WearFoodItem(
    val id: String,
    val name: String,
    val plannedTime: String? = null,
    val amount: Double? = null,
    val unit: String = "",
    val kcal: Double = 0.0,
    val protein: Double = 0.0,
    val carbs: Double = 0.0,
    val fats: Double = 0.0,
    val state: WearFoodState = WearFoodState.NONE
)

@Serializable
data class FoodDayForWearPayload(
    val date: String,
    val items: List<WearFoodItem> = emptyList(),
    val hasPreset: Boolean = true
)

@Serializable
data class FoodStatusSetPayload(
    val date: String,
    val entryId: String,
    val state: WearFoodState
)

/** Mirrors [com.vm.core.models.ActivityCompletion] by name. */
@Serializable
enum class WearActivityCompletion {
    NONE,
    DONE,
    NOT_DONE,
    CANCELLED,
    NA
}

/** One arc of the day's 24h routine; times are effective HH:mm. Sleep arcs are read-only. */
@Serializable
data class WearRoutineActivity(
    val id: String,
    val title: String,
    val start: String,
    val end: String,
    val colorArgb: Int,
    val labelName: String = "",
    val completion: WearActivityCompletion = WearActivityCompletion.NONE,
    val isSleep: Boolean = false
)

@Serializable
data class ActivitiesForWearPayload(
    val date: String,
    val activities: List<WearRoutineActivity> = emptyList()
)

@Serializable
data class ActivityCompletionSetPayload(
    val date: String,
    val activityId: String,
    val completion: WearActivityCompletion
)

/**
 * Supermarket list item. List item ids are regenerated on every phone load,
 * so items are addressed by [index] in the file plus [title] as a guard.
 */
@Serializable
data class WearSupermarketItem(
    val index: Int,
    val title: String,
    val aisle: String = "Other",
    val isChecked: Boolean = false,
    val urgency: String = "URGENT",
    val remaining: Double? = null,
    val quantity: Double? = null,
    val unit: String? = null
)

@Serializable
data class SupermarketForWearPayload(
    val listName: String = "",
    val available: Boolean = true,
    val items: List<WearSupermarketItem> = emptyList()
)

@Serializable
data class SupermarketTogglePayload(
    val index: Int,
    val title: String,
    val isChecked: Boolean
)
