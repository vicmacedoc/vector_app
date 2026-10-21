package com.vm.vector.data

import android.content.Context
import android.util.Base64
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Optional Gemini transcription + short title for Brain voice notes.
 * Never runs automatically after recording — caller must invoke explicitly.
 */
class GeminiBrainClient(
    context: Context,
    private val model: String = DEFAULT_MODEL,
    private val keyStore: GeminiApiKeyStore = GeminiApiKeyStore(context),
) {
    private val appContext: Context = context.applicationContext

    private fun prompt(section: String): String = AiPrompts.get(appContext, "brain", section)

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        explicitNulls = false
    }

    @Serializable
    data class TranscriptionResult(
        val title: String = "",
        val transcript: String = "",
    )

    @Serializable
    data class SummaryResult(
        val summary: String = "",
    )

    fun isConfigured(): Boolean = keyStore.resolve().isConfigured

    suspend fun transcribeAudio(
        audioFile: File,
        mimeType: String = "audio/mp4",
    ): Result<TranscriptionResult> = withContext(Dispatchers.IO) {
        val apiKey = keyStore.resolve().key
        if (apiKey.isBlank()) {
            return@withContext Result.failure(
                IllegalStateException(GeminiApiKeyStore.MISSING_KEY_MESSAGE)
            )
        }
        if (!audioFile.exists() || audioFile.length() == 0L) {
            return@withContext Result.failure(IllegalArgumentException("Audio file missing"))
        }
        // Keep payload reasonable for inlineData (~18MB base64 ≈ 12MB file)
        if (audioFile.length() > MAX_BYTES) {
            return@withContext Result.failure(
                IllegalArgumentException("Audio is too large to transcribe (max ~12 MB).")
            )
        }

        val bytes = audioFile.readBytes()
        val base64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
        val mime = normalizeAudioMime(mimeType)
        post(buildTranscriptionBody(base64, mime)).mapCatching { raw ->
            val result = json.decodeFromString<TranscriptionResult>(raw)
            result.copy(
                title = result.title.trim().take(80),
                transcript = result.transcript.trim(),
            )
        }
    }

    /** Short note from an existing transcript. */
    suspend fun summarizeText(text: String): Result<String> = withContext(Dispatchers.IO) {
        val trimmed = text.trim()
        if (trimmed.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Nothing to summarize"))
        }
        if (trimmed.length > MAX_SUMMARY_CHARS) {
            return@withContext Result.failure(IllegalArgumentException("Transcript is too long to summarize."))
        }
        post(buildSummaryBody(text = trimmed, base64Audio = null, mimeType = null)).mapCatching(::decodeSummary)
    }

    /** Short note from the recording itself, when there is no transcript yet. */
    suspend fun summarizeAudio(
        audioFile: File,
        mimeType: String = "audio/mp4",
    ): Result<String> = withContext(Dispatchers.IO) {
        if (!audioFile.exists() || audioFile.length() == 0L) {
            return@withContext Result.failure(IllegalArgumentException("Audio file missing"))
        }
        if (audioFile.length() > MAX_BYTES) {
            return@withContext Result.failure(
                IllegalArgumentException("Audio is too large to summarize (max ~12 MB).")
            )
        }
        val bytes = audioFile.readBytes()
        val base64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
        val mime = normalizeAudioMime(mimeType)
        post(buildSummaryBody(text = null, base64Audio = base64, mimeType = mime)).mapCatching(::decodeSummary)
    }

    private fun decodeSummary(raw: String): String {
        val summary = json.decodeFromString<SummaryResult>(raw).summary.trim()
        if (summary.isBlank()) error("Gemini returned an empty summary")
        return summary
    }

    private fun post(body: String): Result<String> {
        val apiKey = keyStore.resolve().key
        if (apiKey.isBlank()) {
            return Result.failure(IllegalStateException(GeminiApiKeyStore.MISSING_KEY_MESSAGE))
        }
        return try {
            val url = URL(
                "https://generativelanguage.googleapis.com/v1beta/models/" +
                    "$model:generateContent?key=$apiKey"
            )
            val connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 30_000
                readTimeout = 120_000
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
            }
            connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }

            val code = connection.responseCode
            val responseText = (if (code in 200..299) connection.inputStream else connection.errorStream)
                ?.bufferedReader()
                ?.use { it.readText() }
                .orEmpty()

            if (code !in 200..299) {
                Log.w(TAG, "Gemini HTTP $code: $responseText")
                return Result.failure(
                    Exception(parseApiErrorMessage(responseText) ?: "Gemini request failed (HTTP $code)")
                )
            }

            val content = extractModelJson(responseText)
                ?: return Result.failure(Exception("Gemini returned no JSON content"))
            Result.success(content)
        } catch (e: Exception) {
            Log.e(TAG, "Gemini request failed", e)
            Result.failure(e)
        }
    }

    private fun buildTranscriptionBody(base64Audio: String, mimeType: String): String {
        val parts = buildJsonArray {
            add(
                buildJsonObject {
                    put("text", prompt("transcribe"))
                }
            )
            add(
                buildJsonObject {
                    put(
                        "inlineData",
                        buildJsonObject {
                            put("mimeType", mimeType)
                            put("data", base64Audio)
                        }
                    )
                }
            )
        }
        val root = buildJsonObject {
            put(
                "contents",
                buildJsonArray {
                    add(
                        buildJsonObject {
                            put("role", "user")
                            put("parts", parts)
                        }
                    )
                }
            )
            put(
                "generationConfig",
                buildJsonObject {
                    put("temperature", 0.2)
                    put("responseMimeType", "application/json")
                    put(
                        "responseSchema",
                        buildJsonObject {
                            put("type", "OBJECT")
                            put(
                                "properties",
                                buildJsonObject {
                                    put("title", buildJsonObject { put("type", "STRING") })
                                    put("transcript", buildJsonObject { put("type", "STRING") })
                                }
                            )
                            put(
                                "required",
                                buildJsonArray {
                                    add(kotlinx.serialization.json.JsonPrimitive("title"))
                                    add(kotlinx.serialization.json.JsonPrimitive("transcript"))
                                }
                            )
                        }
                    )
                }
            )
        }
        return json.encodeToString(JsonObject.serializer(), root)
    }

    private fun buildSummaryBody(text: String?, base64Audio: String?, mimeType: String?): String {
        val parts = buildJsonArray {
            add(buildJsonObject { put("text", prompt("summary")) })
            if (!text.isNullOrBlank()) {
                add(buildJsonObject { put("text", text) })
            }
            if (!base64Audio.isNullOrBlank() && !mimeType.isNullOrBlank()) {
                add(
                    buildJsonObject {
                        put(
                            "inlineData",
                            buildJsonObject {
                                put("mimeType", mimeType)
                                put("data", base64Audio)
                            }
                        )
                    }
                )
            }
        }
        val root = buildJsonObject {
            put(
                "contents",
                buildJsonArray {
                    add(
                        buildJsonObject {
                            put("role", "user")
                            put("parts", parts)
                        }
                    )
                }
            )
            put(
                "generationConfig",
                buildJsonObject {
                    put("temperature", 0.3)
                    put("responseMimeType", "application/json")
                    put(
                        "responseSchema",
                        buildJsonObject {
                            put("type", "OBJECT")
                            put(
                                "properties",
                                buildJsonObject {
                                    put("summary", buildJsonObject { put("type", "STRING") })
                                }
                            )
                            put(
                                "required",
                                buildJsonArray {
                                    add(kotlinx.serialization.json.JsonPrimitive("summary"))
                                }
                            )
                        }
                    )
                }
            )
        }
        return json.encodeToString(JsonObject.serializer(), root)
    }

    private fun extractModelJson(responseText: String): String? {
        val root = runCatching { json.parseToJsonElement(responseText).jsonObject }.getOrNull()
            ?: return null
        val candidates = root["candidates"]?.jsonArray ?: return null
        val first = candidates.firstOrNull()?.jsonObject ?: return null
        val parts = first["content"]?.jsonObject?.get("parts")?.jsonArray ?: return null
        val text = parts.firstOrNull()?.jsonObject?.get("text")?.jsonPrimitive?.contentOrNull
            ?: return null
        return text.trim().removeSurrounding("```json").removeSurrounding("```").trim()
    }

    private fun parseApiErrorMessage(responseText: String): String? {
        val root = runCatching { json.parseToJsonElement(responseText).jsonObject }.getOrNull()
            ?: return null
        return root["error"]?.jsonObject?.get("message")?.jsonPrimitive?.contentOrNull
    }

    private fun normalizeAudioMime(mime: String): String = when {
        mime.contains("mp4", ignoreCase = true) || mime.contains("m4a", ignoreCase = true) -> "audio/mp4"
        mime.contains("mpeg", ignoreCase = true) || mime.contains("mp3", ignoreCase = true) -> "audio/mpeg"
        mime.contains("wav", ignoreCase = true) -> "audio/wav"
        mime.contains("ogg", ignoreCase = true) -> "audio/ogg"
        mime.contains("webm", ignoreCase = true) -> "audio/webm"
        mime.contains("3gpp", ignoreCase = true) || mime.contains("3gp", ignoreCase = true) -> "audio/3gpp"
        mime.isBlank() -> "audio/mp4"
        else -> mime
    }

    companion object {
        private const val TAG = "GeminiBrain"
        const val DEFAULT_MODEL = "gemini-3.6-flash"
        private const val MAX_BYTES = 12L * 1024L * 1024L
        private const val MAX_SUMMARY_CHARS = 100_000


    }
}
