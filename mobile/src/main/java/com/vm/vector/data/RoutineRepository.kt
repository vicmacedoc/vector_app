package com.vm.vector.data

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
import java.nio.charset.StandardCharsets
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.UUID
import kotlin.math.round

@OptIn(ExperimentalSerializationApi::class)
class RoutineRepository(
    private val database: VectorDatabase,
    private val preferenceManager: PreferenceManager,
    private val driveService: DriveService,
    private val presetStorage: PresetStorage,
) {
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

    private val timeUnits = setOf("h", "min", "s")

    /**
     * Stable id for preset rows without an explicit [RoutinePlanEntry.id], so Wear and Calendar
     * always agree on the same [RoutineEntry.id] across parses (fixes watch→phone merge).
     */
    private fun stableGeneratedPlanId(planEntry: RoutinePlanEntry, dayOfWeek: Int, index: Int): String {
        val key = "${planEntry.category}\u0000${planEntry.title}\u0000${planEntry.type.name}\u0000$dayOfWeek\u0000$index"
        val uuid = UUID.nameUUIDFromBytes(key.toByteArray(StandardCharsets.UTF_8))
        return "gen_$uuid"
    }

    /**
     * Entries for the Wear routine timer: saved day if any, otherwise active preset for that weekday.
     * Only [RoutineType.NUMERICAL] with unit h / min / s.
     */
    suspend fun loadRoutineEntriesForWearSync(date: String): List<RoutineEntry> {
        val existing = getEntriesByDateSync(date)
        val entries = if (existing.isNotEmpty()) {
            existing
        } else {
            loadPresetEntriesForDate(date).getOrNull() ?: emptyList()
        }
        return entries.filter { entry ->
            entry.type == RoutineType.NUMERICAL &&
                entry.unit?.lowercase(Locale.US) in timeUnits
        }
    }

    suspend fun insertEntry(entry: RoutineEntry) = dao.insertEntry(entry)

    suspend fun insertEntries(entries: List<RoutineEntry>) = dao.insertEntries(entries)

    suspend fun updateEntry(entry: RoutineEntry) = dao.updateEntry(entry)

    suspend fun deleteEntry(entry: RoutineEntry) = dao.deleteEntry(entry)

    suspend fun getDatesWithCompletedRoutines(): List<String> = dao.getDatesWithCompletedRoutines()

    /**
     * Replaces all entries for a date with the given list. Used when saving from Calendar
     * so that only explicitly saved state is persisted (completion circles read from this).
     */
    suspend fun replaceEntriesForDate(date: String, entries: List<RoutineEntry>) {
        dao.deleteEntriesByDate(date)
        if (entries.isNotEmpty()) dao.insertEntries(entries)
    }

    /**
     * Loads weekly routine plan from active preset (Settings) and returns entries for a specific day of week.
     * If no preset is configured, returns failure.
     */
    suspend fun loadPresetEntriesForDate(date: String): Result<List<RoutineEntry>> {
        return try {
            val jsonContent = presetStorage.getPresetContent(PresetCategory.Routine)
                ?: return Result.failure(Exception("No routine preset active. Load a preset in Settings or from the Calendar screen."))
            parsePresetContentAndGetEntriesForDate(jsonContent, date)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Parses preset JSON and returns routine entries for the given date. Used when applying a new preset.
     */
    fun parsePresetContentAndGetEntriesForDate(jsonContent: String, date: String): Result<List<RoutineEntry>> {
        return try {
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
            val entries = planEntries.mapIndexed { index, planEntry ->
                val planId = planEntry.id.ifBlank { stableGeneratedPlanId(planEntry, dayOfWeek, index) }
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
     * One decimal place, matching [CalendarScreen] numerical slider rounding so the thumb and value stay aligned.
     */
    private fun normalizeNumericalSliderValue(value: Double): Double =
        round(value * 10.0) / 10.0

    /**
     * Computes entry with new value and recalculated status (no DB write). Used for in-memory Calendar edits.
     *
     * @param fromWear When true, NA is not preserved (watch completion replaces manual NA) and value is normalized.
     */
    fun computeEntryWithNewValue(entry: RoutineEntry, newValue: Double?, fromWear: Boolean = false): RoutineEntry {
        val shouldKeepNA = entry.status == RoutineStatus.NA && !fromWear
        val clampedRaw = newValue?.coerceIn(
            entry.minValue ?: 0.0,
            entry.maxValue ?: Double.MAX_VALUE
        )
        val clampedValue = if (entry.type == RoutineType.NUMERICAL && clampedRaw != null) {
            normalizeNumericalSliderValue(clampedRaw)
        } else {
            clampedRaw
        }
        val updated = entry.copy(currentValue = clampedValue)
        val newStatus = if (shouldKeepNA) RoutineStatus.NA else calculateGoalStatus(updated)
        val isGoalMet = when {
            newStatus == RoutineStatus.NA -> false
            updated.type == RoutineType.NUMERICAL -> {
                val goalValue = updated.goalValue ?: 0.0
                val current = updated.currentValue ?: 0.0
                if (updated.directionBetter == 1) current >= goalValue else current <= goalValue
            }
            else -> newStatus == RoutineStatus.DONE || newStatus == RoutineStatus.EXCEEDED
        }
        return updated.copy(status = newStatus, isGoalMet = isGoalMet)
    }

    /**
     * Computes toggled NA state for numerical entry (no DB write). Used for in-memory Calendar edits.
     */
    fun computeToggleNumericalStatus(entry: RoutineEntry): RoutineEntry {
        val newStatus = if (entry.status == RoutineStatus.NA) {
            calculateGoalStatus(entry)
        } else {
            RoutineStatus.NA
        }
        val isGoalMet = newStatus == RoutineStatus.DONE || newStatus == RoutineStatus.EXCEEDED
        return entry.copy(status = newStatus, isGoalMet = isGoalMet)
    }

    /**
     * Updates entry with new currentValue and recalculates status and isGoalMet.
     * If status is NA, keeps it as NA (manual override).
     */
    suspend fun updateEntryValue(entryId: String, date: String, newValue: Double?): RoutineEntry? {
        val entry = dao.getEntriesByDateSync(date)
            .find { it.id == entryId } ?: return null
        val finalEntry = computeEntryWithNewValue(entry, newValue)
        dao.updateEntry(finalEntry)
        return finalEntry
    }
    
    /**
     * Toggles numerical entry between automatic status (from slider) and NA.
     */
    suspend fun toggleNumericalStatus(entryId: String, date: String): RoutineEntry? {
        val entry = dao.getEntriesByDateSync(date)
            .find { it.id == entryId } ?: return null
        val updated = computeToggleNumericalStatus(entry)
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

}
