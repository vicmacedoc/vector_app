package com.vm.vector.data

import android.content.Context
import androidx.room.Room
import com.vm.core.models.RoutineEntry
import com.vm.core.models.RoutinePlanEntry
import com.vm.core.models.RoutineStatus
import com.vm.core.models.RoutineType
import com.vm.core.models.VectorDatabase
import com.vm.core.models.WeeklyRoutinePlan
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalSerializationApi::class)
class RoutineRepository(
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

    private val dao = database.routineEntryDao()

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

    fun getEntriesByDate(date: String): Flow<List<RoutineEntry>> = dao.getEntriesByDate(date)

    suspend fun getEntriesByDateSync(date: String): List<RoutineEntry> = dao.getEntriesByDateSync(date)

    suspend fun insertEntry(entry: RoutineEntry) = dao.insertEntry(entry)

    suspend fun insertEntries(entries: List<RoutineEntry>) = dao.insertEntries(entries)

    suspend fun updateEntry(entry: RoutineEntry) = dao.updateEntry(entry)

    suspend fun deleteEntry(entry: RoutineEntry) = dao.deleteEntry(entry)

    suspend fun getDatesWithCompletedRoutines(): List<String> = dao.getDatesWithCompletedRoutines()

    /**
     * Loads weekly routine plan from Drive and returns entries for a specific day of week.
     */
    suspend fun loadPresetEntriesForDate(date: String): Result<List<RoutineEntry>> {
        return try {
            val rootFolderId = preferenceManager.googleDriveFolderId.first()
            if (rootFolderId.isNullOrBlank()) {
                return Result.failure(Exception("Drive folder ID not configured"))
            }
            
            // Fetch routine_weekly.json from Drive
            val routineWeeklyResult = driveService.getRoutineWeekly(rootFolderId)
            val jsonContent = when (routineWeeklyResult) {
                is DriveResult.Success -> routineWeeklyResult.data
                is DriveResult.NeedsRemoteConsent -> return Result.failure(Exception("Needs remote consent. Please authorize Drive access in Settings."))
                is DriveResult.ListsFolderNotFound -> return Result.failure(Exception("presets folder or routine_weekly.json not found in Drive. Please ensure the file exists at presets/routine_weekly.json"))
                is DriveResult.ConfigurationError -> return Result.failure(Exception("Drive configuration error. Please check your Drive folder ID in Settings."))
                is DriveResult.Unauthorized -> return Result.failure(Exception("Drive access unauthorized. Please sign in and grant DRIVE scope permission."))
                else -> return Result.failure(Exception("Failed to fetch routine plan from Drive: $routineWeeklyResult"))
            }
            
            // Parse weekly plan
            val weeklyPlan = json.decodeFromString<WeeklyRoutinePlan>(jsonContent)
            
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
            
            // Convert to RoutineEntry with default values (NONE status, null currentValue). Generate id at assignment if not in preset.
            val baseTime = System.currentTimeMillis()
            val entries = planEntries.mapIndexed { index, planEntry ->
                val planId = planEntry.id.ifBlank { "gen_${baseTime}_$index" }
                RoutineEntry(
                    id = "${planId}_${date}",
                    date = date,
                    category = planEntry.category,
                    title = planEntry.title,
                    type = planEntry.type,
                    unit = planEntry.unit,
                    goalValue = planEntry.goalValue,
                    partialThreshold = planEntry.partialThreshold,
                    minValue = planEntry.minValue ?: 0.0,
                    maxValue = planEntry.maxValue ?: (planEntry.goalValue ?: 1.0) * 2.0,
                    currentValue = null,  // Null = Not Reported (NONE)
                    directionBetter = planEntry.directionBetter,
                    status = RoutineStatus.NONE,
                    isGoalMet = false,
                    timestamp = System.currentTimeMillis()
                )
            }
            
            Result.success(entries)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Calculates goal status based on currentValue, goalValue, partialThreshold, and directionBetter.
     * Returns NONE if currentValue is null.
     */
    fun calculateGoalStatus(entry: RoutineEntry): RoutineStatus {
        if (entry.type == RoutineType.CATEGORICAL) {
            return entry.status
        }
        
        // Numerical logic - if currentValue is null, return NONE
        val currentValue = entry.currentValue ?: return RoutineStatus.NONE
        
        val goalValue = entry.goalValue ?: return RoutineStatus.NOT_DONE
        val partialThreshold = entry.partialThreshold ?: (if (entry.directionBetter == 1) goalValue * 0.5 else goalValue * 1.5)
        val maxValue = entry.maxValue ?: goalValue * 2.0
        
        return when (entry.directionBetter) {
            1 -> { // Higher is better
                when {
                    currentValue < partialThreshold -> RoutineStatus.NOT_DONE
                    currentValue >= partialThreshold && currentValue < goalValue -> RoutineStatus.PARTIAL
                    currentValue >= goalValue && currentValue < maxValue -> RoutineStatus.DONE
                    currentValue == maxValue -> RoutineStatus.EXCEEDED
                    else -> RoutineStatus.DONE
                }
            }
            -1 -> { // Lower is better
                when {
                    currentValue > partialThreshold -> RoutineStatus.NOT_DONE
                    currentValue <= partialThreshold && currentValue > goalValue -> RoutineStatus.PARTIAL
                    currentValue <= goalValue -> RoutineStatus.DONE
                    else -> RoutineStatus.DONE
                }
            }
            else -> RoutineStatus.NONE
        }
    }

    /**
     * Updates entry with new currentValue and recalculates status and isGoalMet.
     * If status is NA, keeps it as NA (manual override).
     */
    suspend fun updateEntryValue(entryId: String, date: String, newValue: Double?): RoutineEntry? {
        val entry = dao.getEntriesByDateSync(date)
            .find { it.id == entryId } ?: return null
        
        // If status is NA, keep it as NA (manual override)
        val shouldKeepNA = entry.status == RoutineStatus.NA
        
        val clampedValue = newValue?.coerceIn(
            entry.minValue ?: 0.0,
            entry.maxValue ?: Double.MAX_VALUE
        )
        
        val updated = entry.copy(currentValue = clampedValue)
        val newStatus = if (shouldKeepNA) {
            RoutineStatus.NA
        } else {
            calculateGoalStatus(updated)
        }
        
        val isGoalMet = when (updated.type) {
            RoutineType.NUMERICAL -> {
                val goalValue = updated.goalValue ?: 0.0
                val current = updated.currentValue ?: 0.0
                if (updated.directionBetter == 1) {
                    current >= goalValue
                } else {
                    current <= goalValue
                }
            }
            RoutineType.CATEGORICAL -> newStatus == RoutineStatus.DONE || newStatus == RoutineStatus.EXCEEDED
        }
        
        val finalEntry = updated.copy(
            status = newStatus,
            isGoalMet = isGoalMet
        )
        
        dao.updateEntry(finalEntry)
        return finalEntry
    }
    
    /**
     * Toggles numerical entry between automatic status (from slider) and NA.
     */
    suspend fun toggleNumericalStatus(entryId: String, date: String): RoutineEntry? {
        val entry = dao.getEntriesByDateSync(date)
            .find { it.id == entryId } ?: return null
        
        val newStatus = if (entry.status == RoutineStatus.NA) {
            // Switch from NA to automatic calculation
            calculateGoalStatus(entry)
        } else {
            // Switch from automatic to NA
            RoutineStatus.NA
        }
        
        val updated = entry.copy(status = newStatus)
        dao.updateEntry(updated)
        return updated
    }

    /**
     * Updates entry with new status (for categorical or manual override).
     */
    suspend fun updateEntryStatus(entryId: String, date: String, newStatus: RoutineStatus): RoutineEntry? {
        val entry = dao.getEntriesByDateSync(date)
            .find { it.id == entryId } ?: return null
        
        val isGoalMet = newStatus == RoutineStatus.DONE || newStatus == RoutineStatus.EXCEEDED
        val updated = entry.copy(
            status = newStatus,
            isGoalMet = isGoalMet
        )
        
        dao.updateEntry(updated)
        return updated
    }

    /**
     * Saves all entries for a date to Room and uploads/updates JSON log to Drive logs/routine_logs.json.
     */
    suspend fun saveDay(date: String): Result<Unit> {
        return try {
            val entries = dao.getEntriesByDateSync(date)
            
            // Save entries to Room (they're already updated)
            dao.insertEntries(entries)
            
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
            
            // Try to fetch existing routine_logs.json
            val allLogs = mutableMapOf<String, List<RoutineEntry>>()
            val existingLogsResult = driveService.getFileContentFromFolder(logsFolderId, "routine_logs.json")
            
            if (existingLogsResult is DriveResult.Success) {
                try {
                    // Parse existing logs (format: { "YYYY-MM-DD": [entries], ... })
                    val existingData = json.decodeFromString<Map<String, List<RoutineEntry>>>(existingLogsResult.data)
                    allLogs.putAll(existingData)
                } catch (e: Exception) {
                    // If parsing fails, start fresh - log the error but continue
                    android.util.Log.w("RoutineRepository", "Failed to parse existing logs, starting fresh", e)
                }
            }
            
            // Update with current date's entries
            allLogs[date] = entries
            
            // Generate pretty-printed JSON
            val jsonLog = prettyJson.encodeToString(allLogs)
            
            // Upload/overwrite routine_logs.json
            val fileIdResult = driveService.findFileInFolder(logsFolderId, "routine_logs.json")
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
                    val uploadResult = driveService.uploadJsonFileToFolder(logsFolderId, "routine_logs.json", jsonLog)
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
