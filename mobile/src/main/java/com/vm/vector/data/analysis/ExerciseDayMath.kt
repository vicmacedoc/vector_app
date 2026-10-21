package com.vm.vector.data.analysis

import com.vm.core.models.WorkoutSet
import com.vm.core.models.durationToMinutes
import com.vm.core.models.isDropRow
import com.vm.vector.data.WorkoutRepository
import java.util.Locale

enum class ExerciseBucket(val label: String) {
    RESISTANCE("Resistance"),
    RUN("Run"),
    BIKE("Bike"),
    SWIM("Swim"),
    ENDURANCE("Endurance"),
    OTHER("Other"),
    ;

    val group: ExerciseGroup
        get() = when (this) {
            RESISTANCE -> ExerciseGroup.RESISTANCE
            RUN, BIKE, SWIM, ENDURANCE -> ExerciseGroup.ENDURANCE
            OTHER -> ExerciseGroup.OTHER
        }
}

/** The three fixed day-summary markers; Endurance sums every sport. */
enum class ExerciseGroup(val label: String) {
    RESISTANCE("Resistance"),
    ENDURANCE("Endurance"),
    OTHER("Other"),
}

data class ExerciseProgress(val done: Double, val planned: Double) {
    operator fun plus(other: ExerciseProgress) = ExerciseProgress(done + other.done, planned + other.planned)

    companion object {
        val ZERO = ExerciseProgress(0.0, 0.0)
    }
}

data class BucketMinutes(val bucket: ExerciseBucket, val minutes: ExerciseProgress)

data class GroupMinutes(val group: ExerciseGroup, val minutes: ExerciseProgress)

data class MuscleVolumeProgress(val muscle: String, val sets: ExerciseProgress)

/** One exercise of the day with its done / planned minutes. */
data class ExerciseActivityProgress(
    val name: String,
    val bucket: ExerciseBucket,
    val minutes: ExerciseProgress,
)

data class ExerciseDaySummary(
    val minutes: ExerciseProgress,
    val byBucket: List<BucketMinutes>,
    val muscles: List<MuscleVolumeProgress>,
    /** Always one entry per [ExerciseGroup], in enum order; 0 / 0 when nothing is planned. */
    val byGroup: List<GroupMinutes> = ExerciseGroup.entries.map { GroupMinutes(it, ExerciseProgress.ZERO) },
    val activities: List<ExerciseActivityProgress> = emptyList(),
) {
    val isEmpty: Boolean get() = byBucket.isEmpty()
}

/**
 * Day totals for the Exercise calendar, derived from the in-memory rows so every edit,
 * status change and watch merge is reflected immediately.
 *
 * - Planned minutes per exercise = `totalDuration` (fallback: interval/set durations plus rests).
 * - Exercises added on top of the plan add their done minutes to the day and category totals, but
 *   not their planned minutes, so day and category targets reflect the plan only.
 * - Done minutes = the watch-measured `actualTotalDuration` when present, otherwise
 *   planned × completed share of main sets / intervals.
 * - A unit's completion credit is its own `isCompleted` once any unit of the exercise has one;
 *   otherwise the exercise status decides (Done/Exceeded = 1, Partial = 0.5).
 * - Muscle volume: weighted set-equivalents of main sets (drops excluded).
 */
object ExerciseDayMath {

