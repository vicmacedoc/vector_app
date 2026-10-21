package com.vm.vector.data

import com.vm.core.models.AuditStatus
import com.vm.core.models.FoodEntry
import com.vm.core.models.FoodPlanEntry
import com.vm.core.models.VectorDatabase
import com.vm.core.models.WeeklyFoodPlan
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalSerializationApi::class)
class FoodRepository(
    private val database: VectorDatabase,
    private val preferenceManager: PreferenceManager,
    private val presetStorage: PresetStorage,
) {
    private val dao = database.foodEntryDao()
    private val libraryRepository = LibraryRepository(database)

    private val prettyJson = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
        prettyPrintIndent = "  "
        explicitNulls = false
    }
    
    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    fun getEntriesByDate(date: String): Flow<List<FoodEntry>> = dao.getEntriesByDate(date)

    suspend fun getEntriesByDateSync(date: String): List<FoodEntry> = dao.getEntriesByDateSync(date)

    suspend fun insertEntry(entry: FoodEntry) = dao.insertEntry(entry)

    suspend fun insertEntries(entries: List<FoodEntry>) = dao.insertEntries(entries)

    suspend fun updateEntry(entry: FoodEntry) = dao.updateEntry(entry)

    suspend fun deleteEntry(entry: FoodEntry) = dao.deleteEntry(entry)

    suspend fun getDatesWithCheckedMeals(): List<String> = dao.getDatesWithCheckedMeals()

    /**
     * Replaces all entries for a date with the given list. Used when saving from Calendar
     * so that only explicitly saved state is persisted (completion circles read from this).
     */
    suspend fun replaceEntriesForDate(date: String, entries: List<FoodEntry>) {
        dao.deleteEntriesByDate(date)
        if (entries.isNotEmpty()) dao.insertEntries(entries)
    }

    /**
     * Deletes all day-entry data (food, habits, activities, workout, self journal, daily home).
     * Diary photo album rows (`self_collection` / `self_collection_image`) are intentionally not cleared:
     * they mirror Google Drive and stay available after reset. Activity labels and presets on this
     * device are not removed. Drive files are not modified.
     */
    suspend fun resetDatabase(): Result<Unit> {
        return try {
            database.foodEntryDao().deleteAllEntries()
            database.habitLogDao().deleteAll()
            database.habitDefinitionDao().deleteAll()
            database.habitDayConfigDao().deleteAll()
            database.activityEntryDao().deleteAll()
            database.workoutSetDao().deleteAllSets()
            database.dailyHomeEntryDao().deleteAll()
            database.selfEntryDao().deleteAll()
            database.exerciseLibraryItemDao().deleteAll()
            database.mealLibraryItemDao().deleteAll()
            database.routinePresetDao().deleteAll()
            database.activityLibraryItemDao().deleteAll()
            database.calendarDraftDao().deleteAll()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Loads weekly food plan from active preset (Settings) and returns entries for a specific day of week.
     * If no preset is configured, returns failure.
     */
    suspend fun loadPresetEntriesForDate(date: String): Result<List<FoodEntry>> {
        return try {
            val jsonContent = presetStorage.getPresetContent(PresetCategory.Food)
                ?: return Result.failure(Exception("No food preset active. Load a preset in Settings or from the Calendar screen."))
            ingestMealsFromWeeklyPlan(jsonContent)
            parsePresetContentAndGetEntriesForDate(jsonContent, date)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** Meals the active preset plans for [date]'s weekday; null when no preset is active. */
    suspend fun presetPlanEntriesForDate(date: String): List<FoodPlanEntry>? {
        val jsonContent = presetStorage.getPresetContent(PresetCategory.Food) ?: return null
        val days = runCatching { json.decodeFromString<WeeklyFoodPlan>(jsonContent).weekly_plan }.getOrNull()
            ?: return null
        val parsed = runCatching { SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(date) }.getOrNull()
            ?: return null
        return when (Calendar.getInstance().apply { time = parsed }.get(Calendar.DAY_OF_WEEK)) {
            Calendar.MONDAY -> days.monday
            Calendar.TUESDAY -> days.tuesday
            Calendar.WEDNESDAY -> days.wednesday
            Calendar.THURSDAY -> days.thursday
            Calendar.FRIDAY -> days.friday
            Calendar.SATURDAY -> days.saturday
            else -> days.sunday
        }
    }

    fun encodePlanEntriesJson(entries: List<FoodPlanEntry>): String =
        json.encodeToString(ListSerializer(FoodPlanEntry.serializer()), entries)

    /**
     * Parses preset JSON and returns food entries for the given date. Used when applying a new preset.
     */
    suspend fun ingestMealsFromWeeklyPlan(jsonContent: String) {
        runCatching {
            val weeklyPlan = json.decodeFromString<WeeklyFoodPlan>(jsonContent)
            val days = weeklyPlan.weekly_plan
            libraryRepository.upsertMealsFromPlan(
                days.monday + days.tuesday + days.wednesday + days.thursday +
                    days.friday + days.saturday + days.sunday
            )
        }
    }

    fun parsePresetContentAndGetEntriesForDate(jsonContent: String, date: String): Result<List<FoodEntry>> {
        return try {
            val weeklyPlan = json.decodeFromString<WeeklyFoodPlan>(jsonContent)
            
            // Determine day of week
            val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            val parsedDate = dateFormat.parse(date) ?: return Result.failure(Exception("Invalid date format"))
            val calendar = Calendar.getInstance().apply { time = parsedDate }
            val dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK)
            
            // Get entries for the day
            val planEntries = when (dayOfWeek) {
                Calendar.MONDAY -> weeklyPlan.weekly_plan.monday
                Calendar.TUESDAY -> weeklyPlan.weekly_plan.tuesday
                Calendar.WEDNESDAY -> weeklyPlan.weekly_plan.wednesday
                Calendar.THURSDAY -> weeklyPlan.weekly_plan.thursday
                Calendar.FRIDAY -> weeklyPlan.weekly_plan.friday
                Calendar.SATURDAY -> weeklyPlan.weekly_plan.saturday
                Calendar.SUNDAY -> weeklyPlan.weekly_plan.sunday
                else -> emptyList()
            }
            
            // Convert to FoodEntry with PLANNED status. Ids are stable per weekday slot so an
            // untouched preset day always compares equal to itself (no false unsaved changes).
            // planned* = immutable preset baseline; time/amount = editable effective values.
            val entries = planEntries.mapIndexed { index, planEntry ->
                val planId = planEntry.id.ifBlank { "gen_$index" }
                FoodEntry(
                    id = "${planId}_${date}",
                    date = date,
                    plannedTime = planEntry.plannedTime,
                    name = planEntry.name,
                    kcal = planEntry.kcal,
                    protein = planEntry.protein,
                    carbs = planEntry.carbs,
                    fats = planEntry.fats,
                    plannedAmount = planEntry.plannedAmount,
                    time = planEntry.plannedTime,
                    amount = planEntry.plannedAmount,
                    quantityMultiplier = 1.0,
                    unit = planEntry.unit,
                    isChecked = false,
                    status = AuditStatus.PLANNED,
                    timestamp = System.currentTimeMillis()
                )
            }
            
            Result.success(entries)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Saves all entries for a date to Room. Bakes quantityMultiplier into macros and [FoodEntry.amount]
     * (never into plannedAmount). Preset baselines stay intact; status is resolved from effective vs planned.
     */
    suspend fun saveDay(date: String): Result<Unit> {
        return try {
            val entries = dao.getEntriesByDateSync(date)
            val scaledEntries = entries.map { entry ->
                val multiplier = entry.quantityMultiplier
                val baseAmount = entry.effectiveAmount()
                val baked = entry.copy(
                    kcal = entry.kcal * multiplier,
                    protein = entry.protein * multiplier,
                    carbs = entry.carbs * multiplier,
                    fats = entry.fats * multiplier,
                    amount = baseAmount * multiplier,
                    time = entry.effectiveTime(),
                    quantityMultiplier = 1.0,
                )
                baked.copy(status = baked.resolveAuditStatus(multiplier = 1.0))
            }
            dao.insertEntries(scaledEntries)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
