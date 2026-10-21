package com.vm.vector.data

import com.vm.core.models.ActivityLibraryItem
import com.vm.core.models.ActivityType
import com.vm.core.models.ExerciseLibraryItem
import com.vm.core.models.FoodEntry
import com.vm.core.models.FoodPlanEntry
import com.vm.core.models.MealLibraryItem
import com.vm.core.models.RoutinePlanActivity
import com.vm.core.models.RoutinePresetActivity
import com.vm.core.models.VectorDatabase
import com.vm.core.models.WorkoutDaySession
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.Locale
import java.util.UUID

class LibraryRepository(
    database: VectorDatabase,
) {
    private val exerciseDao = database.exerciseLibraryItemDao()
    private val mealDao = database.mealLibraryItemDao()
    private val routineDao = database.routinePresetDao()
    private val activityDao = database.activityLibraryItemDao()
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

    fun observeExercises(): Flow<List<ExerciseLibraryItem>> = exerciseDao.getAll()
    fun observeMeals(): Flow<List<MealLibraryItem>> = mealDao.getAll()
    fun observeActivities(): Flow<List<ActivityLibraryItem>> = activityDao.getAll()

    suspend fun upsertExerciseSession(session: WorkoutDaySession) {
        val type = session.type.ifBlank { "RESISTANCE" }.uppercase(Locale.US)
        val name = session.title.trim().ifBlank {
            session.exercises.firstOrNull()?.name?.trim().orEmpty()
        }
        if (name.isBlank()) return
        val typeKey = type.lowercase(Locale.US)
        val nameKey = name.lowercase(Locale.US)
        val existing = exerciseDao.getByTypeAndName(typeKey, nameKey)
        val item = ExerciseLibraryItem(
            id = existing?.id ?: "exlib_${UUID.randomUUID()}",
            type = type,
            name = name,
            description = session.description,
            payloadJson = json.encodeToString(session.copy(title = name, type = type)),
            typeKey = typeKey,
            nameKey = nameKey,
            createdAt = existing?.createdAt ?: System.currentTimeMillis(),
        )
        exerciseDao.insert(item)
    }

    suspend fun upsertExerciseSessions(sessions: List<WorkoutDaySession>) {
        sessions.forEach { upsertExerciseSession(it) }
    }

    fun decodeExerciseSession(item: ExerciseLibraryItem): WorkoutDaySession? =
        runCatching { json.decodeFromString<WorkoutDaySession>(item.payloadJson) }.getOrNull()

    suspend fun deleteExercise(id: String) = exerciseDao.deleteById(id)

    suspend fun upsertMeal(
        name: String,
        kcal: Double,
        protein: Double,
        carbs: Double,
        fats: Double,
        amount: Double,
        unit: String,
    ) {
        val trimmed = name.trim()
        if (trimmed.isBlank()) return
        val nameKey = trimmed.lowercase(Locale.US)
        val existing = mealDao.getByNameKey(nameKey)
        mealDao.insert(
            MealLibraryItem(
                id = existing?.id ?: "mealib_${UUID.randomUUID()}",
                name = trimmed,
                kcal = kcal,
                protein = protein,
                carbs = carbs,
                fats = fats,
                amount = amount,
                unit = unit.ifBlank { "g" },
                nameKey = nameKey,
                createdAt = existing?.createdAt ?: System.currentTimeMillis(),
            )
        )
    }

    suspend fun upsertMealsFromPlan(entries: List<FoodPlanEntry>) {
        entries.forEach { entry ->
            upsertMeal(
                name = entry.name,
                kcal = entry.kcal,
                protein = entry.protein,
                carbs = entry.carbs,
                fats = entry.fats,
                amount = entry.plannedAmount,
                unit = entry.unit,
            )
        }
    }

    suspend fun upsertMealsFromEntries(entries: List<FoodEntry>) {
        entries.forEach { entry ->
            upsertMeal(
                name = entry.name,
                kcal = entry.kcal,
                protein = entry.protein,
                carbs = entry.carbs,
                fats = entry.fats,
                amount = entry.effectiveAmount(),
                unit = entry.unit,
            )
        }
    }

    suspend fun deleteMeal(id: String) = mealDao.deleteById(id)

    /** Saves one activity by title (case-insensitive); an existing item keeps its id and is updated. */
    suspend fun upsertActivity(
        title: String,
        startTime: String,
        endTime: String,
        labelName: String,
        type: ActivityType,
        description: String,
    ) {
        val trimmed = title.trim()
        if (trimmed.isBlank()) return
        val nameKey = trimmed.lowercase(Locale.US)
        val existing = activityDao.getByNameKey(nameKey)
        activityDao.insert(
            ActivityLibraryItem(
                id = existing?.id ?: "actlib_${UUID.randomUUID()}",
                title = trimmed,
                startTime = startTime,
                endTime = endTime,
                labelName = labelName.trim(),
                type = type,
                description = description.trim(),
                nameKey = nameKey,
                createdAt = existing?.createdAt ?: System.currentTimeMillis(),
            )
        )
    }

    suspend fun upsertActivitiesFromPlan(items: List<RoutinePlanActivity>) {
        items.forEach { item ->
            upsertActivity(
                title = item.title,
                startTime = normalizeActivityHhMm(item.startTime, ActivityImportDefaults.START_TIME),
                endTime = normalizeActivityHhMm(item.endTime, ActivityImportDefaults.END_TIME),
                labelName = item.label,
                type = parseActivityTypeOrDefault(item.type, ActivityType.PLANNED),
                description = item.description,
            )
        }
    }

    suspend fun deleteActivity(id: String) = activityDao.deleteById(id)

    /** Flattens legacy routine bundles into single library activities, then deletes the bundles. */
    suspend fun migrateLegacyRoutineBundles() {
        val bundles = routineDao.getAllOnce()
        if (bundles.isEmpty()) return
        bundles.forEach { bundle ->
            runCatching { json.decodeFromString<List<RoutinePresetActivity>>(bundle.payloadJson) }
                .getOrDefault(emptyList())
                .forEach { activity ->
                    upsertActivity(
                        title = activity.title,
                        startTime = activity.startTime,
                        endTime = activity.endTime,
                        labelName = activity.labelName,
                        type = activity.type,
                        description = activity.description,
                    )
                }
            routineDao.deleteById(bundle.id)
        }
    }
}
