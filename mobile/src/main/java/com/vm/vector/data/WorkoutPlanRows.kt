package com.vm.vector.data

import com.vm.core.models.MuscleTargets
import com.vm.core.models.WeeklyWorkoutPlan
import com.vm.core.models.WorkoutDaySession
import com.vm.core.models.WorkoutDropPlan
import com.vm.core.models.WorkoutExercisePlan
import com.vm.core.models.WorkoutIntervalPlan
import com.vm.core.models.WorkoutSet
import com.vm.core.models.WorkoutSetPlan
import com.vm.core.models.WorkoutSetType
import com.vm.core.models.durationToMinutes
import com.vm.core.models.isDropRow
import com.vm.core.models.isInterval
import java.util.Calendar
import java.util.Locale

/**
 * Converts preset JSON (template v2) to [WorkoutSet] rows and back.
 * One row per main set, per drop (`…_d{n}`), per interval, or a single row for OTHER exercises.
 */
object WorkoutPlanRows {

    fun sessionsForDate(plan: WeeklyWorkoutPlan, date: String): List<WorkoutDaySession> {
        val parts = date.split("-")
        require(parts.size == 3) { "Invalid date format: $date" }
        val year = requireNotNull(parts[0].toIntOrNull()) { "Invalid year: ${parts[0]}" }
        val month = requireNotNull(parts[1].toIntOrNull()?.minus(1)) { "Invalid month: ${parts[1]}" }
        val day = requireNotNull(parts[2].toIntOrNull()) { "Invalid day: ${parts[2]}" }
        val cal = Calendar.getInstance(Locale.US).apply {
            set(Calendar.YEAR, year)
            set(Calendar.MONTH, month)
            set(Calendar.DAY_OF_MONTH, day)
        }
        return when (cal.get(Calendar.DAY_OF_WEEK)) {
            Calendar.MONDAY -> plan.weekly_plan.monday
            Calendar.TUESDAY -> plan.weekly_plan.tuesday
            Calendar.WEDNESDAY -> plan.weekly_plan.wednesday
            Calendar.THURSDAY -> plan.weekly_plan.thursday
            Calendar.FRIDAY -> plan.weekly_plan.friday
            Calendar.SATURDAY -> plan.weekly_plan.saturday
            Calendar.SUNDAY -> plan.weekly_plan.sunday
            else -> emptyList()
        }
    }

    /** Preset rows for [date]; exercise ids are `ex_{session}_{exercise}`. */
    fun planToRows(plan: WeeklyWorkoutPlan, date: String): List<WorkoutSet> =
        sessionsForDate(plan, date).flatMapIndexed { sessionIndex, session ->
            sessionToRows(date, session, idPrefix = "ex_$sessionIndex")
        }

    /** Rows for one session; exercise ids are `{idPrefix}_{exerciseIndex}`. */
    fun sessionToRows(
        date: String,
        session: WorkoutDaySession,
        idPrefix: String,
        sessionTypeOverride: String? = null,
    ): List<WorkoutSet> {
        val sessionType = (sessionTypeOverride ?: session.type).ifBlank { "RESISTANCE" }
        return session.exercises.flatMapIndexed { exerciseIndex, exercise ->
            exerciseToRows(
                date = date,
                exerciseId = "${idPrefix}_$exerciseIndex",
                sessionTitle = session.title,
                sessionDescription = session.description,
                sessionType = sessionType,
                sport = session.sport,
                exercise = exercise,
            )
        }
    }

