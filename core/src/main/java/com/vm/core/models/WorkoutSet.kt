package com.vm.core.models

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Entity(
    tableName = "workout_sets",
    indices = [Index(value = ["date"], name = "index_workout_sets_date")]
)
@Serializable
data class WorkoutSet(
    @PrimaryKey val id: String, // format: "date_exerciseId_setNumber"
    val exerciseId: String,
    val setNumber: Int,
    val date: String,
    val sessionTitle: String = "",
    val sessionDescription: String = "",
    val exerciseName: String = "",
    val exerciseDescription: String = "",
    val timing: String? = null,
    val exerciseType: String = "RESISTANCE",
    val groupingId: String? = null,
    val workoutStatus: String? = null, // "Done", "Partial", "Exceed"
    val description: String = "", 
    val cadence: String? = null,
    // Units
    val unitLoad: String = "kg",
    val unitDuration: String = "s",
    val unitDistance: String = "km",
    val unitVelocity: String = "m/s",
    // Planned Targets (Pulled from JSON)
    val targetLoad: Double? = null,
    val targetReps: Int? = null,
    val targetDuration: Int? = null,
    val targetDistance: Double? = null,
    val targetVelocity: Double? = null,
    val targetRir: Int? = null,
    val targetRest: Int? = null,
    val restUnit: String = "s",
    // Actual Results (Entered by Victor)
    val actualLoad: Double? = null,
    val actualReps: Int? = null,
    val actualDuration: Int? = null,
    val actualDistance: Double? = null,
    val actualVelocity: Double? = null,
    val actualRpe: Double? = null,
    val actualRir: Int? = null,
    val actualRest: Int? = null,
    val isCompleted: Boolean = false,
    /** Comma-separated muscle tags from exercise `target` in preset (Training Volume / analysis). */
    val targetMuscles: String = ""
)