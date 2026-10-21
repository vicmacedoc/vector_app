package com.vm.vector.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
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
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * Parses sleep times and stages from optional text and/or a screenshot
 * (Nest Hub, Health app, etc.). Every field is nullable so only reported values apply.
 */
class GeminiSleepClient(
    context: Context,
    private val model: String = DEFAULT_MODEL,
    private val keyStore: GeminiApiKeyStore = GeminiApiKeyStore(context),
) {
    private val appContext: Context = context.applicationContext

    private fun prompt(section: String): String = AiPrompts.get(appContext, "sleep", section)

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        explicitNulls = false
    }

    @Serializable
    data class SleepEstimate(
        val plannedBedtime: String? = null,
        val plannedWakeUp: String? = null,
        val actualBedtime: String? = null,
        val actualWakeUp: String? = null,
        val fellAsleepAt: String? = null,
        val latencyMinutes: Int? = null,
        val deepMinutes: Int? = null,
        val lightMinutes: Int? = null,
        val remMinutes: Int? = null,
        val awakeMinutes: Int? = null,
        val deepPercent: Int? = null,
        val lightPercent: Int? = null,
        val remPercent: Int? = null,
        val awakePercent: Int? = null,
    )

    suspend fun estimateSleep(
        description: String?,
        imageBytes: ByteArray?,
        mimeType: String = "image/jpeg",
    ): Result<SleepEstimate> = withContext(Dispatchers.IO) {
        val apiKey = keyStore.resolve().key
        if (apiKey.isBlank()) {
            return@withContext Result.failure(
                IllegalStateException(GeminiApiKeyStore.MISSING_KEY_MESSAGE)
            )
        }
        val trimmed = description?.trim().orEmpty()
        if (trimmed.isBlank() && imageBytes == null) {
            return@withContext Result.failure(
                IllegalArgumentException("Provide a description and/or a photo.")
            )
        }

        try {
            val preparedImage = imageBytes?.let { prepareImageForUpload(it) }
            val body = buildRequestBody(trimmed, preparedImage, mimeType)
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

            val estimate = json.decodeFromString<SleepEstimate>(estimateJson)
            Result.success(normalize(estimate))
        } catch (e: Exception) {
            Log.e(TAG, "estimateSleep failed", e)
            Result.failure(e)
        }
    }

    private fun normalize(estimate: SleepEstimate): SleepEstimate = estimate.copy(
        plannedBedtime = normalizeHhMm(estimate.plannedBedtime),
        plannedWakeUp = normalizeHhMm(estimate.plannedWakeUp),
        actualBedtime = normalizeHhMm(estimate.actualBedtime),
        actualWakeUp = normalizeHhMm(estimate.actualWakeUp),
        fellAsleepAt = normalizeHhMm(estimate.fellAsleepAt),
        latencyMinutes = estimate.latencyMinutes?.takeIf { it >= 0 },
        deepMinutes = estimate.deepMinutes?.takeIf { it >= 0 },
        lightMinutes = estimate.lightMinutes?.takeIf { it >= 0 },
        remMinutes = estimate.remMinutes?.takeIf { it >= 0 },
        awakeMinutes = estimate.awakeMinutes?.takeIf { it >= 0 },
        deepPercent = estimate.deepPercent?.takeIf { it in 0..100 },
        lightPercent = estimate.lightPercent?.takeIf { it in 0..100 },
        remPercent = estimate.remPercent?.takeIf { it in 0..100 },
        awakePercent = estimate.awakePercent?.takeIf { it in 0..100 },
    )

    private fun normalizeHhMm(raw: String?): String? {
        val minutes = com.vm.core.models.SleepMath.parseHhMm(raw) ?: return null
        return com.vm.core.models.SleepMath.formatHhMm(minutes)
    }

    private fun buildRequestBody(
        description: String,
        image: PreparedImage?,
        fallbackMime: String,
    ): String {
        val parts = buildJsonArray {
            add(
                buildJsonObject {
                    put(
                        "text",
                        buildString {
                            append(prompt("system"))
                            if (description.isNotBlank()) {
                                append("\n\nUser description:\n")
                                append(description)
                            } else {
                                append("\n\nUser provided a screenshot only; extract sleep data from the image.")
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
                    put("temperature", 0.1)
                    put("responseMimeType", "application/json")
                    put("responseSchema", sleepResponseSchema())
                }
            )
        }
        return json.encodeToString(JsonObject.serializer(), root)
    }

    private fun sleepResponseSchema(): JsonObject {
        fun hhMm() = buildJsonObject { put("type", "STRING") }
        fun integer() = buildJsonObject { put("type", "INTEGER") }
        return buildJsonObject {
            put("type", "OBJECT")
            put(
                "properties",
                buildJsonObject {
                    put("plannedBedtime", hhMm())
                    put("plannedWakeUp", hhMm())
                    put("actualBedtime", hhMm())
                    put("actualWakeUp", hhMm())
                    put("fellAsleepAt", hhMm())
                    put("latencyMinutes", integer())
                    put("deepMinutes", integer())
                    put("lightMinutes", integer())
                    put("remMinutes", integer())
                    put("awakeMinutes", integer())
                    put("deepPercent", integer())
                    put("lightPercent", integer())
                    put("remPercent", integer())
                    put("awakePercent", integer())
                }
            )
        }
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
        private const val TAG = "GeminiSleep"
        const val DEFAULT_MODEL = "gemini-3.6-flash"

    }
}
