package com.vm.vector.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "vector_preferences")

class PreferenceManager(private val context: Context) {
    companion object {
        private val GOOGLE_DRIVE_FOLDER_ID_KEY = stringPreferencesKey("google_drive_folder_id")
        private val DAILY_PLAN_DRAFT_KEY = stringPreferencesKey("daily_plan_draft")
    }

    val googleDriveFolderId: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[GOOGLE_DRIVE_FOLDER_ID_KEY]
    }

    val dailyPlanDraft: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[DAILY_PLAN_DRAFT_KEY]
    }

    suspend fun saveGoogleDriveFolderId(folderId: String) {
        context.dataStore.edit { preferences ->
            preferences[GOOGLE_DRIVE_FOLDER_ID_KEY] = folderId
        }
    }

    suspend fun saveDailyPlanDraft(text: String) {
        context.dataStore.edit { preferences ->
            preferences[DAILY_PLAN_DRAFT_KEY] = text
        }
    }
}
