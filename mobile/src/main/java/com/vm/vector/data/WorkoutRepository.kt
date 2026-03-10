package com.vm.vector.data

import android.content.Context
import kotlinx.coroutines.flow.first
import com.vm.core.models.VectorDatabase
import com.vm.core.models.WorkoutSet
import com.vm.core.models.WorkoutDaySession
import com.vm.core.models.WorkoutExercisePlan
import com.vm.core.models.WorkoutSetPlan
import com.vm.core.models.WeeklyWorkoutPlan
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.text.SimpleDateFormat
import java.util.*

/**
 * Workout blueprint loader and local persistence.
 * Blueprints: presets/workout_weekly.json on Drive. Execution data: Room workout_sets only. No Drive log.
 */
class WorkoutRepository(
    private val context: Context,
    private val database: VectorDatabase,
    private val preferenceManager: PreferenceManager,
    private val driveService: DriveService,
    private val presetStorage: PresetStorage,
) {
    private val dao = database.workoutSetDao()

    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    fun getSetsByDate(date: String): Flow<List<WorkoutSet>> = dao.getSetsByDate(date)

    suspend fun getSetsByDateSync(date: String): List<WorkoutSet> = dao.getSetsByDateSync(date)

    /**
     * Loads workout for a date for display only. Always loads blueprint and merges with existing
     * so that metadata (sessionDescription, exerciseDescription, timing) is filled when saved sets
     * have blank values. Does not persist; user taps Save Workout to write to DB.
     */
    suspend fun loadWorkoutForDateDisplay(date: String): Result<List<WorkoutSet>> = withContext(Dispatchers.IO) {
        try {
            val existing = dao.getSetsByDateSync(date)
            val blueprintSets = loadBlueprintSetsForDate(date).getOrElse { return@withContext Result.failure(it) }
            val merged = mergeBlueprintWithExisting(blueprintSets, existing, date)
            Result.success(merged)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Fetches only the preset from Drive for the given date (no DB read/write).
     * Use to replace current in-memory workout with latest preset when user taps Refresh from Drive.
     */
    suspend fun loadPresetOnlyForDate(date: String): Result<List<WorkoutSet>> = withContext(Dispatchers.IO) {
        try {
            val blueprintSets = loadBlueprintSetsForDate(date).getOrElse { return@withContext Result.failure(it) }
            Result.success(mergeBlueprintWithExisting(blueprintSets, emptyList(), date))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * When a date is selected and caller needs to persist immediately (legacy): load from Drive,
     * merge with existing Room data, then UPSERT into Room. Prefer loadWorkoutForDateDisplay for Calendar.
     */
    suspend fun loadBlueprintForDate(date: String): Result<List<WorkoutSet>> = withContext(Dispatchers.IO) {
        try {
            val existing = dao.getSetsByDateSync(date)
            val blueprintSets = loadBlueprintSetsForDate(date).getOrElse { return@withContext Result.failure(it) }
            val merged = mergeBlueprintWithExisting(blueprintSets, existing, date)
            dao.insertSets(merged)
            Result.success(dao.getSetsByDateSync(date))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Fetches preset from active preset (Settings) and returns WorkoutSet list for the given date (day of week).
     * If no preset is configured, returns failure.
     */
    private suspend fun loadBlueprintSetsForDate(date: String): Result<List<WorkoutSet>> = withContext(Dispatchers.IO) {
        try {
            val content = presetStorage.getPresetContent(PresetCategory.Workout)
                ?: return@withContext Result.failure(Exception("No workout preset active. Load a preset in Settings or from the Calendar screen."))
            parseWorkoutPlanContentToSets(content, date)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Parses preset JSON and returns workout sets for the given date. Used when applying a new preset.
     */
    fun getPresetSetsForDateFromContent(content: String, date: String): Result<List<WorkoutSet>> = try {
        parseWorkoutPlanContentToSets(content, date)
    } catch (e: Exception) {
        Result.failure(e)
    }

    private fun parseWorkoutPlanContentToSets(content: String, date: String): Result<List<WorkoutSet>> {
        return try {
            val plan = json.decodeFromString<WeeklyWorkoutPlan>(content)
            val parts = date.split("-")
            if (parts.size != 3) return Result.failure(Exception("Invalid date format: $date"))
            val year = parts[0].toIntOrNull() ?: return Result.failure(Exception("Invalid year: ${parts[0]}"))
            val month = parts[1].toIntOrNull()?.minus(1) ?: return Result.failure(Exception("Invalid month: ${parts[1]}"))
            val day = parts[2].toIntOrNull() ?: return Result.failure(Exception("Invalid day: ${parts[2]}"))
            val cal = Calendar.getInstance(Locale.US).apply {
                set(Calendar.YEAR, year)
                set(Calendar.MONTH, month)
                set(Calendar.DAY_OF_MONTH, day)
            }
            val dayOfWeek = cal.get(Calendar.DAY_OF_WEEK)
            val sessions: List<WorkoutDaySession> = when (dayOfWeek) {
                Calendar.MONDAY -> plan.weekly_plan.monday
                Calendar.TUESDAY -> plan.weekly_plan.tuesday
                Calendar.WEDNESDAY -> plan.weekly_plan.wednesday
                Calendar.THURSDAY -> plan.weekly_plan.thursday
                Calendar.FRIDAY -> plan.weekly_plan.friday
                Calendar.SATURDAY -> plan.weekly_plan.saturday
                Calendar.SUNDAY -> plan.weekly_plan.sunday
                else -> emptyList()
            }
            val sets = mutableListOf<WorkoutSet>()
            sessions.forEachIndexed { sessionIndex, session ->
                val sessionType = session.type.ifBlank { "RESISTANCE" }
                session.exercises.forEachIndexed { exerciseIndex, exercise ->
                    val exerciseId = "ex_${sessionIndex}_$exerciseIndex"
                    val exerciseType = sessionType
                    if (exercise.sets.isEmpty()) {
                        val id = "${date}_${exerciseId}_1"
                        sets.add(
                            WorkoutSet(
                                id = id,
                                exerciseId = exerciseId,
                                setNumber = 1,
                                date = date,
                                sessionTitle = session.title,
                                sessionDescription = session.description,
                                exerciseName = exercise.name,
                                exerciseDescription = exercise.description,
                                timing = exercise.timing,
                                groupingId = exercise.groupingId,
                                description = exercise.description,
                                cadence = null,
                                unitLoad = "kg",
                                unitDuration = exercise.unitDuration,
                                unitDistance = exercise.unitDistance,
                                unitVelocity = "m/s",
                                targetLoad = null,
                                targetReps = null,
                                targetDuration = exercise.targetDuration,
                                targetDistance = exercise.targetDistance,
                                targetVelocity = null,
                                targetRir = null,
                                targetRest = null,
                                restUnit = "s",
                                actualLoad = null,
                                actualReps = null,
                                actualDuration = null,
                                actualDistance = null,
                                actualVelocity = null,
                                actualRpe = null,
                                actualRir = null,
                                actualRest = null,
                                isCompleted = false
                            )
                        )
                    } else {
                        exercise.sets.forEach { setPlan ->
                            val id = "${date}_${exerciseId}_${setPlan.setNumber}"
                            sets.add(
                                WorkoutSet(
                                    id = id,
                                    exerciseId = exerciseId,
                                    setNumber = setPlan.setNumber,
                                    date = date,
                                    sessionTitle = session.title,
                                    sessionDescription = session.description,
                                    exerciseName = exercise.name,
                                    exerciseDescription = exercise.description,
                                    timing = exercise.timing,
                                    groupingId = exercise.groupingId,
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
                                    actualLoad = null,
                                    actualReps = null,
                                    actualDuration = null,
                                    actualDistance = null,
                                    actualVelocity = null,
                                    actualRpe = null,
                                    actualRir = null,
                                    actualRest = null,
                                    isCompleted = false
                                )
                            )
                        }
                    }
                }
            }
            Result.success(sets)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun mergeBlueprintWithExisting(
        blueprint: List<WorkoutSet>,
        existing: List<WorkoutSet>,
        date: String
    ): List<WorkoutSet> {
        val existingById = existing.associateBy { it.id }
        return blueprint.map { blue ->
            existingById[blue.id]?.let { kept ->
                blue.copy(
                    sessionTitle = kept.sessionTitle.ifBlank { blue.sessionTitle },
                    sessionDescription = kept.sessionDescription.ifBlank { blue.sessionDescription },
                    exerciseName = kept.exerciseName.ifBlank { blue.exerciseName },
                    exerciseDescription = kept.exerciseDescription.ifBlank { blue.exerciseDescription },
                    timing = kept.timing ?: blue.timing,
                    exerciseType = kept.exerciseType.ifBlank { blue.exerciseType },
                    groupingId = kept.groupingId ?: blue.groupingId,
                    workoutStatus = kept.workoutStatus ?: blue.workoutStatus,
                    actualLoad = kept.actualLoad,
                    actualReps = kept.actualReps,
                    actualVelocity = kept.actualVelocity,
                    actualRpe = kept.actualRpe,
                    actualRir = kept.actualRir,
                    actualDuration = kept.actualDuration,
                    actualDistance = kept.actualDistance,
                    actualRest = kept.actualRest,
                    isCompleted = kept.isCompleted
                )
            } ?: blue
        }
    }

    suspend fun updateSet(entry: WorkoutSet) = dao.updateSet(entry)

    suspend fun insertSets(entries: List<WorkoutSet>) = dao.insertSets(entries)

    /**
     * Save Workout: replace all sets for date with the given list. Only saved state is in DB (Home completion uses this).
     */
    suspend fun saveWorkoutForDate(date: String, sets: List<WorkoutSet>): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            dao.deleteSetsByDate(date)
            if (sets.isNotEmpty()) dao.insertSets(sets)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Parse JSON from clipboard into WorkoutSet list for the given date.
     * Accepts: WorkoutDaySession { "title", "exercises" }, or List<WorkoutExercisePlan>, or single WorkoutExercisePlan.
     */
    fun parseAndConvertToWorkoutSets(date: String, jsonString: String): Result<List<WorkoutSet>> {
        return try {
            val trimmed = jsonString.trim()
            if (trimmed.isEmpty()) return Result.failure(Exception("Empty JSON"))
            val sets = when {
                trimmed.startsWith("{") && trimmed.contains("exercises") -> {
                    val session = json.decodeFromString<WorkoutDaySession>(trimmed)
                    sessionToWorkoutSets(date, session, "added")
                }
                trimmed.startsWith("[") -> {
                    val exercises = json.decodeFromString<List<WorkoutExercisePlan>>(trimmed)
                    exercisesToWorkoutSets(date, "Added", "", exercises, 0)
                }
                else -> {
                    val exercise = json.decodeFromString<WorkoutExercisePlan>(trimmed)
                    exercisesToWorkoutSets(date, "Added", "", listOf(exercise), 0)
                }
            }
            if (sets.isEmpty()) Result.failure(Exception("No exercises or sets in JSON"))
            else Result.success(sets)
        } catch (e: Exception) {
            Result.failure(Exception("Invalid JSON: ${e.message}"))
        }
    }

    private fun sessionToWorkoutSets(date: String, session: WorkoutDaySession, sessionIdPrefix: String): List<WorkoutSet> {
        val sessionType = session.type.ifBlank { "RESISTANCE" }
        return exercisesToWorkoutSets(date, session.title, session.description, session.exercises, 0, sessionType)
    }

    private fun exercisesToWorkoutSets(
        date: String,
        sessionTitle: String,
        sessionDescription: String = "",
        exercises: List<WorkoutExercisePlan>,
        sessionIndexOffset: Int,
        sessionType: String? = null
    ): List<WorkoutSet> {
        val result = mutableListOf<WorkoutSet>()
        exercises.forEachIndexed { exerciseIndex, exercise ->
            val exerciseId = "ex_added_${sessionIndexOffset}_$exerciseIndex"
            val exerciseType = (sessionType?.takeIf { it.isNotBlank() } ?: exercise.type).ifBlank { "RESISTANCE" }
            if (exercise.sets.isEmpty()) {
                val id = "${date}_${exerciseId}_1"
                result.add(
                    WorkoutSet(
                        id = id,
                        exerciseId = exerciseId,
                        setNumber = 1,
                        date = date,
                        sessionTitle = sessionTitle,
                        sessionDescription = sessionDescription,
                        exerciseName = exercise.name,
                        exerciseDescription = exercise.description,
                        timing = exercise.timing,
                        groupingId = exercise.groupingId,
                        description = exercise.description,
                        cadence = null,
                        unitLoad = "kg",
                        unitDuration = exercise.unitDuration,
                        unitDistance = exercise.unitDistance,
                        unitVelocity = "m/s",
                        targetLoad = null,
                        targetReps = null,
                        targetDuration = exercise.targetDuration,
                        targetDistance = exercise.targetDistance,
                        targetVelocity = null,
                        targetRir = null,
                        targetRest = null,
                        restUnit = "s",
                        actualLoad = null,
                        actualReps = null,
                        actualDuration = null,
                        actualDistance = null,
                        actualVelocity = null,
                        actualRpe = null,
                        actualRir = null,
                        actualRest = null,
                        isCompleted = false
                    )
                )
            } else {
                exercise.sets.forEach { setPlan ->
                    val id = "${date}_${exerciseId}_${setPlan.setNumber}"
                    result.add(
                        WorkoutSet(
                            id = id,
                            exerciseId = exerciseId,
                            setNumber = setPlan.setNumber,
                            date = date,
                            sessionTitle = sessionTitle,
                            sessionDescription = sessionDescription,
                            exerciseName = exercise.name,
                            exerciseDescription = exercise.description,
                            timing = exercise.timing,
                            groupingId = exercise.groupingId,
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
                            actualLoad = null,
                            actualReps = null,
                            actualDuration = null,
                            actualDistance = null,
                            actualVelocity = null,
                            actualRpe = null,
                            actualRir = null,
                            actualRest = null,
                            isCompleted = false
                        )
                    )
                }
            }
        }
        return result
    }
}
