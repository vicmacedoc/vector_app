package com.vm.vector.data

import android.content.Context
import androidx.room.Room
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
    private val preferenceManager: PreferenceManager,
    private val driveService: DriveService,
) {

    private val database: VectorDatabase = Room.databaseBuilder(
        context,
        VectorDatabase::class.java,
        "vector_database"
    )
        .fallbackToDestructiveMigration()
        .build()

    private val dao = database.workoutSetDao()

    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    fun getSetsByDate(date: String): Flow<List<WorkoutSet>> = dao.getSetsByDate(date)

    suspend fun getSetsByDateSync(date: String): List<WorkoutSet> = dao.getSetsByDateSync(date)

    /**
     * When a date is selected: load day's exercises from workout_weekly.json (Drive presets),
     * merge with any existing Room data for that date (keep actuals), then UPSERT into Room.
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
     * Fetch preset from Drive (presets/workout_weekly.json) and return WorkoutSet list for the given date (day of week).
     */
    private suspend fun loadBlueprintSetsForDate(date: String): Result<List<WorkoutSet>> = withContext(Dispatchers.IO) {
        try {
            val rootFolderId = preferenceManager.googleDriveFolderId.first() ?: ""
            if (rootFolderId.isBlank()) {
                return@withContext Result.failure(Exception("Drive folder ID not configured"))
            }
            val driveResult = driveService.getWorkoutWeekly(rootFolderId)
            val content = when (driveResult) {
                is DriveResult.Success -> driveResult.data
                is DriveResult.NeedsRemoteConsent -> return@withContext Result.failure(Exception("Needs remote consent. Please authorize Drive access in Settings."))
                is DriveResult.ListsFolderNotFound -> return@withContext Result.failure(Exception("presets folder or workout_weekly.json not found in Drive. Ensure presets/workout_weekly.json exists."))
                is DriveResult.ConfigurationError -> return@withContext Result.failure(Exception("Drive configuration error. Check your Drive folder ID in Settings."))
                is DriveResult.Unauthorized -> return@withContext Result.failure(Exception("Drive access unauthorized. Sign in and grant DRIVE scope permission."))
                else -> return@withContext Result.failure(Exception("Failed to fetch workout plan from Drive: $driveResult"))
            }
            val plan = json.decodeFromString<WeeklyWorkoutPlan>(content)
            val parts = date.split("-")
            if (parts.size != 3) return@withContext Result.failure(Exception("Invalid date format: $date"))
            val year = parts[0].toIntOrNull() ?: return@withContext Result.failure(Exception("Invalid year: ${parts[0]}"))
            val month = parts[1].toIntOrNull()?.minus(1) ?: return@withContext Result.failure(Exception("Invalid month: ${parts[1]}"))
            val day = parts[2].toIntOrNull() ?: return@withContext Result.failure(Exception("Invalid day: ${parts[2]}"))
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
                session.exercises.forEachIndexed { exerciseIndex, exercise ->
                    val exerciseId = "ex_${sessionIndex}_$exerciseIndex"
                    val exerciseType = exercise.type.ifBlank { "RESISTANCE" }
                    if (exercise.sets.isEmpty()) {
                        val id = "${date}_${exerciseId}_1"
                        sets.add(
                            WorkoutSet(
                                id = id,
                                exerciseId = exerciseId,
                                setNumber = 1,
                                date = date,
                                sessionTitle = session.title,
                                exerciseName = exercise.name,
                                exerciseType = exerciseType,
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
                                    exerciseName = exercise.name,
                                    exerciseType = exerciseType,
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
                    exerciseName = kept.exerciseName.ifBlank { blue.exerciseName },
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
     * Save Workout: UPSERT current state into Room workout_sets. No Drive log.
     */
    suspend fun saveWorkoutForDate(date: String, sets: List<WorkoutSet>): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            dao.insertSets(sets)
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
                    exercisesToWorkoutSets(date, "Added", exercises, 0)
                }
                else -> {
                    val exercise = json.decodeFromString<WorkoutExercisePlan>(trimmed)
                    exercisesToWorkoutSets(date, "Added", listOf(exercise), 0)
                }
            }
            if (sets.isEmpty()) Result.failure(Exception("No exercises or sets in JSON"))
            else Result.success(sets)
        } catch (e: Exception) {
            Result.failure(Exception("Invalid JSON: ${e.message}"))
        }
    }

    private fun sessionToWorkoutSets(date: String, session: WorkoutDaySession, sessionIdPrefix: String): List<WorkoutSet> {
        return exercisesToWorkoutSets(date, session.title, session.exercises, 0)
    }

    private fun exercisesToWorkoutSets(
        date: String,
        sessionTitle: String,
        exercises: List<WorkoutExercisePlan>,
        sessionIndexOffset: Int
    ): List<WorkoutSet> {
        val result = mutableListOf<WorkoutSet>()
        exercises.forEachIndexed { exerciseIndex, exercise ->
            val exerciseId = "ex_added_${sessionIndexOffset}_$exerciseIndex"
            val exerciseType = exercise.type.ifBlank { "RESISTANCE" }
            if (exercise.sets.isEmpty()) {
                val id = "${date}_${exerciseId}_1"
                result.add(
                    WorkoutSet(
                        id = id,
                        exerciseId = exerciseId,
                        setNumber = 1,
                        date = date,
                        sessionTitle = sessionTitle,
                        exerciseName = exercise.name,
                        exerciseType = exerciseType,
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
                            exerciseName = exercise.name,
                            exerciseType = exerciseType,
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
