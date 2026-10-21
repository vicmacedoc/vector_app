package com.vm.vector.data

import com.vm.core.models.ActivityCompletion
import com.vm.core.models.ActivityEntry
import com.vm.core.models.ActivityLabel
import com.vm.core.models.ActivityType
import com.vm.core.models.RoutinePlanActivity
import com.vm.core.models.VectorDatabase
import com.vm.core.models.WeeklyRoutinePlan
import com.vm.core.models.WeeklyRoutinePlanDays
import com.vm.core.models.effectiveEndTime
import com.vm.core.models.effectiveStartTime
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.decodeFromJsonElement
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.UUID

class ActivityRepository(
    private val database: VectorDatabase,
    private val presetStorage: PresetStorage? = null,
) {
    private val labelDao = database.activityLabelDao()
    private val entryDao = database.activityEntryDao()
    private val libraryRepository = LibraryRepository(database)

    private val presetJson = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    suspend fun ensureSleepLabel() {
        if (labelDao.getByIdSync(ActivityLabel.SLEEP_LABEL_ID) == null) {
            labelDao.insert(
                ActivityLabel(
                    id = ActivityLabel.SLEEP_LABEL_ID,
                    name = ActivityLabel.SLEEP_LABEL_NAME,
                    colorArgb = ActivityLabel.SLEEP_COLOR_ARGB,
                    isSystem = true
                )
            )
        }
    }

    suspend fun getAllLabels(): List<ActivityLabel> {
        ensureSleepLabel()
        return labelDao.getAllSync()
    }

    suspend fun getEntriesForDate(date: String): List<ActivityEntry> =
        entryDao.getByDateSync(date).sortedWith(
            compareBy(
                { parseSortMinutes(it.effectiveStartTime()) },
                { parseSortMinutes(it.effectiveEndTime()) },
                { it.title },
            )
        )

    private fun parseSortMinutes(time: String): Int {
        val parts = time.trim().split(":")
        val h = parts.getOrNull(0)?.toIntOrNull() ?: 0
        val m = parts.getOrNull(1)?.toIntOrNull() ?: 0
        return h * 60 + m
    }

    suspend fun upsertEntry(entry: ActivityEntry) {
        entryDao.insert(entry)
    }

    suspend fun deleteEntry(id: String) {
        entryDao.deleteById(id)
    }

    suspend fun deleteEntriesForDate(date: String) {
        entryDao.deleteByDate(date)
    }

    suspend fun updateCompletion(id: String, completion: ActivityCompletion) {
        val existing = entryDao.getByIdSync(id) ?: return
        entryDao.update(existing.copy(completion = completion, timestamp = System.currentTimeMillis()))
    }

    suspend fun toggleType(id: String) {
        val existing = entryDao.getByIdSync(id) ?: return
        val next = if (existing.type == ActivityType.PLANNED) ActivityType.NEW else ActivityType.PLANNED
        entryDao.update(existing.copy(type = next, timestamp = System.currentTimeMillis()))
    }

    suspend fun createLabel(name: String, colorArgb: Int): Result<ActivityLabel> {
        val trimmed = name.trim()
        if (trimmed.isBlank()) return Result.failure(Exception("Label name cannot be empty"))
        if (labelDao.getByNameSync(trimmed) != null) {
            return Result.failure(Exception("A label with this name already exists"))
        }
        val label = ActivityLabel(
            id = "label_${UUID.randomUUID()}",
            name = trimmed,
            colorArgb = colorArgb,
            isSystem = false
        )
        labelDao.insert(label)
        return Result.success(label)
    }

    /**
     * Finds an existing label by name (case-insensitive) or creates one with [colorArgb].
     * Existing labels keep their original color.
     */
    suspend fun getOrCreateLabel(name: String, colorArgb: Int): Result<ActivityLabel> {
        val trimmed = name.trim()
        if (trimmed.isBlank()) return Result.failure(Exception("Label name cannot be empty"))
        labelDao.getByNameSync(trimmed)?.let { return Result.success(it) }
        return createLabel(trimmed, colorArgb)
    }

    suspend fun deleteLabel(id: String): Result<Unit> {
        val label = labelDao.getByIdSync(id)
            ?: return Result.failure(Exception("Label not found"))
        if (label.isSystem) {
            return Result.failure(Exception("System labels cannot be deleted"))
        }
        entryDao.deleteByLabelId(id)
        labelDao.deleteById(id)
        return Result.success(Unit)
    }

    suspend fun getDatesWithAnyActivity(): Set<String> =
        entryDao.getDatesWithEntries().toSet()

    /** Persists that day's routine activity entries. Labels are managed in Settings. */
    suspend fun persistDaySnapshot(
        date: String,
        entries: List<ActivityEntry>,
    ) {
        val keepEntryIds = entries.map { it.id }.toSet()
        entryDao.getByDateSync(date)
            .filter { it.id !in keepEntryIds }
            .forEach { deleteEntry(it.id) }
        entries.forEach { upsertEntry(it) }
    }

    // --- Weekly Routine preset ---

    /** Decodes a Routine preset, wrapped in `weekly_plan` or with weekday keys at the top level. */
    fun decodeRoutinePlan(content: String): WeeklyRoutinePlan {
        val root = presetJson.parseToJsonElement(content.trim()) as? JsonObject
            ?: throw IllegalArgumentException("Routine preset must be a JSON object")
        if ("weekly_plan" in root) {
            return presetJson.decodeFromJsonElement(WeeklyRoutinePlan.serializer(), root)
        }
        if (WEEKDAY_KEYS.none { it in root }) {
            throw IllegalArgumentException("Routine preset needs weekday keys (monday … sunday)")
        }
        val days = presetJson.decodeFromJsonElement<WeeklyRoutinePlanDays>(root)
        val name = (root["name"] as? JsonPrimitive)?.content.orEmpty()
        return WeeklyRoutinePlan(name = name, weekly_plan = days)
    }

    fun planActivitiesForDate(plan: WeeklyRoutinePlan, date: String): List<RoutinePlanActivity> {
        val parsed = SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(date)
            ?: throw IllegalArgumentException("Invalid date format")
        val days = plan.weekly_plan
        return when (Calendar.getInstance().apply { time = parsed }.get(Calendar.DAY_OF_WEEK)) {
            Calendar.MONDAY -> days.monday
            Calendar.TUESDAY -> days.tuesday
            Calendar.WEDNESDAY -> days.wednesday
            Calendar.THURSDAY -> days.thursday
            Calendar.FRIDAY -> days.friday
            Calendar.SATURDAY -> days.saturday
            Calendar.SUNDAY -> days.sunday
            else -> emptyList()
        }
    }

    /** Label names in the preset that do not exist yet (case-insensitive). */
    suspend fun unknownPresetLabels(content: String): List<String> {
        val known = getAllLabels().map { it.name.lowercase(Locale.US) }.toSet()
        return decodeRoutinePlan(content).weekly_plan.all()
            .map { it.label.trim() }
            .filter { it.isNotEmpty() && it.lowercase(Locale.US) !in known }
            .distinctBy { it.lowercase(Locale.US) }
    }

    /** Creates any labels the preset references but the app does not have yet. */
    suspend fun ensurePresetLabels(content: String) {
        unknownPresetLabels(content).forEach { getOrCreateLabel(it, NEW_PRESET_LABEL_COLOR) }
    }

    /** Saves every preset activity (all weekdays) as a single item in the Routine library. */
    suspend fun ingestPresetActivities(content: String) {
        runCatching { libraryRepository.upsertActivitiesFromPlan(decodeRoutinePlan(content).weekly_plan.all()) }
    }

    /**
     * Validates a preset for Settings: decodes it and maps [date]'s weekday slice.
     * Unknown labels are not created here; they fall back to the default label.
     */
    suspend fun parseRoutinePresetForDate(content: String, date: String): Result<List<ActivityEntry>> =
        runCatching {
            val plan = decodeRoutinePlan(content)
            plan.weekly_plan.all().forEachIndexed { index, item ->
                require(item.title.isNotBlank()) { "Activity #${index + 1} has no title" }
                require(isValidActivityHhMm(item.startTime)) { "\"${item.title}\" has an invalid startTime" }
                require(isValidActivityHhMm(item.endTime)) { "\"${item.title}\" has an invalid endTime" }
            }
            planToEntries(planActivitiesForDate(plan, date), date, getAllLabels())
        }

    suspend fun getActiveRoutinePresetContent(): String? =
        presetStorage?.getPresetContent(PresetCategory.Routine)

    /** The active Routine preset's activities for [date], or failure when no preset is active. */
    suspend fun loadPresetActivitiesForDate(date: String): Result<List<ActivityEntry>> = runCatching {
        val content = getActiveRoutinePresetContent()
            ?: throw NoRoutinePresetException()
        ensurePresetLabels(content)
        ingestPresetActivities(content)
        planToEntries(planActivitiesForDate(decodeRoutinePlan(content), date), date, getAllLabels())
    }

    /** The active preset's activities for [date] as plain plan rows (for AI prompts and the library). */
    suspend fun presetPlanForDate(date: String): List<RoutinePlanActivity>? {
        val content = getActiveRoutinePresetContent() ?: return null
        return runCatching { planActivitiesForDate(decodeRoutinePlan(content), date) }.getOrNull()
    }

    fun encodePlanActivitiesJson(items: List<RoutinePlanActivity>): String =
        presetJson.encodeToString(ListSerializer(RoutinePlanActivity.serializer()), items)

    /** Every activity in the active preset, across all weekdays. */
    suspend fun allPresetActivities(): List<RoutinePlanActivity> {
        val content = getActiveRoutinePresetContent() ?: return emptyList()
        return runCatching { decodeRoutinePlan(content).weekly_plan.all() }.getOrDefault(emptyList())
    }

    private fun planToEntries(
        items: List<RoutinePlanActivity>,
        date: String,
        labels: List<ActivityLabel>,
    ): List<ActivityEntry> {
        val fallbackLabelId = (labels.firstOrNull { !it.isSystem } ?: labels.firstOrNull())?.id.orEmpty()
        return items.mapIndexed { index, item ->
            val labelId = labels.find { it.name.equals(item.label.trim(), ignoreCase = true) }?.id
                ?: fallbackLabelId
            ActivityEntry(
                id = "preset_${date}_$index",
                date = date,
                title = item.title.trim(),
                startTime = normalizeActivityHhMm(item.startTime, ActivityImportDefaults.START_TIME),
                endTime = normalizeActivityHhMm(item.endTime, ActivityImportDefaults.END_TIME),
                labelId = labelId,
                completion = ActivityCompletion.NONE,
                type = parseActivityTypeOrDefault(item.type, ActivityType.PLANNED),
                description = item.description.trim(),
                timestamp = 0L,
            )
        }.sortedWith(
            compareBy(
                { parseSortMinutes(it.startTime) },
                { parseSortMinutes(it.endTime) },
            )
        )
    }

    class NoRoutinePresetException : Exception("No routine preset active. Load one in Settings.")

    private companion object {
        val WEEKDAY_KEYS = listOf("monday", "tuesday", "wednesday", "thursday", "friday", "saturday", "sunday")
        val NEW_PRESET_LABEL_COLOR: Int = 0xFF546E7A.toInt()
    }
}