    fun summarize(sets: List<WorkoutSet>): ExerciseDaySummary {
        val exercises = sets
            .filterNot { WorkoutRepository.isRestDayAcknowledgment(it) || it.exerciseType == "REST_ACK" }
            .groupBy { it.exerciseId }
            .values
        val bucketTotals = LinkedHashMap<ExerciseBucket, ExerciseProgress>()
        val muscleTotals = LinkedHashMap<String, ExerciseProgress>()
        val activities = mutableListOf<ExerciseActivityProgress>()
        exercises.forEach { rows ->
            val mains = rows.filter { !it.isDropRow }
            if (mains.isEmpty()) return@forEach
            val credits = completionCredits(mains)
            val planned = plannedMinutes(rows)
            val share = if (mains.isEmpty()) 0.0 else credits.sum() / mains.size
            val measured = rows.firstNotNullOfOrNull { it.actualTotalDuration }?.takeIf { it > 0 }
            val done = measured?.let { it / 60.0 } ?: (planned * share)
            val first = rows.first()
            val bucket = bucketOf(first)
            val target = if (WorkoutRepository.isAddedExercise(first.exerciseId)) 0.0 else planned
            bucketTotals[bucket] = (bucketTotals[bucket] ?: ExerciseProgress.ZERO) + ExerciseProgress(done, target)
            activities += ExerciseActivityProgress(
                name = first.exerciseName.ifBlank { first.sessionTitle.ifBlank { bucket.label } },
                bucket = bucket,
                minutes = ExerciseProgress(done, planned),
            )

            mains.forEachIndexed { index, set ->
                if (!TrainingVolumeMath.isResistance(set)) return@forEachIndexed
                TrainingVolumeMath.targetWeights(set).forEach { (muscle, weight) ->
                    val add = ExerciseProgress(done = weight * credits[index], planned = weight)
                    muscleTotals[muscle] = (muscleTotals[muscle] ?: ExerciseProgress.ZERO) + add
                }
            }
        }
        val byBucket = ExerciseBucket.entries.mapNotNull { bucket ->
            bucketTotals[bucket]?.let { BucketMinutes(bucket, it) }
        }
        val total = byBucket.fold(ExerciseProgress.ZERO) { acc, b -> acc + b.minutes }
        val muscles = muscleTotals
            .map { (muscle, progress) -> MuscleVolumeProgress(muscle, progress) }
            .sortedWith(compareByDescending<MuscleVolumeProgress> { it.sets.planned }.thenBy { it.muscle })
        val byGroup = ExerciseGroup.entries.map { group ->
            GroupMinutes(
                group,
                byBucket.filter { it.bucket.group == group }
                    .fold(ExerciseProgress.ZERO) { acc, b -> acc + b.minutes },
            )
        }
        return ExerciseDaySummary(total, byBucket, muscles, byGroup, activities)
    }

    fun bucketOf(set: WorkoutSet): ExerciseBucket = when (set.exerciseType.uppercase(Locale.US)) {
        "RESISTANCE" -> ExerciseBucket.RESISTANCE
        "ENDURANCE" -> when (set.sport?.uppercase(Locale.US)) {
            "RUN" -> ExerciseBucket.RUN
            "BIKE" -> ExerciseBucket.BIKE
            "SWIM" -> ExerciseBucket.SWIM
            else -> ExerciseBucket.ENDURANCE
        }
        else -> ExerciseBucket.OTHER
    }

    fun plannedMinutes(rows: List<WorkoutSet>): Double {
        rows.firstNotNullOfOrNull { row -> row.totalDuration?.let { durationToMinutes(it, row.unitTotalDuration) } }
            ?.let { return it }
        return rows.filter { !it.isDropRow }.sumOf { row ->
            val work = row.targetDuration?.let { durationToMinutes(it.toDouble(), row.unitDuration) } ?: 0.0
            val rest = row.targetRest?.let { durationToMinutes(it.toDouble(), row.restUnit) } ?: 0.0
            work + rest
        }
    }

    /** One credit (0..1) per main set / interval, aligned with [mains]. */
    fun completionCredits(mains: List<WorkoutSet>): List<Double> {
        if (mains.any { it.isCompleted }) return mains.map { if (it.isCompleted) 1.0 else 0.0 }
        return mains.map { statusCredit(it.workoutStatus) }
    }

    private fun statusCredit(status: String?): Double = when (status?.trim()?.lowercase(Locale.US)) {
        "done", "exceeded", "exceed" -> 1.0
        "partial" -> 0.5
        else -> 0.0
    }
}