    fun exerciseToRows(
        date: String,
        exerciseId: String,
        sessionTitle: String,
        sessionDescription: String,
        sessionType: String,
        sport: String?,
        exercise: WorkoutExercisePlan,
    ): List<WorkoutSet> {
        val base = WorkoutSet(
            id = "${date}_${exerciseId}_1",
            exerciseId = exerciseId,
            setNumber = 1,
            date = date,
            sessionTitle = sessionTitle,
            sessionDescription = sessionDescription,
            exerciseName = exercise.name,
            exerciseDescription = exercise.description,
            timing = exercise.timing,
            exerciseType = sessionType,
            groupingId = exercise.supersetGroup?.trim()?.uppercase(Locale.US)?.ifBlank { null },
            sport = sport?.trim()?.uppercase(Locale.US)?.ifBlank { null },
            stroke = exercise.stroke?.trim()?.uppercase(Locale.US)?.ifBlank { null },
            totalDuration = exercise.plannedTotalMinutes(),
            unitTotalDuration = "min",
            targetMuscles = MuscleTargets.format(exercise.target),
        )
        return when {
            exercise.intervals.isNotEmpty() -> exercise.intervals.map { interval ->
                base.copy(
                    id = "${date}_${exerciseId}_${interval.intervalNumber}",
                    setNumber = interval.intervalNumber,
                    setType = WorkoutSetType.INTERVAL,
                    description = interval.description,
                    targetDuration = interval.targetDuration,
                    unitDuration = interval.unitDuration,
                    targetDistance = interval.targetDistance,
                    unitDistance = interval.unitDistance,
                    targetZone = interval.targetZone,
                    targetRpe = interval.targetRpe,
                    targetCadence = interval.targetCadence,
                    unitCadence = interval.unitCadence,
                    targetRest = interval.rest,
                    restUnit = interval.restUnit,
                )
            }
            exercise.sets.isNotEmpty() -> exercise.sets.flatMap { setPlan -> setRows(base, date, exerciseId, setPlan) }
            else -> listOf(
                // OTHER (and legacy exercise-level ENDURANCE): work time is the exercise total.
                base.copy(
                    description = exercise.description,
                    unitDuration = if (exercise.targetDuration != null) exercise.durationUnit else "min",
                    targetDuration = exercise.targetDuration,
                    targetDistance = exercise.targetDistance,
                    unitDistance = exercise.unitDistance,
                    targetRpe = exercise.targetRpe,
                    targetRest = exercise.rest,
                    restUnit = exercise.restUnit,
                )
            )
        }
    }

    private fun setRows(base: WorkoutSet, date: String, exerciseId: String, setPlan: WorkoutSetPlan): List<WorkoutSet> {
        val isDrop = setPlan.drops.isNotEmpty() || setPlan.setType.equals(WorkoutSetType.DROP, ignoreCase = true)
        val mainId = "${date}_${exerciseId}_${setPlan.setNumber}"
        val main = base.copy(
            id = mainId,
            setNumber = setPlan.setNumber,
            setType = if (isDrop) WorkoutSetType.DROP else WorkoutSetType.NORMAL,
            description = setPlan.description,
            cadence = setPlan.cadence,
            unitLoad = setPlan.unitLoad,
            unitDuration = setPlan.unitDuration,
            unitDistance = setPlan.unitDistance,
            unitVelocity = setPlan.unitVelocity,
            targetLoad = setPlan.targetLoad,
            targetReps = setPlan.targetReps,
            targetDuration = setPlan.targetDuration,
            targetDistance = setPlan.targetDistance,
            targetVelocity = setPlan.targetVelocity,
            targetRir = setPlan.targetRir,
            targetRest = setPlan.rest,
            restUnit = setPlan.restUnit,
        )
        val drops = setPlan.drops.sortedBy { it.dropNumber }.mapIndexed { index, drop ->
            val dropNumber = drop.dropNumber.takeIf { it > 0 } ?: (index + 1)
            main.copy(
                id = "${mainId}_d$dropNumber",
                dropNumber = dropNumber,
                description = drop.description,
                cadence = null,
                unitLoad = drop.unitLoad ?: setPlan.unitLoad,
                targetLoad = drop.targetLoad,
                targetReps = drop.targetReps,
                targetDuration = null,
                targetDistance = null,
                targetVelocity = null,
                targetRir = drop.targetRir,
                targetRest = null,
            )
        }
        return listOf(main) + drops
    }

