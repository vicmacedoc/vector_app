package com.vm.vector.presentation.workout

import com.vm.core.models.WorkoutSet
import com.vm.core.models.isDropRow

/** One exercise in a resistance workout: its main sets in order, each with its drops. */
data class ResistanceExercise(
    val exerciseId: String,
    val exerciseName: String,
    val description: String,
    val groupingId: String?,
    val mains: List<WorkoutSet>,
    /** Drops keyed by the main set's [WorkoutSet.setNumber]. */
    val drops: Map<Int, List<WorkoutSet>>,
) {
    val rowIds: List<String> get() = mains.map { it.id } + drops.values.flatten().map { it.id }

    /** Planned minutes, used to split a superset's measured time between its members. */
    val plannedMinutes: Double? get() = mains.firstOrNull()?.totalDuration?.takeIf { it > 0.0 }
}

/** One unit of work: a main set and its drops, done back to back before resting. */
data class ResistanceStep(
    val exercise: ResistanceExercise,
    /** Position inside a superset (0 = A1). */
    val memberIndex: Int,
    val main: WorkoutSet,
    val drops: List<WorkoutSet>,
    /** 1-based round (= set number within the exercise). */
    val round: Int,
) {
    val rowIds: List<String> get() = listOf(main.id) + drops.map { it.id }
}

/**
 * A carousel entry: one exercise, or a superset of consecutive exercises sharing a grouping letter.
 * Superset steps are interleaved round by round (A1, A2, A1, A2, ...).
 */
data class ResistanceBlock(
    val groupingId: String?,
    val exercises: List<ResistanceExercise>,
    val steps: List<ResistanceStep>,
) {
    val isSuperset: Boolean get() = exercises.size > 1
    val totalRounds: Int get() = exercises.maxOfOrNull { it.mains.size } ?: 0
    val rowIds: Set<String> get() = exercises.flatMap { it.rowIds }.toSet()

    val title: String
        get() = if (isSuperset) {
            "${supersetKind(exercises.size)} ${groupingId.orEmpty()}".trim()
        } else {
            exercises.first().exerciseName
        }

    fun badge(step: ResistanceStep): String? =
        if (isSuperset) "${groupingId.orEmpty()}${step.memberIndex + 1}" else null
}

fun supersetKind(size: Int): String = when (size) {
    2 -> "Superset"
    3 -> "Tri-set"
    else -> "Giant set"
}

fun SessionItem.toResistanceExercises(): List<ResistanceExercise> =
    sets.groupBy { it.exerciseId }
        .entries
        .sortedBy { (id, _) -> id.substringAfterLast('_').toIntOrNull() ?: 0 }
        .mapNotNull { (id, rows) ->
            val mains = rows.filter { !it.isDropRow }.sortedBy { it.setNumber }
            val first = mains.firstOrNull() ?: return@mapNotNull null
            ResistanceExercise(
                exerciseId = id,
                exerciseName = first.exerciseName,
                description = first.exerciseDescription,
                groupingId = first.groupingId?.trim()?.uppercase()?.ifBlank { null },
                mains = mains,
                drops = rows.filter { it.isDropRow }
                    .sortedBy { it.dropNumber }
                    .groupBy { it.setNumber },
            )
        }

fun SessionItem.toResistanceBlocks(): List<ResistanceBlock> {
    val exercises = toResistanceExercises()
    val blocks = mutableListOf<ResistanceBlock>()
    var start = 0
    while (start < exercises.size) {
        val group = exercises[start].groupingId
        var end = start + 1
        if (group != null) {
            while (end < exercises.size && exercises[end].groupingId == group) end++
        }
        val members = exercises.subList(start, end).toList()
        blocks += buildBlock(group.takeIf { members.size > 1 }, members)
        start = end
    }
    return blocks
}

private fun buildBlock(groupingId: String?, members: List<ResistanceExercise>): ResistanceBlock {
    val rounds = members.maxOf { it.mains.size }
    val steps = buildList {
        for (round in 0 until rounds) {
            members.forEachIndexed { memberIndex, exercise ->
                val main = exercise.mains.getOrNull(round) ?: return@forEachIndexed
                add(
                    ResistanceStep(
                        exercise = exercise,
                        memberIndex = memberIndex,
                        main = main,
                        drops = exercise.drops[main.setNumber].orEmpty(),
                        round = round + 1,
                    )
                )
            }
        }
    }
    return ResistanceBlock(groupingId, members, steps)
}

/** "Done" when every main set is concluded, "Partial" when some are, null when none. */
fun exerciseStatus(rows: List<WorkoutSet>): String? {
    val mains = rows.filter { !it.isDropRow }
    val done = mains.count { it.isCompleted }
    return when {
        mains.isEmpty() || done == 0 -> null
        done == mains.size -> "Done"
        else -> "Partial"
    }
}

/** Planned rest after [set] in seconds; unset rest defaults to 60 s like the legacy flow. */
fun restSeconds(set: WorkoutSet): Int = when (set.restUnit.lowercase()) {
    "min", "minute", "minutes" -> (set.targetRest ?: 1) * 60
    else -> set.targetRest ?: 60
}

/** Converts measured rest seconds back into the set's rest unit. */
fun restInUnit(seconds: Int, restUnit: String): Int = when (restUnit.lowercase()) {
    "min", "minute", "minutes" -> seconds / 60
    else -> seconds
}

/**
 * Splits [elapsedSec] between superset members in proportion to their planned minutes
 * (equal shares when nothing is planned).
 */
fun splitElapsed(elapsedSec: Int, exercises: List<ResistanceExercise>): Map<String, Int> {
    if (exercises.isEmpty()) return emptyMap()
    val weights = exercises.map { it.plannedMinutes ?: 0.0 }
    val total = weights.sum()
    return exercises.mapIndexed { index, exercise ->
        val share = if (total > 0.0) weights[index] / total else 1.0 / exercises.size
        exercise.exerciseId to (elapsedSec * share).toInt()
    }.toMap()
}
