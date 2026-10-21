package com.vm.vector.data.analysis

import com.vm.core.models.MuscleTargets
import com.vm.core.models.WorkoutSet
import com.vm.core.models.isDropRow
import java.util.Locale

/**
 * Preset `target` maps are stored on each [WorkoutSet] as weighted tags (`back:1.0,biceps:0.5`).
 * Each completed main set gives every target muscle its weight in set-equivalents. Legacy unweighted
 * CSV reads as first tag = 1.0, the rest = 0.5.
 *
 * Drop rows add to tonnage but not to set volume: a main set plus its drops counts as one set.
 */
object TrainingVolumeMath {

    fun orderedTargetTags(targetMusclesCsv: String): List<String> =
        MuscleTargets.parse(targetMusclesCsv).keys.toList()

    fun targetWeights(set: WorkoutSet): Map<String, Double> = MuscleTargets.parse(set.targetMuscles)

    fun isResistance(set: WorkoutSet): Boolean =
        set.exerciseType.equals("RESISTANCE", ignoreCase = true)

    /** Set-equivalents [muscle] gets from [set] if it were completed; 0 for drops and non-resistance rows. */
    fun plannedLoadForMuscle(set: WorkoutSet, muscle: String): Double {
        if (!isResistance(set) || set.isDropRow) return 0.0
        return targetWeights(set)[muscle.lowercase(Locale.US)] ?: 0.0
    }

    /**
     * Fractional "set equivalents" this [muscle] receives from one completed resistance set.
     * Returns 0 if not completed, not RESISTANCE, a drop row, or muscle not in the target map.
     */
    fun fractionalLoadForMuscle(set: WorkoutSet, muscle: String): Double {
        if (!AnalysisStats.workoutSetCountsAsCompleted(set)) return 0.0
        return plannedLoadForMuscle(set, muscle)
    }

    /** Load × reps for a completed set, the standard tonnage proxy for training stress. */
    fun tonnage(set: WorkoutSet): Double {
        if (!AnalysisStats.workoutSetCountsAsCompleted(set)) return 0.0
        val load = set.actualLoad ?: return 0.0
        val reps = set.actualReps ?: return 0.0
        if (load <= 0.0 || reps <= 0) return 0.0
        return load * reps
    }

    /**
     * Epley estimated one-rep max: load × (1 + reps / 30).
     * Null when the set has no usable load/rep pair, was not completed, or is a drop.
     */
    fun estimatedOneRepMax(set: WorkoutSet): Double? {
        if (set.isDropRow) return null
        if (!AnalysisStats.workoutSetCountsAsCompleted(set)) return null
        val load = set.actualLoad ?: return null
        val reps = set.actualReps ?: return null
        if (load <= 0.0 || reps <= 0) return null
        return load * (1.0 + reps / 30.0)
    }
}