    /** Inverse of [exerciseToRows] for one exercise's rows (any order). */
    fun rowsToExercisePlan(rows: List<WorkoutSet>): WorkoutExercisePlan {
        val sorted = rows.sortedWith(compareBy<WorkoutSet> { it.setNumber }.thenBy { it.dropNumber })
        val r0 = sorted.first()
        val mains = sorted.filter { !it.isDropRow }
        val isIntervals = sorted.any { it.isInterval }
        val isResistance = r0.exerciseType.equals("RESISTANCE", ignoreCase = true)
        val intervals = if (isIntervals) {
            mains.map { row ->
                WorkoutIntervalPlan(
                    intervalNumber = row.setNumber,
                    description = row.description,
                    targetDuration = row.targetDuration,
                    unitDuration = row.unitDuration,
                    targetDistance = row.targetDistance,
                    unitDistance = row.unitDistance,
                    targetZone = row.targetZone,
                    targetRpe = row.targetRpe,
                    targetCadence = row.targetCadence,
                    unitCadence = row.unitCadence,
                    rest = row.targetRest,
                    restUnit = row.restUnit,
                )
            }
        } else {
            emptyList()
        }
        val sets = if (!isIntervals && (isResistance || mains.size > 1)) {
            mains.map { main ->
                val drops = sorted.filter { it.isDropRow && it.setNumber == main.setNumber }
                WorkoutSetPlan(
                    setNumber = main.setNumber,
                    description = main.description,
                    cadence = main.cadence,
                    setType = if (drops.isNotEmpty() || main.setType == WorkoutSetType.DROP) {
                        WorkoutSetType.DROP
                    } else {
                        WorkoutSetType.NORMAL
                    },
                    targetLoad = main.targetLoad,
                    unitLoad = main.unitLoad,
                    targetReps = main.targetReps,
                    targetVelocity = main.targetVelocity,
                    unitVelocity = main.unitVelocity,
                    targetDuration = main.targetDuration,
                    unitDuration = main.unitDuration,
                    targetDistance = main.targetDistance,
                    unitDistance = main.unitDistance,
                    targetRir = main.targetRir,
                    drops = drops.map { drop ->
                        WorkoutDropPlan(
                            dropNumber = drop.dropNumber,
                            description = drop.description,
                            targetLoad = drop.targetLoad,
                            unitLoad = drop.unitLoad,
                            targetReps = drop.targetReps,
                            targetRir = drop.targetRir,
                        )
                    },
                    rest = main.targetRest,
                    restUnit = main.restUnit,
                )
            }
        } else {
            emptyList()
        }
        val single = !isIntervals && sets.isEmpty()
        val totalMinutes = r0.totalDuration?.let { durationToMinutes(it, r0.unitTotalDuration) }
        return WorkoutExercisePlan(
            name = r0.exerciseName,
            type = r0.exerciseType.ifBlank { "RESISTANCE" },
            target = MuscleTargets.parse(r0.targetMuscles),
            supersetGroup = r0.groupingId,
            description = r0.exerciseDescription.ifBlank { if (single) r0.description else "" },
            timing = r0.timing,
            totalDuration = totalMinutes,
            unitDuration = if (totalMinutes != null) "min" else r0.unitDuration.takeIf { single },
            stroke = r0.stroke,
            targetDistance = if (single) r0.targetDistance else null,
            unitDistance = r0.unitDistance,
            targetDuration = if (single && totalMinutes == null) r0.targetDuration else null,
            rest = if (single) r0.targetRest else null,
            restUnit = r0.restUnit,
            targetRpe = if (single) r0.targetRpe else null,
            sets = sets,
            intervals = intervals,
        )
    }

    /** Groups rows by exercise in first-appearance order and rebuilds a session. */
    fun rowsToSession(rows: List<WorkoutSet>): WorkoutDaySession {
        val first = rows.first()
        val exercises = rows.map { it.exerciseId }.distinct().mapNotNull { exId ->
            rows.filter { it.exerciseId == exId }.takeIf { it.isNotEmpty() }?.let { rowsToExercisePlan(it) }
        }
        val sessionType = first.exerciseType.ifBlank { exercises.firstOrNull()?.type ?: "RESISTANCE" }
        return WorkoutDaySession(
            title = first.sessionTitle.ifBlank { "Workout" },
            description = first.sessionDescription,
            type = sessionType,
            sport = first.sport,
            exercises = exercises,
        )
    }
}
