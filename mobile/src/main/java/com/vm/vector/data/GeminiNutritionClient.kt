package com.vm.vector.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
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
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * Estimates Food calendar rows from optional text and/or a food photo via Gemini, shaped by
 * [CalendarAiMode]: one meal, several rows, or a whole day (from scratch or from the preset).
 * Rows match the Add Meal form; [MealEstimate.time] is optional (blank → caller uses the phone clock).
 */
class GeminiNutritionClient(
    context: Context,
    private val model: String = DEFAULT_MODEL,
    private val keyStore: GeminiApiKeyStore = GeminiApiKeyStore(context),
) {
    private val appContext: Context = context.applicationContext

    private fun prompt(section: String): String = AiPrompts.get(appContext, "nutrition", section)

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    @Serializable
    data class MealEstimate(
        val name: String = "",
        /** HH:mm when the user implies it; null otherwise. */
        val time: String? = null,
        val amount: Double = 0.0,
        val unit: String = "g",
        val kcal: Double = 0.0,
        val protein: Double = 0.0,
        val carbs: Double = 0.0,
        val fats: Double = 0.0,
    )

    @Serializable
    data class MealEstimateBatch(
        val meals: List<MealEstimate> = emptyList(),
    )

    suspend fun estimateMeals(
        mode: CalendarAiMode,
        description: String?,
        imageBytes: ByteArray?,
        date: String,
        dayContext: CalendarDayContext = CalendarDayContext(),
        mimeType: String = "image/jpeg",
    ): Result<List<MealEstimate>> = withContext(Dispatchers.IO) {
        val apiKey = keyStore.resolve().key
        if (apiKey.isBlank()) {
            return@withContext Result.failure(
                IllegalStateException(GeminiApiKeyStore.MISSING_KEY_MESSAGE)
            )
        }
        val trimmed = description?.trim().orEmpty()
        if (trimmed.isBlank() && imageBytes == null && mode != CalendarAiMode.PLAN_PRESET) {
            return@withContext Result.failure(
                IllegalArgumentException("Provide a description and/or a photo.")
            )
        }

        try {
            val preparedImage = imageBytes?.let { prepareImageForUpload(it) }
            val body = buildRequestBody(mode, trimmed, preparedImage, mimeType, date, dayContext)
            val url = URL(
                "https://generativelanguage.googleapis.com/v1beta/models/" +
                    "$model:generateContent?key=$apiKey"
            )
            val connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 30_000
                readTimeout = 60_000
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
                listOf(json.decodeFromString<MealEstimate>(estimateJson))
            } else {
                json.decodeFromString<MealEstimateBatch>(estimateJson).meals
            }
            val validated = estimates.mapNotNull(::validateEstimate)
                .sortedBy { it.time.orEmpty() }
            if (validated.isEmpty()) {
                return@withContext Result.failure(
                    Exception("Gemini JSON missing required meal fields (name/amount/kcal)")
                )
            }
            Result.success(validated)
        } catch (e: Exception) {
            Log.e(TAG, "estimateMeals failed", e)
            Result.failure(e)
        }
    }

    private fun validateEstimate(estimate: MealEstimate): MealEstimate? {
        if (estimate.name.isBlank()) return null
        if (estimate.amount <= 0) return null
        if (estimate.kcal < 0) return null
        return estimate.copy(
            time = normalizeMealTimeOrBlank(estimate.time).ifBlank { null },
            unit = estimate.unit.ifBlank { "g" },
            protein = estimate.protein.coerceAtLeast(0.0),
            carbs = estimate.carbs.coerceAtLeast(0.0),
            fats = estimate.fats.coerceAtLeast(0.0),
        )
    }

    private fun buildRequestBody(
        mode: CalendarAiMode,
        description: String,
        image: PreparedImage?,
        fallbackMime: String,
        date: String,
        dayContext: CalendarDayContext,
    ): String {
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
                            append(dayContext.promptSection(mode, "Food rows"))
                            when {
                                description.isNotBlank() -> {
                                    append("\n\nUser instruction:\n")
                                    append(description)
                                }
                                image != null -> append("\n\nThe user provided a photo only; estimate from the image.")
                                else -> append("\n\nUser instruction: (none — reproduce the preset as is)")
                            }
                        }
                    )
                }
            )
            if (image != null) {
                add(
                    buildJsonObject {
                        put(
                            "inlineData",
                            buildJsonObject {
                                put("mimeType", image.mimeType.ifBlank { fallbackMime })
                                put("data", image.base64)
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
                    put("temperature", 0.2)
                    put("responseMimeType", "application/json")
                    put(
                        "responseSchema",
                        if (mode == CalendarAiMode.SINGLE) mealResponseSchema() else mealBatchResponseSchema()
                    )
                }
            )
        }
        return json.encodeToString(JsonObject.serializer(), root)
    }

    private fun modeInstructions(mode: CalendarAiMode): String = prompt("mode.${mode.name}")

    private fun mealBatchResponseSchema(): JsonObject = buildJsonObject {
        put("type", "OBJECT")
        put(
            "properties",
            buildJsonObject {
                put(
                    "meals",
                    buildJsonObject {
                        put("type", "ARRAY")
                        put("items", mealResponseSchema())
                    }
                )
            }
        )
        put("required", buildJsonArray { add(JsonPrimitive("meals")) })
    }

    private fun mealResponseSchema(): JsonObject = buildJsonObject {
        put("type", "OBJECT")
        put(
            "properties",
            buildJsonObject {
                put("name", buildJsonObject { put("type", "STRING") })
                put("time", buildJsonObject { put("type", "STRING") })
                put("amount", buildJsonObject { put("type", "NUMBER") })
                put("unit", buildJsonObject { put("type", "STRING") })
                put("kcal", buildJsonObject { put("type", "NUMBER") })
                put("protein", buildJsonObject { put("type", "NUMBER") })
                put("carbs", buildJsonObject { put("type", "NUMBER") })
                put("fats", buildJsonObject { put("type", "NUMBER") })
            }
        )
        put(
            "required",
            buildJsonArray {
                add(JsonPrimitive("name"))
                add(JsonPrimitive("amount"))
                add(JsonPrimitive("unit"))
                add(JsonPrimitive("kcal"))
                add(JsonPrimitive("protein"))
                add(JsonPrimitive("carbs"))
                add(JsonPrimitive("fats"))
            }
        )
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

    private data class PreparedImage(val base64: String, val mimeType: String)

    private fun prepareImageForUpload(bytes: ByteArray): PreparedImage {
        val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            ?: return PreparedImage(
                base64 = Base64.encodeToString(bytes, Base64.NO_WRAP),
                mimeType = "image/jpeg"
            )
        val maxSide = 1280
        val scale = minOf(1f, maxSide.toFloat() / maxOf(decoded.width, decoded.height))
        val scaled = if (scale < 1f) {
            Bitmap.createScaledBitmap(
                decoded,
                (decoded.width * scale).toInt().coerceAtLeast(1),
                (decoded.height * scale).toInt().coerceAtLeast(1),
                true
            )
        } else {
            decoded
        }
        val out = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.JPEG, 85, out)
        if (scaled !== decoded) scaled.recycle()
        decoded.recycle()
        return PreparedImage(
            base64 = Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP),
            mimeType = "image/jpeg"
        )
    }

    companion object {
        private const val TAG = "GeminiNutrition"
        const val DEFAULT_MODEL = "gemini-3.6-flash"

    }
}
