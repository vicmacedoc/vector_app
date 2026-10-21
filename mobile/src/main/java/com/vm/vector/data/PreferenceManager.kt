package com.vm.vector.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "vector_preferences")

class PreferenceManager(private val context: Context) {
    companion object {
        private val GOOGLE_DRIVE_FOLDER_ID_KEY = stringPreferencesKey("google_drive_folder_id")
        private val LAST_OPENED_LIST_ID_KEY = stringPreferencesKey("last_opened_list_id")
        private val WORKOUT_PRESET_DISPLAY_NAME_KEY = stringPreferencesKey("workout_preset_display_name")
        private val FOOD_PRESET_DISPLAY_NAME_KEY = stringPreferencesKey("food_preset_display_name")
        private val WORKOUT_PRESET_JSON_NAME_KEY = stringPreferencesKey("workout_preset_json_name")
        private val FOOD_PRESET_JSON_NAME_KEY = stringPreferencesKey("food_preset_json_name")
        private val ROUTINE_PRESET_DISPLAY_NAME_KEY = stringPreferencesKey("routine_preset_display_name")
        private val ROUTINE_PRESET_JSON_NAME_KEY = stringPreferencesKey("routine_preset_json_name")
        private val PRESET_OVERWRITE_DATE_ROUTINE_KEY = stringPreferencesKey("preset_overwrite_date_routine")
        private val WAKE_ALARM_BASE_MINUTES_KEY = intPreferencesKey("wake_alarm_base_minutes")
        private val WAKE_ALARM_OFFSETS_KEY = stringPreferencesKey("wake_alarm_offsets") // "0,5,10,15"
        private val FILLOUT_REMINDER_MINUTES_KEY = intPreferencesKey("fillout_reminder_minutes")
        private val PRESET_OVERWRITE_DATE_FOOD_KEY = stringPreferencesKey("preset_overwrite_date_food")
        private val PRESET_OVERWRITE_DATE_WORKOUT_KEY = stringPreferencesKey("preset_overwrite_date_workout")
        /** Persisted sleep targets (Settings) so they survive database reset. */
        private val SLEEP_TARGET_BEDTIME_MINUTES_KEY = intPreferencesKey("sleep_target_bedtime_minutes")
        private val SLEEP_TARGET_WAKEUP_MINUTES_KEY = intPreferencesKey("sleep_target_wakeup_minutes")
        private val AUDIO_SEEK_BACK_SEC_KEY = intPreferencesKey("audio_seek_back_sec")
        private val AUDIO_SEEK_FORWARD_SEC_KEY = intPreferencesKey("audio_seek_forward_sec")
        private val LEARN_ON_HOLD_DAYS_KEY = intPreferencesKey("learn_on_hold_days")
        private val LEARN_REVIEW_INTERVALS_KEY = stringPreferencesKey("learn_review_intervals") // "3,7,21,60,180"
        private val LEARN_QUIZ_SIZE_KEY = intPreferencesKey("learn_quiz_size")
        private val LEARN_PASS_PERCENT_KEY = intPreferencesKey("learn_pass_percent")
        private val LEARN_TEXT_BACKFILL_DONE_KEY = booleanPreferencesKey("learn_text_backfill_done")
        private val MOTION_DAILY_TARGET_KEY = intPreferencesKey("motion_daily_target")
        private val MOTION_SUPERMARKET_DAY_KEY = stringPreferencesKey("motion_supermarket_day") // "SUNDAY"
        private val INSTALL_ID_KEY = stringPreferencesKey("install_id")
        private val LAST_DB_BACKUP_AT_KEY = longPreferencesKey("last_db_backup_at")
        private val BACKUP_CONFLICT_KEY = stringPreferencesKey("backup_conflict_json")
        private val LAST_BRAIN_SYNC_AT_KEY = longPreferencesKey("last_brain_sync_at")

        val AUDIO_SEEK_CHOICES = setOf(1, 5, 10, 15, 30, 60)
        const val DEFAULT_SEEK_BACK_SEC = 15
        const val DEFAULT_SEEK_FORWARD_SEC = 30
    }

