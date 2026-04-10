package com.vm.vector.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
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
        private val ROUTINE_PRESET_DISPLAY_NAME_KEY = stringPreferencesKey("routine_preset_display_name")
        private val DIET_PRESET_DISPLAY_NAME_KEY = stringPreferencesKey("diet_preset_display_name")
        private val WORKOUT_PRESET_JSON_NAME_KEY = stringPreferencesKey("workout_preset_json_name")
        private val ROUTINE_PRESET_JSON_NAME_KEY = stringPreferencesKey("routine_preset_json_name")
        private val DIET_PRESET_JSON_NAME_KEY = stringPreferencesKey("diet_preset_json_name")
        private val WAKE_ALARM_BASE_MINUTES_KEY = intPreferencesKey("wake_alarm_base_minutes")
        private val WAKE_ALARM_OFFSETS_KEY = stringPreferencesKey("wake_alarm_offsets") // "0,5,10,15"
        private val FILLOUT_REMINDER_MINUTES_KEY = intPreferencesKey("fillout_reminder_minutes")
        private val PRESET_OVERWRITE_DATE_ROUTINE_KEY = stringPreferencesKey("preset_overwrite_date_routine")
        private val PRESET_OVERWRITE_DATE_DIET_KEY = stringPreferencesKey("preset_overwrite_date_diet")
        private val PRESET_OVERWRITE_DATE_WORKOUT_KEY = stringPreferencesKey("preset_overwrite_date_workout")
        /** Persisted sleep targets (Settings) so they survive database reset. */
        private val SLEEP_TARGET_BEDTIME_MINUTES_KEY = intPreferencesKey("sleep_target_bedtime_minutes")
        private val SLEEP_TARGET_WAKEUP_MINUTES_KEY = intPreferencesKey("sleep_target_wakeup_minutes")
    }

    val googleDriveFolderId: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[GOOGLE_DRIVE_FOLDER_ID_KEY]
    }

    val workoutPresetDisplayName: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[WORKOUT_PRESET_DISPLAY_NAME_KEY]
    }

    val routinePresetDisplayName: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[ROUTINE_PRESET_DISPLAY_NAME_KEY]
    }

    val dietPresetDisplayName: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[DIET_PRESET_DISPLAY_NAME_KEY]
    }

    val workoutPresetJsonName: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[WORKOUT_PRESET_JSON_NAME_KEY]
    }

    val routinePresetJsonName: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[ROUTINE_PRESET_JSON_NAME_KEY]
    }

    val dietPresetJsonName: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[DIET_PRESET_JSON_NAME_KEY]
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
                PresetCategory.Routine -> ROUTINE_PRESET_DISPLAY_NAME_KEY
                PresetCategory.Diet -> DIET_PRESET_DISPLAY_NAME_KEY
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
                PresetCategory.Routine -> ROUTINE_PRESET_JSON_NAME_KEY
                PresetCategory.Diet -> DIET_PRESET_JSON_NAME_KEY
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
                PresetCategory.Routine -> PRESET_OVERWRITE_DATE_ROUTINE_KEY
                PresetCategory.Diet -> PRESET_OVERWRITE_DATE_DIET_KEY
            }
            preferences[key] = date
        }
    }

    suspend fun getPresetOverwriteDateRoutine(): String? =
        context.dataStore.data.first()[PRESET_OVERWRITE_DATE_ROUTINE_KEY]

    suspend fun getPresetOverwriteDateDiet(): String? =
        context.dataStore.data.first()[PRESET_OVERWRITE_DATE_DIET_KEY]

    suspend fun getPresetOverwriteDateWorkout(): String? =
        context.dataStore.data.first()[PRESET_OVERWRITE_DATE_WORKOUT_KEY]
}
