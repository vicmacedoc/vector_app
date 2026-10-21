package com.vm.core.models

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

/** Row kinds stored in [WorkoutSet.setType]. */
object WorkoutSetType {
    const val NORMAL = "NORMAL"
    /** Main set of a drop set; its drops are separate rows with [WorkoutSet.dropNumber] > 0. */
    const val DROP = "DROP"
    /** One ENDURANCE interval. */
    const val INTERVAL = "INTERVAL"
}

@Entity(
    tableName = "workout_sets",
    indices = [Index(value = ["date"], name = "index_workout_sets_date")]
)
@Serializable
data class WorkoutSet(
    /** `date_exerciseId_setNumber`; drop rows append `_d{dropNumber}`. */
    @PrimaryKey val id: String,
    val exerciseId: String,
    val setNumber: Int,
    val date: String,
    val sessionTitle: String = "",
    val sessionDescription: String = "",
    val exerciseName: String = "",
    val exerciseDescription: String = "",
    val timing: String? = null,
    val exerciseType: String = "RESISTANCE",
    /** Superset group letter; consecutive exercises sharing it run round-robin. */
    val groupingId: String? = null,
    val workoutStatus: String? = null, // "Done", "Partial", "Exceed"
    val description: String = "", 
    val cadence: String? = null,
    /** ENDURANCE session sport: RUN | BIKE | SWIM. */
    val sport: String? = null,
    /** Swim stroke for the exercise. */
    val stroke: String? = null,
    val setType: String = WorkoutSetType.NORMAL,
    /** 0 = main set or interval; 1.. = drop following set [setNumber]. */
    val dropNumber: Int = 0,
    // Units
    val unitLoad: String = "kg",
    val unitDuration: String = "s",
    val unitDistance: String = "km",
    val unitVelocity: String = "m/s",
    val unitCadence: String = "rpm",
    // Planned Targets (Pulled from JSON)
    val targetLoad: Double? = null,
    val targetReps: Int? = null,
    val targetDuration: Int? = null,
    val targetDistance: Double? = null,
    val targetVelocity: Double? = null,
    val targetRir: Int? = null,
    val targetRest: Int? = null,
    val restUnit: String = "s",
    val targetZone: Int? = null,
    val targetRpe: Double? = null,
    val targetCadence: Int? = null,
    /** Planned time for the whole exercise (copied onto each of its rows), in [unitTotalDuration]. */
    val totalDuration: Double? = null,
    val unitTotalDuration: String = "min",
    // Actual Results (Entered by Victor)
    val actualLoad: Double? = null,
    val actualReps: Int? = null,
    /** Seconds. */
    val actualDuration: Int? = null,
    val actualDistance: Double? = null,
    val actualVelocity: Double? = null,
    val actualRpe: Double? = null,
    val actualRir: Int? = null,
    val actualRest: Int? = null,
    val actualCadence: Int? = null,
    /** Seconds the watch measured for the whole exercise (same value on each of its rows). */
    val actualTotalDuration: Int? = null,
    val isCompleted: Boolean = false,
    /** Weighted muscle tags `back:1.0,biceps:0.5` (legacy: ordered CSV). See [MuscleTargets]. */
    val targetMuscles: String = ""
)

val WorkoutSet.isDropRow: Boolean get() = dropNumber > 0

val WorkoutSet.isInterval: Boolean get() = setType == WorkoutSetType.INTERVAL