    val googleDriveFolderId: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[GOOGLE_DRIVE_FOLDER_ID_KEY]
    }

    val workoutPresetDisplayName: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[WORKOUT_PRESET_DISPLAY_NAME_KEY]
    }

    val foodPresetDisplayName: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[FOOD_PRESET_DISPLAY_NAME_KEY]
            ?: preferences[stringPreferencesKey("diet_preset_display_name")]
    }

    val routinePresetDisplayName: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[ROUTINE_PRESET_DISPLAY_NAME_KEY]
    }

    val routinePresetJsonName: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[ROUTINE_PRESET_JSON_NAME_KEY]
    }

    val workoutPresetJsonName: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[WORKOUT_PRESET_JSON_NAME_KEY]
    }

    val foodPresetJsonName: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[FOOD_PRESET_JSON_NAME_KEY]
            ?: preferences[stringPreferencesKey("diet_preset_json_name")]
    }

    val lastOpenedListId: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[LAST_OPENED_LIST_ID_KEY]
    }

    val wakeAlarmBaseMinutes: Flow<Int?> = context.dataStore.data.map { preferences ->
        preferences[WAKE_ALARM_BASE_MINUTES_KEY]
    }

    val wakeAlarmOffsets: Flow<List<Int>> = context.dataStore.data.map { preferences ->
        preferences[WAKE_ALARM_OFFSETS_KEY]?.split(",")?.mapNotNull { it.trim().toIntOrNull() } ?: emptyList()
    }

    val filloutReminderMinutes: Flow<Int?> = context.dataStore.data.map { preferences ->
        preferences[FILLOUT_REMINDER_MINUTES_KEY]
    }

    val sleepTargetBedtimeMinutes: Flow<Int?> = context.dataStore.data.map { preferences ->
        preferences[SLEEP_TARGET_BEDTIME_MINUTES_KEY]
    }

    val sleepTargetWakeupMinutes: Flow<Int?> = context.dataStore.data.map { preferences ->
        preferences[SLEEP_TARGET_WAKEUP_MINUTES_KEY]
    }

    val audioSeekBackSec: Flow<Int> = context.dataStore.data.map { preferences ->
        sanitizeSeek(preferences[AUDIO_SEEK_BACK_SEC_KEY], DEFAULT_SEEK_BACK_SEC)
    }

    val audioSeekForwardSec: Flow<Int> = context.dataStore.data.map { preferences ->
        sanitizeSeek(preferences[AUDIO_SEEK_FORWARD_SEC_KEY], DEFAULT_SEEK_FORWARD_SEC)
    }

    suspend fun saveGoogleDriveFolderId(folderId: String) {
        context.dataStore.edit { preferences ->
            preferences[GOOGLE_DRIVE_FOLDER_ID_KEY] = folderId
        }
    }

    suspend fun saveLastOpenedListId(fileId: String?) {
        context.dataStore.edit { preferences ->
            if (fileId != null) {
                preferences[LAST_OPENED_LIST_ID_KEY] = fileId
            } else {
                preferences.remove(LAST_OPENED_LIST_ID_KEY)
            }
        }
    }

    suspend fun saveWakeAlarmBaseMinutes(minutes: Int?) {
        context.dataStore.edit { preferences ->
            if (minutes != null) {
                preferences[WAKE_ALARM_BASE_MINUTES_KEY] = minutes
            } else {
                preferences.remove(WAKE_ALARM_BASE_MINUTES_KEY)
            }
        }
    }

    suspend fun saveWakeAlarmOffsets(offsets: List<Int>) {
        context.dataStore.edit { preferences ->
            if (offsets.isEmpty()) {
                preferences.remove(WAKE_ALARM_OFFSETS_KEY)
            } else {
                preferences[WAKE_ALARM_OFFSETS_KEY] = offsets.sorted().joinToString(",")
            }
        }
    }

    suspend fun saveFilloutReminderMinutes(minutes: Int?) {
        context.dataStore.edit { preferences ->
            if (minutes != null) {
                preferences[FILLOUT_REMINDER_MINUTES_KEY] = minutes
            } else {
                preferences.remove(FILLOUT_REMINDER_MINUTES_KEY)
            }
        }
    }

    suspend fun saveSleepTargetMinutes(bedtimeMinutes: Int, wakeupMinutes: Int) {
        context.dataStore.edit { preferences ->
            preferences[SLEEP_TARGET_BEDTIME_MINUTES_KEY] = bedtimeMinutes
            preferences[SLEEP_TARGET_WAKEUP_MINUTES_KEY] = wakeupMinutes
        }
    }

    suspend fun savePresetDisplayName(category: PresetCategory, displayName: String?) {
        context.dataStore.edit { preferences ->
            val key = when (category) {
                PresetCategory.Workout -> WORKOUT_PRESET_DISPLAY_NAME_KEY
                PresetCategory.Food -> FOOD_PRESET_DISPLAY_NAME_KEY
                PresetCategory.Routine -> ROUTINE_PRESET_DISPLAY_NAME_KEY
            }
            if (displayName != null) {
                preferences[key] = displayName
            } else {
                preferences.remove(key)
            }
        }
    }

    suspend fun savePresetJsonName(category: PresetCategory, name: String?) {
        context.dataStore.edit { preferences ->
            val key = when (category) {
                PresetCategory.Workout -> WORKOUT_PRESET_JSON_NAME_KEY
                PresetCategory.Food -> FOOD_PRESET_JSON_NAME_KEY
                PresetCategory.Routine -> ROUTINE_PRESET_JSON_NAME_KEY
            }
            if (name != null && name.isNotBlank()) {
                preferences[key] = name
            } else {
                preferences.remove(key)
            }
        }
    }

    suspend fun setPresetOverwriteDate(category: PresetCategory, date: String) {
        context.dataStore.edit { preferences ->
            val key = when (category) {
                PresetCategory.Workout -> PRESET_OVERWRITE_DATE_WORKOUT_KEY
                PresetCategory.Food -> PRESET_OVERWRITE_DATE_FOOD_KEY
                PresetCategory.Routine -> PRESET_OVERWRITE_DATE_ROUTINE_KEY
            }
            preferences[key] = date
        }
    }

    suspend fun getPresetOverwriteDateFood(): String? =
        context.dataStore.data.first()[PRESET_OVERWRITE_DATE_FOOD_KEY]
            ?: context.dataStore.data.first()[stringPreferencesKey("preset_overwrite_date_diet")]

    suspend fun getPresetOverwriteDateWorkout(): String? =
        context.dataStore.data.first()[PRESET_OVERWRITE_DATE_WORKOUT_KEY]

    suspend fun saveAudioSeekBackSec(seconds: Int) {
        context.dataStore.edit { preferences ->
            preferences[AUDIO_SEEK_BACK_SEC_KEY] = sanitizeSeek(seconds, DEFAULT_SEEK_BACK_SEC)
        }
    }

    suspend fun saveAudioSeekForwardSec(seconds: Int) {
        context.dataStore.edit { preferences ->
            preferences[AUDIO_SEEK_FORWARD_SEC_KEY] = sanitizeSeek(seconds, DEFAULT_SEEK_FORWARD_SEC)
        }
    }

    val learningSettings: Flow<LearningSettings> = context.dataStore.data.map { preferences ->
        LearningSettings.sanitized(
            onHoldDays = preferences[LEARN_ON_HOLD_DAYS_KEY],
            reviewIntervalsDays = preferences[LEARN_REVIEW_INTERVALS_KEY]
                ?.split(",")
                ?.mapNotNull { it.trim().toIntOrNull() },
            quizSize = preferences[LEARN_QUIZ_SIZE_KEY],
            passPercent = preferences[LEARN_PASS_PERCENT_KEY],
        )
    }

    suspend fun saveLearningSettings(settings: LearningSettings) {
        val clean = LearningSettings.sanitized(
            settings.onHoldDays,
            settings.reviewIntervalsDays,
            settings.quizSize,
            settings.passPercent,
        )
        context.dataStore.edit { preferences ->
            preferences[LEARN_ON_HOLD_DAYS_KEY] = clean.onHoldDays
            preferences[LEARN_REVIEW_INTERVALS_KEY] = clean.reviewIntervalsDays.joinToString(",")
            preferences[LEARN_QUIZ_SIZE_KEY] = clean.quizSize
            preferences[LEARN_PASS_PERCENT_KEY] = clean.passPercent
        }
    }

    val motionSettings: Flow<MotionSettings> = context.dataStore.data.map { preferences ->
        MotionSettings.sanitized(
            dailyTarget = preferences[MOTION_DAILY_TARGET_KEY],
            supermarketDay = preferences[MOTION_SUPERMARKET_DAY_KEY],
        )
    }

    suspend fun saveMotionSettings(settings: MotionSettings) {
        val clean = MotionSettings.sanitized(settings.dailyTarget, settings.supermarketDay.name)
        context.dataStore.edit { preferences ->
            preferences[MOTION_DAILY_TARGET_KEY] = clean.dailyTarget
            preferences[MOTION_SUPERMARKET_DAY_KEY] = clean.supermarketDay.name
        }
    }

    suspend fun isLearnTextBackfillDone(): Boolean =
        context.dataStore.data.first()[LEARN_TEXT_BACKFILL_DONE_KEY] == true

    suspend fun setLearnTextBackfillDone() {
        context.dataStore.edit { preferences -> preferences[LEARN_TEXT_BACKFILL_DONE_KEY] = true }
    }

    /** Random ID created on first use; identifies this install in backup manifests and Brain sync. */
    suspend fun installId(): String {
        context.dataStore.data.first()[INSTALL_ID_KEY]?.let { return it }
        var id = ""
        context.dataStore.edit { preferences ->
            id = preferences[INSTALL_ID_KEY] ?: java.util.UUID.randomUUID().toString().also {
                preferences[INSTALL_ID_KEY] = it
            }
        }
        return id
    }

    suspend fun lastDbBackupAt(): Long = context.dataStore.data.first()[LAST_DB_BACKUP_AT_KEY] ?: 0L

    suspend fun setLastDbBackupAt(millis: Long) {
        context.dataStore.edit { preferences -> preferences[LAST_DB_BACKUP_AT_KEY] = millis }
    }

    /** JSON of the remote backup manifest that blocked an automatic backup; null when there is no conflict. */
    val backupConflict: Flow<String?> = context.dataStore.data.map { it[BACKUP_CONFLICT_KEY] }

    suspend fun setBackupConflict(json: String?) {
        context.dataStore.edit { preferences ->
            if (json == null) preferences.remove(BACKUP_CONFLICT_KEY) else preferences[BACKUP_CONFLICT_KEY] = json
        }
    }

    val lastBrainSyncAt: Flow<Long> = context.dataStore.data.map { it[LAST_BRAIN_SYNC_AT_KEY] ?: 0L }

    suspend fun setLastBrainSyncAt(millis: Long) {
        context.dataStore.edit { preferences -> preferences[LAST_BRAIN_SYNC_AT_KEY] = millis }
    }

    /** Every stored preference as text, for the settings backup. Internal sync/backup bookkeeping is left out. */
    suspend fun exportSettings(): Map<String, String> {
        val internal = setOf(BACKUP_CONFLICT_KEY.name, LAST_DB_BACKUP_AT_KEY.name, LAST_BRAIN_SYNC_AT_KEY.name)
        return context.dataStore.data.first().asMap()
            .filterKeys { it.name !in internal }
            .entries.associate { (key, value) -> key.name to value.toString() }
            .toSortedMap()
    }

    private fun sanitizeSeek(seconds: Int?, fallback: Int): Int =
        if (seconds != null && seconds in AUDIO_SEEK_CHOICES) seconds else fallback
}
