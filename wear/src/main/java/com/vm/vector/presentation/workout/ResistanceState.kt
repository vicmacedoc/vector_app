package com.vm.vector.presentation.workout

import com.vm.core.models.WorkoutSet

/**
 * One exercise in a resistance workout: same exerciseId and its sets in order.
 */
data class ResistanceExercise(
    val exerciseId: String,
    val exerciseName: String,
    val sets: List<WorkoutSet>
)

fun SessionItem.toResistanceExercises(): List<ResistanceExercise> =
    sets.groupBy { it.exerciseId }.map { (id, list) ->
        val first = list.first()
        ResistanceExercise(
            exerciseId = id,
            exerciseName = first.exerciseName,
            sets = list.sortedBy { it.setNumber }
        )
    }
