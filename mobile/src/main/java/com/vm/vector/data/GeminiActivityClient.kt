package com.vm.vector.data

import android.content.Context
import android.util.Log
import com.vm.core.models.ActivityType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.net.HttpURLConnection
import java.net.URL

/**
 * Turns a free-text instruction into Routine activities via Gemini, shaped by [CalendarAiMode]:
 * one activity, several activities, or a whole-day timeline (from scratch or from the preset).
 * Returns drafts aligned with [ActivityImportDraft] (nullable fields → defaults in merge).
 */
class GeminiActivityClient(
    context: Context,
    private val model: String = DEFAULT_MODEL,
    private val keyStore: GeminiApiKeyStore = GeminiApiKeyStore(context),
) {
    private val appContext: Context = context.applicationContext

    private fun prompt(section: String): String = AiPrompts.get(appContext, "activity", section)

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    @Serializable
    data class ActivityEstimate(
        val title: String? = null,
        val startTime: String? = null,
        val endTime: String? = null,
        val label: String? = null,
        val type: String? = null,
        val description: String? = null,
    )

    @Serializable
    data class ActivityEstimateBatch(
        val activities: List<ActivityEstimate> = emptyList(),
    )

    suspend fun estimateActivities(
        mode: CalendarAiMode,
        description: String,
        date: String,
        existingLabels: List<String>,
        dayContext: CalendarDayContext = CalendarDayContext(),
    ): Result<List<ActivityImportDraft>> = withContext(Dispatchers.IO) {
        val apiKey = keyStore.resolve().key
        if (apiKey.isBlank()) {
            return@withContext Result.failure(
                IllegalStateException(GeminiApiKeyStore.MISSING_KEY_MESSAGE)
            )
        }
        val trimmed = description.trim()
        if (trimmed.isBlank() && mode != CalendarAiMode.PLAN_PRESET) {
            return@withContext Result.failure(
                IllegalArgumentException("Describe the activities for this day.")
            )
        }

        try {
            val body = buildRequestBody(mode, trimmed, date, existingLabels, dayContext)
            val url = URL(
                "https://generativelanguage.googleapis.com/v1beta/models/" +
                    "$model:generateContent?key=$apiKey"
            )
            val connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 30_000
                readTimeout = 90_000
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
            }

            connection.outputStream.use { os ->
                os.write(body.toByteArray(Charsets.UTF_8))
            }

            val code = connection.responseCode
            val responseText = (if (code in 200..299) connection.inputStream else connection.errorStream)
                ?.bufferedReader()
                ?.use { it.readText() }
                .orEmpty()

            if (code !in 200..299) {
                Log.w(TAG, "Gemini HTTP $code: $responseText")
                return@withContext Result.failure(
                    Exception(parseApiErrorMessage(responseText) ?: "Gemini request failed (HTTP $code)")
                )
            }

            val estimateJson = extractModelJson(responseText)
                ?: return@withContext Result.failure(Exception("Gemini returned no JSON content"))

            val estimates = if (mode == CalendarAiMode.SINGLE) {
                listOf(json.decodeFromString<ActivityEstimate>(estimateJson))
            } else {
                json.decodeFromString<ActivityEstimateBatch>(estimateJson).activities
            }
            val defaultType = if (mode.isPlan) ActivityType.PLANNED else ActivityType.NEW
            val drafts = estimates
                .map { it.toDraft(defaultType) }
                .filter { it.title.isNotBlank() }
                .sortedBy { it.startTime }
            if (drafts.isEmpty()) {
                return@withContext Result.failure(Exception("Gemini returned no activities"))
            }
            Result.success(drafts)
        } catch (e: Exception) {
            Log.e(TAG, "estimateActivities failed", e)
            Result.failure(e)
        }
    }

    private fun ActivityEstimate.toDraft(defaultType: ActivityType): ActivityImportDraft {
        return ActivityImportDraft(
            title = title.asOptionalImportValue().orEmpty(),
            startTime = normalizeActivityHhMm(startTime, ActivityImportDefaults.START_TIME),
            endTime = normalizeActivityHhMm(endTime, ActivityImportDefaults.END_TIME),
            labelName = label.asOptionalImportValue(),
            type = parseActivityTypeOrDefault(type, defaultType),
            description = description.asOptionalImportValue().orEmpty(),
            source = ActivityImportSource.AI,
        )
    }

    private fun buildRequestBody(
        mode: CalendarAiMode,
        description: String,
        date: String,
        existingLabels: List<String>,
        dayContext: CalendarDayContext,
    ): String {
        val labelList = if (existingLabels.isEmpty()) {
            "(none yet — omit label)"
        } else {
            existingLabels.joinToString(", ")
        }
        val parts = buildJsonArray {
            add(
                buildJsonObject {
                    put(
                        "text",
                        buildString {
                            append(prompt("system"))
                            append("\n\n")
                            append(modeInstructions(mode))
                            append("\n\nCalendar date: ")
                            append(date)
                            append("\nAvailable labels (use one of these exact names): ")
                            append(labelList)
                            append(dayContext.promptSection(mode, "Activities"))
                            append("\n\nUser instruction:\n")
                            append(description.ifBlank { "(none — reproduce the preset as is)" })
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
                        if (mode == CalendarAiMode.SINGLE) {
                            activitySchema(existingLabels)
                        } else {
                            activityBatchResponseSchema(existingLabels)
                        }
                    )
                }
            )
        }
        return json.encodeToString(JsonObject.serializer(), root)
    }

    private fun modeInstructions(mode: CalendarAiMode): String = prompt("mode.${mode.name}")

    private fun activitySchema(labels: List<String>): JsonObject = buildJsonObject {
        put("type", "OBJECT")
        put(
            "properties",
            buildJsonObject {
                put("title", buildJsonObject { put("type", "STRING") })
                put("startTime", buildJsonObject { put("type", "STRING") })
                put("endTime", buildJsonObject { put("type", "STRING") })
                put(
                    "label",
                    buildJsonObject {
                        put("type", "STRING")
                        if (labels.isNotEmpty()) {
                            put("enum", buildJsonArray { labels.forEach { add(JsonPrimitive(it)) } })
                        }
                    }
                )
                put(
                    "type",
                    buildJsonObject {
                        put("type", "STRING")
                        put("enum", buildJsonArray { add(JsonPrimitive("PLANNED")); add(JsonPrimitive("NEW")) })
                    }
                )
                put("description", buildJsonObject { put("type", "STRING") })
            }
        )
        put(
            "required",
            buildJsonArray {
                add(JsonPrimitive("title"))
                add(JsonPrimitive("startTime"))
                add(JsonPrimitive("endTime"))
                add(JsonPrimitive("type"))
            }
        )
    }

    private fun activityBatchResponseSchema(labels: List<String>): JsonObject = buildJsonObject {
        put("type", "OBJECT")
        put(
            "properties",
            buildJsonObject {
                put(
                    "activities",
                    buildJsonObject {
                        put("type", "ARRAY")
                        put("items", activitySchema(labels))
                    }
                )
            }
        )
        put("required", buildJsonArray { add(JsonPrimitive("activities")) })
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

    companion object {
        private const val TAG = "GeminiActivity"
        const val DEFAULT_MODEL = "gemini-3.6-flash"

    }
}
