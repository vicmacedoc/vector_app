package com.vm.vector.data.analysis

import com.vm.core.models.WorkoutSet
import java.util.Locale

/**
 * Preset `target` lists are stored on each [WorkoutSet] as comma-separated muscles (order preserved).
 * First tag = primary (1.0× per completed set); each additional tag = secondary (0.5× per completed set).
 */
object TrainingVolumeMath {

    fun orderedTargetTags(targetMusclesCsv: String): List<String> =
        targetMusclesCsv.split(',').map { it.trim().lowercase(Locale.US) }.filter { it.isNotEmpty() }

    /**
     * Fractional "set equivalents" this [muscle] receives from one completed resistance set.
     * Returns 0 if not completed, not RESISTANCE, or muscle not in the target list.
     */
    fun fractionalLoadForMuscle(set: WorkoutSet, muscle: String): Double {
        if (!AnalysisStats.workoutSetCountsAsCompleted(set)) return 0.0
        if (!set.exerciseType.equals("RESISTANCE", ignoreCase = true)) return 0.0
        val m = muscle.lowercase(Locale.US)
        val tags = orderedTargetTags(set.targetMuscles)
        if (tags.isEmpty()) return 0.0
        var total = 0.0
        tags.forEachIndexed { index, tag ->
            if (tag == m) {
                total += if (index == 0) 1.0 else 0.5
            }
        }
        return total
    }
}
