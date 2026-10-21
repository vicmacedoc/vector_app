package com.vm.vector.data.sync

import android.content.Context
import androidx.room.withTransaction
import com.vm.core.models.ActivityEntry
import com.vm.core.models.ActivityLabel
import com.vm.core.models.ActivityLibraryItem
import com.vm.core.models.DailyHomeEntry
import com.vm.core.models.ExerciseLibraryItem
import com.vm.core.models.FoodEntry
import com.vm.core.models.HabitDayConfig
import com.vm.core.models.HabitDefinition
import com.vm.core.models.HabitLog
import com.vm.core.models.MealLibraryItem
import com.vm.core.models.SelfEntry
import com.vm.core.models.SleepEntry
import com.vm.core.models.VectorDatabase
import com.vm.core.models.WorkoutSet
import com.vm.vector.alarm.SleepAlarmScheduler
import com.vm.vector.data.LearningSettings
import com.vm.vector.data.PreferenceManager
import com.vm.vector.data.PresetCategory
import com.vm.vector.data.PresetStorage
import kotlinx.coroutines.flow.first
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import java.io.File
import java.security.MessageDigest

/** Reads and writes one daily shard (a month of Calendar/Home rows, or the config shard) from Room and prefs. */
class DailySnapshotStore(
    context: Context,
    private val database: VectorDatabase,
) {
    private val appContext = context.applicationContext
    private val preferences = PreferenceManager(appContext)
    private val presets = PresetStorage(appContext, preferences)
    private val json = ShardFile.json

    /** Every shard that has local rows, plus [DailyShards.CONFIG]. */
    suspend fun localShards(): Set<String> {
        val months = buildSet {
            addAll(database.foodEntryDao().getMonths())
            addAll(database.activityEntryDao().getMonths())
            addAll(database.sleepEntryDao().getMonths())
            addAll(database.workoutSetDao().getMonths())
            addAll(database.selfEntryDao().getMonths())
            addAll(database.dailyHomeEntryDao().getMonths())
            addAll(database.habitLogDao().getMonths())
            addAll(database.habitDayConfigDao().getMonths())
        }
        return months.filter { MONTH.matches(it) }.map { "days/$it" }.toSet() + DailyShards.CONFIG
    }

    suspend fun export(shard: String): RecordSet {
        val month = DailyShards.month(shard) ?: return exportConfig()
        val (start, end) = range(month)
        return mapOf(
            DailyShards.FOOD to encode(database.foodEntryDao().getEntriesBetweenSync(start, end), FoodEntry.serializer()) { it.id },
            DailyShards.ACTIVITIES to encode(database.activityEntryDao().getBetweenSync(start, end), ActivityEntry.serializer()) { it.id },
            DailyShards.SLEEP to encode(database.sleepEntryDao().getBetweenSync(start, end), SleepEntry.serializer()) { it.date },
            DailyShards.WORKOUT_SETS to encode(database.workoutSetDao().getSetsBetweenSync(start, end), WorkoutSet.serializer()) { it.id },
            DailyShards.SELF to encode(database.selfEntryDao().getEntriesBetweenSync(start, end), SelfEntry.serializer()) { it.date },
            DailyShards.HOME to encode(
                database.dailyHomeEntryDao().getEntriesBetweenSync(start, end).map { it.copy(dailyPlanAudioPath = null) },
                DailyHomeEntry.serializer(),
            ) { it.date }.mapValues { (_, obj) -> JsonObject(obj - "dailyPlanAudioPath") },
            DailyShards.HABIT_LOGS to encode(database.habitLogDao().getBetweenSync(start, end), HabitLog.serializer()) { it.id },
            DailyShards.HABIT_CONFIGS to encode(database.habitDayConfigDao().getBetweenSync(start, end), HabitDayConfig.serializer()) { it.date },
        ).filterValues { it.isNotEmpty() }
    }

    /** Replaces the shard's rows with [set] in one transaction. */
    suspend fun import(shard: String, set: RecordSet) {
        DailyImportGuard.run {
            val month = DailyShards.month(shard)
            if (month == null) importConfig(set) else importMonth(month, set)
        }
    }

    private suspend fun importMonth(month: String, set: RecordSet) {
        val (start, end) = range(month)
        val homeDao = database.dailyHomeEntryDao()
        val audioByDate = homeDao.getEntriesBetweenSync(start, end)
            .mapNotNull { e -> e.dailyPlanAudioPath?.let { e.date to it } }.toMap()
        database.withTransaction {
            database.foodEntryDao().deleteBetween(start, end)
            database.foodEntryDao().insertEntries(decode(set, DailyShards.FOOD, FoodEntry.serializer()))
            database.activityEntryDao().deleteBetween(start, end)
            database.activityEntryDao().insertAll(decode(set, DailyShards.ACTIVITIES, ActivityEntry.serializer()))
            database.sleepEntryDao().deleteBetween(start, end)
            database.sleepEntryDao().upsertAll(decode(set, DailyShards.SLEEP, SleepEntry.serializer()))
            database.workoutSetDao().deleteBetween(start, end)
            database.workoutSetDao().insertSets(decode(set, DailyShards.WORKOUT_SETS, WorkoutSet.serializer()))
            database.selfEntryDao().deleteBetween(start, end)
            database.selfEntryDao().insertAll(decode(set, DailyShards.SELF, SelfEntry.serializer()))
            homeDao.deleteBetween(start, end)
            homeDao.insertAll(
                decode(set, DailyShards.HOME, DailyHomeEntry.serializer())
                    .map { it.copy(dailyPlanAudioPath = audioByDate[it.date]) }
            )
            database.habitLogDao().deleteBetween(start, end)
            database.habitLogDao().insertAll(decode(set, DailyShards.HABIT_LOGS, HabitLog.serializer()))
            database.habitDayConfigDao().deleteBetween(start, end)
            database.habitDayConfigDao().insertAll(decode(set, DailyShards.HABIT_CONFIGS, HabitDayConfig.serializer()))
        }
    }

    private suspend fun exportConfig(): RecordSet = mapOf(
        DailyShards.LABELS to encode(database.activityLabelDao().getAllSync(), ActivityLabel.serializer()) { it.id },
        DailyShards.HABITS to encode(database.habitDefinitionDao().getAllSync(), HabitDefinition.serializer()) { it.id },
        DailyShards.MEAL_LIBRARY to encode(database.mealLibraryItemDao().getAllSync(), MealLibraryItem.serializer()) { it.nameKey },
        DailyShards.EXERCISE_LIBRARY to encode(database.exerciseLibraryItemDao().getAllSync(), ExerciseLibraryItem.serializer()) {
            "${it.typeKey}|${it.nameKey}"
        },
        DailyShards.ACTIVITY_LIBRARY to encode(database.activityLibraryItemDao().getAllSync(), ActivityLibraryItem.serializer()) { it.nameKey },
        DailyShards.PRESETS to exportPresets(),
        DailyShards.SETTINGS to mapOf(DailyShards.SHARED_SETTINGS_KEY to exportSettings()),
    ).filterValues { it.isNotEmpty() }

    private suspend fun importConfig(set: RecordSet) {
        database.withTransaction {
            database.activityLabelDao().deleteAll()
            decode(set, DailyShards.LABELS, ActivityLabel.serializer()).forEach { database.activityLabelDao().insert(it) }
            database.habitDefinitionDao().deleteAll()
            decode(set, DailyShards.HABITS, HabitDefinition.serializer()).forEach { database.habitDefinitionDao().insert(it) }
            database.mealLibraryItemDao().deleteAll()
            database.mealLibraryItemDao().insertAll(decode(set, DailyShards.MEAL_LIBRARY, MealLibraryItem.serializer()))
            database.exerciseLibraryItemDao().deleteAll()
            database.exerciseLibraryItemDao().insertAll(decode(set, DailyShards.EXERCISE_LIBRARY, ExerciseLibraryItem.serializer()))
            database.activityLibraryItemDao().deleteAll()
            database.activityLibraryItemDao().insertAll(decode(set, DailyShards.ACTIVITY_LIBRARY, ActivityLibraryItem.serializer()))
        }
        importPresets(set[DailyShards.PRESETS].orEmpty())
        set[DailyShards.SETTINGS]?.get(DailyShards.SHARED_SETTINGS_KEY)?.let { importSettings(it) }
    }

    // Presets live as files; their updatedAt is remembered next to a content hash so an unchanged file keeps its stamp.

    @Serializable
    private data class PresetStamp(val hash: String, val updatedAt: Long)

    private val stampSerializer = MapSerializer(String.serializer(), PresetStamp.serializer())
    private val stampsFile = File(File(appContext.filesDir, ShardSyncFiles.DIR).apply { mkdirs() }, "preset_stamps.json")

    private fun readStamps(): Map<String, PresetStamp> =
        runCatching { json.decodeFromString(stampSerializer, stampsFile.readText()) }.getOrDefault(emptyMap())

    private fun writeStamps(stamps: Map<String, PresetStamp>) {
        stampsFile.writeText(json.encodeToString(stampSerializer, stamps))
    }

    private suspend fun exportPresets(): Records {
        val stamps = readStamps().toMutableMap()
        val out = mutableMapOf<String, JsonObject>()
        for (category in PresetCategory.entries) {
            val content = presets.getPresetContent(category) ?: continue
            val hash = sha(content)
            val updatedAt = stamps[category.name]?.takeIf { it.hash == hash }?.updatedAt
                ?: System.currentTimeMillis().also { stamps[category.name] = PresetStamp(hash, it) }
            out[category.name] = JsonObject(
                mapOf(
                    "category" to JsonPrimitive(category.name),
                    "displayName" to JsonPrimitive(presets.getPresetDisplayName(category) ?: category.name),
                    "content" to JsonPrimitive(content),
                    "updatedAt" to JsonPrimitive(updatedAt),
                )
            )
        }
        writeStamps(stamps.filterKeys { it in out })
        return out
    }

    private suspend fun importPresets(records: Records) {
        val stamps = mutableMapOf<String, PresetStamp>()
        for (category in PresetCategory.entries) {
            val record = records[category.name]
            if (record == null) {
                if (presets.hasPreset(category)) presets.clearPreset(category)
                continue
            }
            val content = (record["content"] as? JsonPrimitive)?.content ?: continue
            val displayName = (record["displayName"] as? JsonPrimitive)?.content ?: category.name
            if (presets.getPresetContent(category) != content || presets.getPresetDisplayName(category) != displayName) {
                presets.savePreset(category, displayName, content)
            }
            stamps[category.name] = PresetStamp(sha(content), record["updatedAt"]?.jsonPrimitive?.longOrNull ?: 0L)
        }
        writeStamps(stamps)
    }

    private suspend fun exportSettings(): JsonObject {
        val learning = preferences.learningSettings.first()
        return JsonObject(
            mapOf(
                "sleepTargetBedtimeMinutes" to nullableInt(preferences.sleepTargetBedtimeMinutes.first()),
                "sleepTargetWakeupMinutes" to nullableInt(preferences.sleepTargetWakeupMinutes.first()),
                "filloutReminderMinutes" to nullableInt(preferences.filloutReminderMinutes.first()),
                "learnOnHoldDays" to JsonPrimitive(learning.onHoldDays),
                "learnReviewIntervals" to JsonArray(learning.reviewIntervalsDays.map { JsonPrimitive(it) }),
                "learnQuizSize" to JsonPrimitive(learning.quizSize),
                "learnPassPercent" to JsonPrimitive(learning.passPercent),
            )
        )
    }

    private suspend fun importSettings(record: JsonObject) {
        fun int(key: String): Int? = (record[key] as? JsonPrimitive)?.intOrNull
        val bedtime = int("sleepTargetBedtimeMinutes")
        val wakeup = int("sleepTargetWakeupMinutes")
        if (bedtime != null && wakeup != null) preferences.saveSleepTargetMinutes(bedtime, wakeup)

        val fillout = int("filloutReminderMinutes")
        if (fillout != preferences.filloutReminderMinutes.first()) {
            preferences.saveFilloutReminderMinutes(fillout)
            val scheduler = SleepAlarmScheduler(appContext)
            if (fillout != null) scheduler.scheduleFilloutReminder(fillout) else scheduler.cancelFilloutReminder()
        }

        val current = preferences.learningSettings.first()
        val intervals = runCatching { record["learnReviewIntervals"]?.jsonArray?.mapNotNull { it.jsonPrimitive.intOrNull } }
            .getOrNull()?.takeIf { it.isNotEmpty() } ?: current.reviewIntervalsDays
        preferences.saveLearningSettings(
            LearningSettings(
                onHoldDays = int("learnOnHoldDays") ?: current.onHoldDays,
                reviewIntervalsDays = intervals,
                quizSize = int("learnQuizSize") ?: current.quizSize,
                passPercent = int("learnPassPercent") ?: current.passPercent,
            )
        )
    }

    private fun <T> encode(rows: List<T>, serializer: KSerializer<T>, key: (T) -> String): Records =
        rows.associate { key(it) to json.encodeToJsonElement(serializer, it).jsonObject }

    private fun <T> decode(set: RecordSet, collection: String, serializer: KSerializer<T>): List<T> =
        set[collection].orEmpty().values.mapNotNull { runCatching { json.decodeFromJsonElement(serializer, it) }.getOrNull() }

    private fun nullableInt(value: Int?) = value?.let { JsonPrimitive(it) } ?: JsonNull

    private fun range(month: String) = "$month-01" to "$month-31"

    private fun sha(text: String): String =
        MessageDigest.getInstance("SHA-256").digest(text.toByteArray()).joinToString("") { "%02x".format(it) }

    private companion object {
        val MONTH = Regex("""\d{4}-\d{2}""")
    }
}

/** Room writes made by sync don't count as local edits; see [BrainImportGuard]. */
object DailyImportGuard {
    private const val QUIET_MS = 3_000L

    @Volatile
    private var running = false

    @Volatile
    private var quietUntil = 0L

    val isQuiet: Boolean get() = running || System.currentTimeMillis() < quietUntil

    suspend fun <T> run(block: suspend () -> T): T {
        running = true
        try {
            return block()
        } finally {
            quietUntil = System.currentTimeMillis() + QUIET_MS
            running = false
        }
    }
}
