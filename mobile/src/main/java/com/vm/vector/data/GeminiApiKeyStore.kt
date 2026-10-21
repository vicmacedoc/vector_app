package com.vm.vector.data

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.vm.vector.BuildConfig

enum class GeminiApiKeySource {
    Custom,
    BuildConfig,
    None,
}

data class GeminiApiKeyResolution(
    val key: String,
    val source: GeminiApiKeySource,
) {
    val isConfigured: Boolean get() = key.isNotBlank()
}

/**
 * Runtime Gemini API key (Settings) with BuildConfig fallback for local builds.
 * Stored in EncryptedSharedPreferences — not synced via Drive/DataStore.
 */
class GeminiApiKeyStore(context: Context) {
    private val appContext = context.applicationContext

    private val prefs by lazy {
        val masterKey = MasterKey.Builder(appContext)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            appContext,
            PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    fun getCustomKey(): String =
        prefs.getString(KEY_GEMINI_API, "").orEmpty()

    fun setCustomKey(key: String) {
        prefs.edit().putString(KEY_GEMINI_API, key.trim()).apply()
    }

    fun clearCustomKey() {
        prefs.edit().remove(KEY_GEMINI_API).apply()
    }

    fun resolve(): GeminiApiKeyResolution {
        val custom = getCustomKey()
        if (custom.isNotBlank()) {
            return GeminiApiKeyResolution(custom, GeminiApiKeySource.Custom)
        }
        val buildKey = BuildConfig.GEMINI_API_KEY
        if (buildKey.isNotBlank()) {
            return GeminiApiKeyResolution(buildKey, GeminiApiKeySource.BuildConfig)
        }
        return GeminiApiKeyResolution("", GeminiApiKeySource.None)
    }

    fun statusLabel(): String = when (resolve().source) {
        GeminiApiKeySource.Custom -> "Using custom key"
        GeminiApiKeySource.BuildConfig -> "Using build key"
        GeminiApiKeySource.None -> "Not configured"
    }

    companion object {
        private const val PREFS_NAME = "vector_gemini_secrets"
        private const val KEY_GEMINI_API = "gemini_api_key"
        const val MISSING_KEY_MESSAGE =
            "Gemini API key missing. Set it in Settings, or add GEMINI_API_KEY to local.properties."
    }
}
