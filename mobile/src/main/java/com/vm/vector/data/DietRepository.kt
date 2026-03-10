package com.vm.vector.data

import com.vm.core.models.AuditStatus
import com.vm.core.models.DietEntry
import com.vm.core.models.DietPlanEntry
import com.vm.core.models.VectorDatabase
import com.vm.core.models.WeeklyDietPlan
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalSerializationApi::class)
class DietRepository(
    private val database: VectorDatabase,
    private val preferenceManager: PreferenceManager,
    private val driveService: DriveService,
    private val presetStorage: PresetStorage,
) {
    private val dao = database.dietEntryDao()

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

    fun getEntriesByDate(date: String): Flow<List<DietEntry>> = dao.getEntriesByDate(date)

    suspend fun getEntriesByDateSync(date: String): List<DietEntry> = dao.getEntriesByDateSync(date)

    suspend fun insertEntry(entry: DietEntry) = dao.insertEntry(entry)

    suspend fun insertEntries(entries: List<DietEntry>) = dao.insertEntries(entries)

    suspend fun updateEntry(entry: DietEntry) = dao.updateEntry(entry)

    suspend fun deleteEntry(entry: DietEntry) = dao.deleteEntry(entry)

    suspend fun getDatesWithCheckedMeals(): List<String> = dao.getDatesWithCheckedMeals()

    /**
     * Replaces all entries for a date with the given list. Used when saving from Calendar
     * so that only explicitly saved state is persisted (completion circles read from this).
     */
    suspend fun replaceEntriesForDate(date: String, entries: List<DietEntry>) {
        dao.deleteEntriesByDate(date)
        if (entries.isNotEmpty()) dao.insertEntries(entries)
    }

    /**
     * Deletes all day-entry data from the database (diet, routine, workout, diary, daily home).
     * Presets and Lists JSON files in Drive are not modified.
     */
    suspend fun resetDatabase(): Result<Unit> {
        return try {
            database.diaryCollectionImageDao().deleteAll()
            database.diaryCollectionDao().deleteAll()
            database.dietEntryDao().deleteAllEntries()
            database.routineEntryDao().deleteAllEntries()
            database.workoutSetDao().deleteAllSets()
            database.dailyHomeEntryDao().deleteAll()
            database.diaryEntryDao().deleteAll()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Loads weekly diet plan from active preset (Settings) and returns entries for a specific day of week.
     * If no preset is configured, returns failure.
     */
    suspend fun loadPresetEntriesForDate(date: String): Result<List<DietEntry>> {
        return try {
            val jsonContent = presetStorage.getPresetContent(PresetCategory.Diet)
                ?: return Result.failure(Exception("No diet preset active. Load a preset in Settings or from the Calendar screen."))
            parsePresetContentAndGetEntriesForDate(jsonContent, date)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Parses preset JSON and returns diet entries for the given date. Used when applying a new preset.
     */
    fun parsePresetContentAndGetEntriesForDate(jsonContent: String, date: String): Result<List<DietEntry>> {
        return try {
            val weeklyPlan = json.decodeFromString<WeeklyDietPlan>(jsonContent)
            
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
            
            // Convert to DietEntry with PLANNED status. Generate id at assignment if not in preset.
            val baseTime = System.currentTimeMillis()
            val entries = planEntries.mapIndexed { index, planEntry ->
                val planId = planEntry.id.ifBlank { "gen_${baseTime}_$index" }
                DietEntry(
                    id = "${planId}_${date}",
                    date = date,
                    plannedTime = planEntry.plannedTime,
                    name = planEntry.name,
                    kcal = planEntry.kcal,
                    protein = planEntry.protein,
                    carbs = planEntry.carbs,
                    fats = planEntry.fats,
                    plannedAmount = planEntry.plannedAmount,
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
     * Saves all entries for a date to Room. Saves scaled values (base * multiplier) and sets
     * AuditStatus to ADJUSTED if multiplier != 1.0. Backup to Drive is handled separately.
     */
    suspend fun saveDay(date: String): Result<Unit> {
        return try {
            val entries = dao.getEntriesByDateSync(date)
            val scaledEntries = entries.map { entry ->
                val multiplier = entry.quantityMultiplier
                val newStatus = if (multiplier != 1.0) AuditStatus.ADJUSTED else entry.status
                entry.copy(
                    kcal = entry.kcal * multiplier,
                    protein = entry.protein * multiplier,
                    carbs = entry.carbs * multiplier,
                    fats = entry.fats * multiplier,
                    plannedAmount = entry.plannedAmount * multiplier,
                    quantityMultiplier = 1.0,
                    status = newStatus
                )
            }
            dao.insertEntries(scaledEntries)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
