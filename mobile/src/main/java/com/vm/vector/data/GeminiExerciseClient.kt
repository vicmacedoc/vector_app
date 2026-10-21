package com.vm.vector.data

import android.content.Context
import android.util.Log
import com.vm.core.models.MuscleTargets
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
import kotlin.math.roundToInt

/**
 * Turns a free-text training instruction into reviewable [ExerciseSessionDraft]s via Gemini.
 * Numeric fields are decoded as doubles because models occasionally emit `8.0` for an integer.
 */
class GeminiExerciseClient(
    context: Context,
    private val model: String = DEFAULT_MODEL,
    private val keyStore: GeminiApiKeyStore = GeminiApiKeyStore(context),
) {
    private val appContext: Context = context.applicationContext

    private fun prompt(section: String): String = AiPrompts.get(appContext, "exercise", section)

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    @Serializable
    data class DropEstimate(
        val description: String? = null,
        val targetLoad: Double? = null,
        val unitLoad: String? = null,
        val targetReps: Double? = null,
        val targetRir: Double? = null,
    )

    @Serializable
    data class SetEstimate(
        val description: String? = null,
        val targetLoad: Double? = null,
        val unitLoad: String? = null,
        val targetReps: Double? = null,
        val targetRir: Double? = null,
        val rest: Double? = null,
        val unitRest: String? = null,
        val targetDuration: Double? = null,
        val unitDuration: String? = null,
        val drops: List<DropEstimate>? = null,
    )

    @Serializable
    data class IntervalEstimate(
        val description: String? = null,
        val targetDuration: Double? = null,
        val unitDuration: String? = null,
        val targetDistance: Double? = null,
        val unitDistance: String? = null,
        val targetZone: Double? = null,
        val targetRpe: Double? = null,
        val targetCadence: Double? = null,
        val rest: Double? = null,
        val unitRest: String? = null,
    )

    @Serializable
    data class MuscleWeightEstimate(
        val muscle: String? = null,
        val weight: Double? = null,
    )

    @Serializable
    data class ExerciseEstimate(
        val name: String? = null,
        val description: String? = null,
        val target: List<MuscleWeightEstimate>? = null,
        val supersetGroup: String? = null,
        val timing: String? = null,
        val totalDuration: Double? = null,
        val unitDuration: String? = null,
        val stroke: String? = null,
        val rest: Double? = null,
        val unitRest: String? = null,
        val sets: List<SetEstimate>? = null,
        val intervals: List<IntervalEstimate>? = null,
    )

    @Serializable
    data class SessionEstimate(
        val title: String? = null,
        val description: String? = null,
        val type: String? = null,
        val sport: String? = null,
        val exercises: List<ExerciseEstimate>? = null,
    )

    @Serializable
    data class SessionEstimateBatch(
        val sessions: List<SessionEstimate> = emptyList(),
    )

    suspend fun estimateSessions(
        mode: CalendarAiMode,
        description: String,
        date: String,
        context: ExerciseAiContext,
        dayContext: CalendarDayContext = CalendarDayContext(),
    ): Result<List<ExerciseSessionDraft>> = withContext(Dispatchers.IO) {
        val apiKey = keyStore.resolve().key
        if (apiKey.isBlank()) {
            return@withContext Result.failure(
                IllegalStateException(GeminiApiKeyStore.MISSING_KEY_MESSAGE)
            )
        }
        val trimmed = description.trim()
        if (trimmed.isBlank() && mode != CalendarAiMode.PLAN_PRESET) {
            return@withContext Result.failure(
                IllegalArgumentException("Describe the workout you want to add.")
            )
        }

        try {
            val body = buildRequestBody(mode, trimmed, date, context, dayContext)
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
                listOf(json.decodeFromString<SessionEstimate>(estimateJson))
            } else {
                json.decodeFromString<SessionEstimateBatch>(estimateJson).sessions
            }
            val drafts = estimates
                .map { it.toDraft(context.loadUnit) }
                .filter { it.exercises.isNotEmpty() }
            if (drafts.isEmpty()) {
                return@withContext Result.failure(Exception("Gemini returned no exercises"))
            }
            Result.success(drafts.normalizedForImport())
        } catch (e: Exception) {
            Log.e(TAG, "estimateSessions failed", e)
            Result.failure(e)
        }
    }

    private fun SessionEstimate.toDraft(defaultLoadUnit: String): ExerciseSessionDraft {
        val kind = parseExerciseKindOrDefault(type)
        return ExerciseSessionDraft(
            title = title.asOptionalImportValue() ?: defaultSessionTitle(kind),
            description = description.asOptionalImportValue().orEmpty(),
            kind = kind,
            sport = if (kind == ExerciseKind.ENDURANCE) normalizeSport(sport) else null,
            exercises = exercises.orEmpty().map { it.toDraft(kind, defaultLoadUnit) },
        )
    }

    private fun ExerciseEstimate.toDraft(
        kind: ExerciseKind,
        defaultLoadUnit: String,
    ): ExerciseImportDraft = ExerciseImportDraft(
        name = name.asOptionalImportValue().orEmpty(),
        description = description.asOptionalImportValue().orEmpty(),
        targets = normalizeMuscleTargets(
            target.orEmpty().mapNotNull { entry ->
                val muscle = entry.muscle ?: return@mapNotNull null
                muscle to (entry.weight ?: MuscleTargets.SECONDARY)
            }.toMap(LinkedHashMap())
        ),
        supersetGroup = normalizeSupersetGroup(supersetGroup),
        timing = normalizeTiming(timing),
        totalDuration = totalDuration.toDoubleInRange(0.0, MAX_DURATION.toDouble()),
        unitDuration = normalizeShortTimeUnit(unitDuration, ExerciseImportDefaults.EXERCISE_DURATION_UNIT),
        stroke = normalizeStroke(stroke),
        sets = when (kind) {
            ExerciseKind.RESISTANCE -> sets.orEmpty().map { it.toDraft(defaultLoadUnit) }
            ExerciseKind.ENDURANCE, ExerciseKind.OTHER -> emptyList()
        },
        intervals = when (kind) {
            ExerciseKind.ENDURANCE -> intervals.orEmpty().map { it.toDraft() }
            ExerciseKind.RESISTANCE, ExerciseKind.OTHER -> emptyList()
        },
        rest = if (kind == ExerciseKind.OTHER) rest.toIntInRange(0, MAX_REST) else null,
        restUnit = normalizeShortTimeUnit(unitRest, ExerciseImportDefaults.REST_UNIT),
    )

    private fun SetEstimate.toDraft(defaultLoadUnit: String): ExerciseSetDraft = ExerciseSetDraft(
        description = description.asOptionalImportValue().orEmpty(),
        targetLoad = targetLoad.toDoubleInRange(0.0, MAX_LOAD),
        unitLoad = normalizeLoadUnit(unitLoad, defaultLoadUnit),
        targetReps = targetReps.toIntInRange(1, MAX_REPS),
        targetRir = targetRir.toIntInRange(0, MAX_RIR),
        rest = rest.toIntInRange(0, MAX_REST),
        restUnit = normalizeShortTimeUnit(unitRest, ExerciseImportDefaults.REST_UNIT),
        targetDuration = targetDuration.toIntInRange(0, MAX_DURATION),
        unitDuration = normalizeShortTimeUnit(unitDuration, ExerciseImportDefaults.SET_DURATION_UNIT),
        drops = drops.orEmpty().map { drop ->
            ExerciseDropDraft(
                description = drop.description.asOptionalImportValue().orEmpty(),
                targetLoad = drop.targetLoad.toDoubleInRange(0.0, MAX_LOAD),
                unitLoad = drop.unitLoad.asOptionalImportValue()?.let { normalizeLoadUnit(it, defaultLoadUnit) },
                targetReps = drop.targetReps.toIntInRange(1, MAX_REPS),
                targetRir = drop.targetRir.toIntInRange(0, MAX_RIR),
            )
        },
    )

    private fun IntervalEstimate.toDraft(): ExerciseIntervalDraft = ExerciseIntervalDraft(
        description = description.asOptionalImportValue().orEmpty(),
        targetDuration = targetDuration.toIntInRange(1, MAX_DURATION),
        unitDuration = normalizeShortTimeUnit(unitDuration, ExerciseImportDefaults.INTERVAL_DURATION_UNIT),
        targetDistance = targetDistance.toDoubleInRange(0.0, MAX_DISTANCE),
        unitDistance = normalizeDistanceUnit(unitDistance),
        targetZone = targetZone.toIntInRange(1, ExerciseImportDefaults.MAX_ZONE),
        targetRpe = normalizeRpe(targetRpe),
        targetCadence = targetCadence.toIntInRange(1, MAX_CADENCE),
        rest = rest.toIntInRange(0, MAX_REST),
        restUnit = normalizeShortTimeUnit(unitRest, ExerciseImportDefaults.REST_UNIT),
    )

    private fun Double?.toIntInRange(min: Int, max: Int): Int? =
        this?.roundToInt()?.takeIf { it in min..max }

    private fun Double?.toDoubleInRange(min: Double, max: Double): Double? =
        this?.takeIf { it.isFinite() && it in min..max && it > 0.0 }

    private fun buildRequestBody(
        mode: CalendarAiMode,
        description: String,
        date: String,
        context: ExerciseAiContext,
        dayContext: CalendarDayContext,
    ): String {
        val muscles = context.muscleVocabulary.takeIf { it.isNotEmpty() }
            ?: ExerciseImportDefaults.MUSCLES
        val history = if (context.recentExercises.isEmpty()) {
            "(no logged history yet — use standard prescriptions)"
        } else {
            context.recentExercises.joinToString("\n") { "- $it" }
        }
        val existing = if (context.existingSessionTitles.isEmpty()) {
            "(none)"
        } else {
            context.existingSessionTitles.joinToString(", ")
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
                            append("\n\nCalendar date for this workout: ")
                            append(date)
                            append("\nAllowed muscle tags (use these exact strings): ")
                            append(muscles.joinToString(", "))
                            append("\nPreferred load unit (use it for every unitLoad): ")
                            append(context.loadUnit)
                            if (!mode.isPlan) {
                                append("\nSessions already on this day (do not repeat them): ")
                                append(existing)
                            }
                            append(dayContext.promptSection(mode, "Sessions"))
                            append("\n\nThe user's recent exercises (name, tags, last prescription):\n")
                            append(history)
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
                        if (mode == CalendarAiMode.SINGLE) sessionSchema() else sessionBatchResponseSchema()
                    )
                }
            )
        }
        return json.encodeToString(JsonObject.serializer(), root)
    }

    private fun modeInstructions(mode: CalendarAiMode): String = prompt("mode.${mode.name}")

    private fun stringSchema(): JsonObject = buildJsonObject { put("type", "STRING") }

    private fun numberSchema(): JsonObject = buildJsonObject { put("type", "NUMBER") }

    private fun integerSchema(): JsonObject = buildJsonObject { put("type", "INTEGER") }

    private fun enumSchema(values: List<String>): JsonObject = buildJsonObject {
        put("type", "STRING")
        put("enum", buildJsonArray { values.forEach { add(JsonPrimitive(it)) } })
    }

    private fun arraySchema(items: JsonObject): JsonObject = buildJsonObject {
        put("type", "ARRAY")
        put("items", items)
    }

    private fun dropSchema(): JsonObject = buildJsonObject {
        put("type", "OBJECT")
        put(
            "properties",
            buildJsonObject {
                put("description", stringSchema())
                put("targetLoad", numberSchema())
                put("unitLoad", stringSchema())
                put("targetReps", integerSchema())
                put("targetRir", integerSchema())
            }
        )
    }

    private fun setSchema(): JsonObject = buildJsonObject {
        put("type", "OBJECT")
        put(
            "properties",
            buildJsonObject {
                put("description", stringSchema())
                put("targetLoad", numberSchema())
                put("unitLoad", stringSchema())
                put("targetReps", integerSchema())
                put("targetRir", integerSchema())
                put("rest", integerSchema())
                put("unitRest", stringSchema())
                put("targetDuration", integerSchema())
                put("unitDuration", stringSchema())
                put("drops", arraySchema(dropSchema()))
            }
        )
    }

    private fun intervalSchema(): JsonObject = buildJsonObject {
        put("type", "OBJECT")
        put(
            "properties",
            buildJsonObject {
                put("description", stringSchema())
                put("targetDuration", integerSchema())
                put("unitDuration", enumSchema(listOf("s", "min")))
                put("targetDistance", numberSchema())
                put("unitDistance", enumSchema(ExerciseImportDefaults.DISTANCE_UNITS))
                put("targetZone", integerSchema())
                put("targetRpe", numberSchema())
                put("targetCadence", integerSchema())
                put("rest", integerSchema())
                put("unitRest", enumSchema(listOf("s", "min")))
            }
        )
    }

    private fun muscleWeightSchema(): JsonObject = buildJsonObject {
        put("type", "OBJECT")
        put(
            "properties",
            buildJsonObject {
                put("muscle", stringSchema())
                put("weight", numberSchema())
            }
        )
        put(
            "required",
            buildJsonArray {
                add(JsonPrimitive("muscle"))
                add(JsonPrimitive("weight"))
            }
        )
    }

    private fun exerciseSchema(): JsonObject = buildJsonObject {
        put("type", "OBJECT")
        put(
            "properties",
            buildJsonObject {
                put("name", stringSchema())
                put("description", stringSchema())
                put("target", arraySchema(muscleWeightSchema()))
                put("supersetGroup", stringSchema())
                put("timing", stringSchema())
                put("totalDuration", numberSchema())
                put("unitDuration", enumSchema(listOf("min", "s")))
                put("stroke", enumSchema(ExerciseImportDefaults.STROKES))
                put("rest", integerSchema())
                put("unitRest", stringSchema())
                put("sets", arraySchema(setSchema()))
                put("intervals", arraySchema(intervalSchema()))
            }
        )
        put("required", buildJsonArray { add(JsonPrimitive("name")) })
    }

    private fun sessionSchema(): JsonObject = buildJsonObject {
        put("type", "OBJECT")
        put(
            "properties",
            buildJsonObject {
                put("title", stringSchema())
                put("description", stringSchema())
                put("type", enumSchema(ExerciseKind.entries.map { it.name }))
                put("sport", enumSchema(ExerciseImportDefaults.SPORTS))
                put("exercises", arraySchema(exerciseSchema()))
            }
        )
        put(
            "required",
            buildJsonArray {
                add(JsonPrimitive("title"))
                add(JsonPrimitive("type"))
                add(JsonPrimitive("exercises"))
            }
        )
    }

    private fun sessionBatchResponseSchema(): JsonObject = buildJsonObject {
        put("type", "OBJECT")
        put(
            "properties",
            buildJsonObject {
                put("sessions", arraySchema(sessionSchema()))
            }
        )
        put("required", buildJsonArray { add(JsonPrimitive("sessions")) })
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
        private const val TAG = "GeminiExercise"
        const val DEFAULT_MODEL = "gemini-3.6-flash"

        private const val MAX_REPS = 999
        private const val MAX_RIR = 10
        private const val MAX_REST = 3600
        private const val MAX_DURATION = 99_999
        private const val MAX_LOAD = 2000.0
        private const val MAX_DISTANCE = 99_999.0
        private const val MAX_CADENCE = 250

    }
}
