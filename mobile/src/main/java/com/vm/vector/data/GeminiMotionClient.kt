package com.vm.vector.data

import android.content.Context
import android.util.Log
import com.vm.core.models.MotionPriority
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
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/** On-demand Gemini: turns free text into Motion to-do drafts. Points are computed locally. */
class GeminiMotionClient(
    context: Context,
    private val model: String = GeminiBrainClient.DEFAULT_MODEL,
    private val keyStore: GeminiApiKeyStore = GeminiApiKeyStore(context),
) {
    private val appContext: Context = context.applicationContext

    private fun prompt(section: String): String = AiPrompts.get(appContext, "motion", section)

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        explicitNulls = false
    }

    @Serializable
    private data class ParsedItem(
        val title: String = "",
        val description: String? = null,
        val deadline: String? = null,
        val priority: String? = null,
        val target: Double? = null,
        val unit: String? = null,
    )

    @Serializable
    private data class ParsedResult(val items: List<ParsedItem> = emptyList())

    fun isConfigured(): Boolean = keyStore.resolve().isConfigured

    suspend fun parseTodos(
        text: String,
        listName: String,
        today: LocalDate = LocalDate.now(),
    ): Result<List<MotionItemDraft>> = withContext(Dispatchers.IO) {
        val trimmed = text.trim()
        if (trimmed.isBlank()) return@withContext Result.failure(IllegalArgumentException("Nothing to read"))
        if (trimmed.length > MAX_CHARS) {
            return@withContext Result.failure(IllegalArgumentException("Text is too long (max ~20k characters)."))
        }
        val prompt = prompt("todos")
            .replace("{today}", "$today (${today.dayOfWeek.name.lowercase()})")
            .replace("{list}", listName)
        post(buildBody(prompt, trimmed)).mapCatching { raw ->
            json.decodeFromString<ParsedResult>(raw).items
                .filter { it.title.isNotBlank() }
                .map { it.toDraft() }
        }
    }

    private fun ParsedItem.toDraft(): MotionItemDraft = MotionItemDraft(
        title = title.trim().take(200),
        notes = description?.trim().orEmpty(),
        priority = MotionPriority.parse(priority),
        dueAt = deadline?.let(::parseDeadline),
        target = target?.takeIf { it > 0.0 },
        unit = unit?.trim()?.takeIf { it.isNotEmpty() },
    )

    /** `yyyy-MM-dd` means end of that day (18:00, like the item dialog); `yyyy-MM-ddTHH:mm` is exact. */
    private fun parseDeadline(value: String): Long? {
        val zone = ZoneId.systemDefault()
        val trimmed = value.trim()
        runCatching { LocalDateTime.parse(trimmed) }.getOrNull()?.let {
            return it.atZone(zone).toInstant().toEpochMilli()
        }
        return runCatching { LocalDate.parse(trimmed.take(10)) }.getOrNull()
            ?.atTime(DEFAULT_DUE_HOUR, 0)
            ?.atZone(zone)
            ?.toInstant()
            ?.toEpochMilli()
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

    private fun buildBody(prompt: String, text: String): String {
        val stringType = buildJsonObject { put("type", "STRING") }
        val item = buildJsonObject {
            put("type", "OBJECT")
            put(
                "properties",
                buildJsonObject {
                    put("title", stringType)
                    put("description", stringType)
                    put("deadline", stringType)
                    put(
                        "priority",
                        buildJsonObject {
                            put("type", "STRING")
                            put(
                                "enum",
                                buildJsonArray { MotionPriority.entries.forEach { add(JsonPrimitive(it.name)) } },
                            )
                        },
                    )
                    put("target", buildJsonObject { put("type", "NUMBER") })
                    put("unit", stringType)
                },
            )
            put("required", buildJsonArray { add(JsonPrimitive("title")) })
        }
        val root = buildJsonObject {
            put(
                "contents",
                buildJsonArray {
                    add(
                        buildJsonObject {
                            put("role", "user")
                            put(
                                "parts",
                                buildJsonArray {
                                    add(buildJsonObject { put("text", prompt) })
                                    add(buildJsonObject { put("text", text) })
                                },
                            )
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
                                    put(
                                        "items",
                                        buildJsonObject {
                                            put("type", "ARRAY")
                                            put("items", item)
                                        },
                                    )
                                },
                            )
                            put("required", buildJsonArray { add(JsonPrimitive("items")) })
                        }
                    )
                }
            )
        }
        return json.encodeToString(JsonObject.serializer(), root)
    }

    private fun extractModelJson(responseText: String): String? {
        val root = runCatching { json.parseToJsonElement(responseText).jsonObject }.getOrNull() ?: return null
        val first = root["candidates"]?.jsonArray?.firstOrNull()?.jsonObject ?: return null
        val parts = first["content"]?.jsonObject?.get("parts")?.jsonArray ?: return null
        val text = parts.firstOrNull()?.jsonObject?.get("text")?.jsonPrimitive?.contentOrNull ?: return null
        return text.trim().removeSurrounding("```json").removeSurrounding("```").trim()
    }

    private fun parseApiErrorMessage(responseText: String): String? {
        val root = runCatching { json.parseToJsonElement(responseText).jsonObject }.getOrNull() ?: return null
        return root["error"]?.jsonObject?.get("message")?.jsonPrimitive?.contentOrNull
    }

    companion object {
        private const val TAG = "GeminiMotion"
        private const val MAX_CHARS = 20_000
        const val DEFAULT_DUE_HOUR = 18

    }
}
