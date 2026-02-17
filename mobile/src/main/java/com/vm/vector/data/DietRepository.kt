package com.vm.vector.data

import android.content.Context
import androidx.room.Room
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
    private val context: Context,
    private val preferenceManager: PreferenceManager,
    private val driveService: DriveService,
) {
    private val database: VectorDatabase = Room.databaseBuilder(
        context,
        VectorDatabase::class.java,
        "vector_database"
    )
        .fallbackToDestructiveMigration() // Handle schema changes by recreating database
        .build()

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
     * Deletes all diet entries from the database (daily responses only, not presets or JSON files).
     */
    suspend fun resetDatabase(): Result<Unit> {
        return try {
            dao.deleteAllEntries()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Loads weekly diet plan from Drive and returns entries for a specific day of week.
     */
    suspend fun loadPresetEntriesForDate(date: String): Result<List<DietEntry>> {
        return try {
            val rootFolderId = preferenceManager.googleDriveFolderId.first()
            if (rootFolderId.isNullOrBlank()) {
                return Result.failure(Exception("Drive folder ID not configured"))
            }
            
            // Fetch diet_weekly.json from Drive
            val dietWeeklyResult = driveService.getDietWeekly(rootFolderId)
            val jsonContent = when (dietWeeklyResult) {
                is DriveResult.Success -> dietWeeklyResult.data
                is DriveResult.NeedsRemoteConsent -> return Result.failure(Exception("Needs remote consent. Please authorize Drive access in Settings."))
                is DriveResult.ListsFolderNotFound -> return Result.failure(Exception("presets folder or diet_weekly.json not found in Drive. Please ensure the file exists at presets/diet_weekly.json"))
                is DriveResult.ConfigurationError -> return Result.failure(Exception("Drive configuration error. Please check your Drive folder ID in Settings."))
                is DriveResult.Unauthorized -> return Result.failure(Exception("Drive access unauthorized. Please sign in and grant DRIVE scope permission."))
                else -> return Result.failure(Exception("Failed to fetch diet plan from Drive: $dietWeeklyResult"))
            }
            
            // Parse weekly plan
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
     * Saves all entries for a date to Room and uploads/updates JSON log to Drive logs/diet_logs.json.
     * Saves scaled values (base * multiplier) and sets AuditStatus to ADJUSTED if multiplier != 1.0.
     */
    suspend fun saveDay(date: String): Result<Unit> {
        return try {
            val entries = dao.getEntriesByDateSync(date)
            
            // Create scaled entries: multiply base values by multiplier, reset multiplier to 1.0
            // Set status to ADJUSTED if multiplier != 1.0, otherwise keep original status
            val scaledEntries = entries.map { entry ->
                val multiplier = entry.quantityMultiplier
                val newStatus = if (multiplier != 1.0) {
                    AuditStatus.ADJUSTED
                } else {
                    entry.status
                }
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
            
            // Save scaled entries to Room
            dao.insertEntries(scaledEntries)
            
            // Upload to Drive logs/ folder
            val rootFolderId = preferenceManager.googleDriveFolderId.first()
            if (rootFolderId.isNullOrBlank()) {
                return Result.failure(Exception("Drive folder ID not configured"))
            }
            
            // Discover or create logs folder
            val logsFolderResult = driveService.discoverOrCreateFolder(rootFolderId, "logs")
            val logsFolderId = when (logsFolderResult) {
                is DriveResult.Success -> logsFolderResult.data
                is DriveResult.NeedsRemoteConsent -> return Result.failure(Exception("Needs remote consent"))
                else -> return Result.failure(Exception("Failed to access logs folder: $logsFolderResult"))
            }
            
            // Try to fetch existing diet_logs.json
            val allLogs = mutableMapOf<String, List<DietEntry>>()
            val existingLogsResult = driveService.getFileContentFromFolder(logsFolderId, "diet_logs.json")
            
            if (existingLogsResult is DriveResult.Success) {
                try {
                    // Parse existing logs (format: { "YYYY-MM-DD": [entries], ... })
                    val existingData = json.decodeFromString<Map<String, List<DietEntry>>>(existingLogsResult.data)
                    allLogs.putAll(existingData)
                } catch (e: Exception) {
                    // If parsing fails, start fresh - log the error but continue
                    android.util.Log.w("DietRepository", "Failed to parse existing logs, starting fresh", e)
                }
            }
            
            // Update with current date's scaled entries
            allLogs[date] = scaledEntries
            
            // Generate pretty-printed JSON
            val jsonLog = prettyJson.encodeToString(allLogs)
            
            // Upload/overwrite diet_logs.json
            val fileIdResult = driveService.findFileInFolder(logsFolderId, "diet_logs.json")
            when (fileIdResult) {
                is DriveResult.Success -> {
                    // File exists, overwrite it
                    val overwriteResult = driveService.overwriteFileContent(fileIdResult.data.id, jsonLog)
                    when (overwriteResult) {
                        is DriveResult.Success -> Result.success(Unit)
                        is DriveResult.NeedsRemoteConsent -> Result.failure(Exception("Needs remote consent"))
                        else -> Result.failure(Exception("Failed to update log: $overwriteResult"))
                    }
                }
                is DriveResult.ConfigurationError -> {
                    // File doesn't exist, create it
                    val uploadResult = driveService.uploadJsonFileToFolder(logsFolderId, "diet_logs.json", jsonLog)
                    when (uploadResult) {
                        is DriveResult.Success -> Result.success(Unit)
                        is DriveResult.NeedsRemoteConsent -> Result.failure(Exception("Needs remote consent"))
                        else -> Result.failure(Exception("Failed to upload log: $uploadResult"))
                    }
                }
                else -> {
                    // Other error (e.g., NeedsRemoteConsent)
                    Result.failure(Exception("Failed to access log file: $fileIdResult"))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
