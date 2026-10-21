package com.vm.vector.data.sync

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

const val SHARD_FORMAT_VERSION = 1

/** Shard layout and merge policy for everything outside Brain. Protocol: docs/daily-sync-spec.md. */
object DailyShards {
    const val CONFIG = "config"

    // days/YYYY-MM
    const val FOOD = "food"
    const val ACTIVITIES = "activities"
    const val SLEEP = "sleep"
    const val WORKOUT_SETS = "workoutSets"
    const val SELF = "self"
    const val HOME = "home"
    const val HABIT_LOGS = "habitLogs"
    const val HABIT_CONFIGS = "habitConfigs"

    // config
    const val LABELS = "labels"
    const val HABITS = "habits"
    const val MEAL_LIBRARY = "mealLibrary"
    const val EXERCISE_LIBRARY = "exerciseLibrary"
    const val ACTIVITY_LIBRARY = "activityLibrary"
    const val PRESETS = "presets"
    const val SETTINGS = "settings"
    const val SHARED_SETTINGS_KEY = "shared"

    val POLICY = MergePolicy(
        keepBoth = emptySet(),
        newestRecordWins = mapOf(
            MEAL_LIBRARY to "createdAt",
            EXERCISE_LIBRARY to "createdAt",
            ACTIVITY_LIBRARY to "createdAt",
            PRESETS to "updatedAt",
            HABIT_CONFIGS to "updatedAt",
        ),
        autoFields = setOf(FOOD, ACTIVITIES, SLEEP, WORKOUT_SETS, SELF, HOME, HABIT_LOGS, LABELS, HABITS, SETTINGS),
        textFields = mapOf(
            FOOD to setOf("name", "unit"),
            ACTIVITIES to setOf("title", "description"),
            WORKOUT_SETS to setOf("sessionTitle", "sessionDescription", "exerciseName", "exerciseDescription", "description", "timing"),
            SELF to setOf("intention", "highlight", "lowlight", "gratitude", "journalText"),
            HOME to setOf("dailyPlanText"),
            LABELS to setOf("name"),
            HABITS to setOf("name"),
        ),
        finalize = { set, _ -> set },
    )

    fun monthShard(date: String): String = "days/${date.take(7)}"

    /** "YYYY-MM" for a day shard, null for [CONFIG]. */
    fun month(shard: String): String? = shard.removePrefix("days/").takeIf { shard.startsWith("days/") }

    fun fileName(shard: String): String = if (shard == CONFIG) "config.json" else "${month(shard)}.json"

    fun shardForFileName(name: String, inDays: Boolean): String? = when {
        !inDays && name == "config.json" -> CONFIG
        inDays && Regex("""\d{4}-\d{2}\.json""").matches(name) -> "days/${name.removeSuffix(".json")}"
        else -> null
    }

    fun label(shard: String): String = month(shard)?.let { "Calendar · $it" } ?: "Settings & libraries"
}

@Serializable
data class ShardFile(
    val formatVersion: Int = SHARD_FORMAT_VERSION,
    val shard: String,
    val rev: Long = 0,
    val writtenBy: SyncDevice? = null,
    val writtenAt: Long = 0,
    val records: Map<String, Map<String, JsonObject>> = emptyMap(),
) {
    fun recordSet(): RecordSet = records

    companion object {
        val json = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
            explicitNulls = true
        }

        fun encode(file: ShardFile): String = json.encodeToString(serializer(), file)
        fun decode(text: String): ShardFile = json.decodeFromString(serializer(), text)
    }
}
