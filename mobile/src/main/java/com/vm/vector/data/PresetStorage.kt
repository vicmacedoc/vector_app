package com.vm.vector.data

import android.content.Context
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import org.json.JSONObject
import java.io.File

/**
 * Stores preset JSON content in app files and display names in preferences.
 * Used when user picks a file (e.g. from Drive or device) to use as weekly plan.
 */
class PresetStorage(
    private val context: Context,
    private val preferenceManager: PreferenceManager,
) {

    private fun presetFile(category: PresetCategory): File =
        File(context.filesDir, "preset_${category.fileSuffix}.json")

    fun hasPreset(category: PresetCategory): Boolean {
        val primary = presetFile(category)
        if (primary.exists()) return true
        // Migrate legacy diet preset file name
        if (category == PresetCategory.Food) {
            val legacy = File(context.filesDir, "preset_diet.json")
            if (legacy.exists()) {
                legacy.copyTo(primary, overwrite = true)
                legacy.delete()
                return true
            }
        }
        return false
    }

    suspend fun getPresetContent(category: PresetCategory): String? {
        hasPreset(category) // trigger legacy migrate
        val file = presetFile(category)
        return if (file.exists()) file.readText() else null
    }

    fun getPresetDisplayNameFlow(category: PresetCategory): Flow<String?> = when (category) {
        PresetCategory.Workout -> preferenceManager.workoutPresetDisplayName
        PresetCategory.Food -> preferenceManager.foodPresetDisplayName
        PresetCategory.Routine -> preferenceManager.routinePresetDisplayName
    }

    suspend fun getPresetDisplayName(category: PresetCategory): String? =
        getPresetDisplayNameFlow(category).first()

    suspend fun savePreset(category: PresetCategory, displayName: String, content: String) {
        presetFile(category).writeText(content)
        preferenceManager.savePresetDisplayName(category, displayName)
        val jsonName = parseNameFromPresetJson(content)
        preferenceManager.savePresetJsonName(category, jsonName)
    }

    suspend fun clearPreset(category: PresetCategory) {
        presetFile(category).delete()
        if (category == PresetCategory.Food) {
            File(context.filesDir, "preset_diet.json").delete()
        }
        preferenceManager.savePresetDisplayName(category, null)
        preferenceManager.savePresetJsonName(category, null)
    }

    private fun parseNameFromPresetJson(content: String): String? {
        return try {
            val obj = JSONObject(content)
            obj.optString("name", "").trim().takeIf { it.isNotBlank() }
        } catch (_: Exception) {
            null
        }
    }
}
