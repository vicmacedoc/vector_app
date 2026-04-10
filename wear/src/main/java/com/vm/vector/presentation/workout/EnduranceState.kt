package com.vm.vector.presentation.workout

import com.vm.core.models.WorkoutSet

/**
 * One exercise in an endurance/other workout: same exerciseId and its sets in order.
 */
data class EnduranceExercise(
    val exerciseId: String,
    val exerciseName: String,
    val sets: List<WorkoutSet>
)

fun SessionItem.toEnduranceExercises(): List<EnduranceExercise> =
    sets.groupBy { it.exerciseId }.map { (id, list) ->
        val first = list.first()
        EnduranceExercise(
            exerciseId = id,
            exerciseName = first.exerciseName,
            sets = list.sortedBy { it.setNumber }
        )
    }
