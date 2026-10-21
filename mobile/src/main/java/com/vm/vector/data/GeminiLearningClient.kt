package com.vm.vector.data

import android.content.Context
import android.util.Base64
import android.util.Log
import com.vm.core.models.BrainNode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
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
import kotlin.math.ceil

/** Gemini helpers for Learn tiles. Only runs when the user asks. */
class GeminiLearningClient(
    context: Context,
    private val model: String = GeminiBrainClient.DEFAULT_MODEL,
    private val keyStore: GeminiApiKeyStore = GeminiApiKeyStore(context),
) {
    private val appContext: Context = context.applicationContext

    private fun prompt(section: String): String = AiPrompts.get(appContext, "learning", section)

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        explicitNulls = false
    }

    @Serializable
    data class SuggestedLink(
        val tileId: String = "",
        /** [DEPENDS_ON]: the current tile depends on [tileId]; [USED_IN]: [tileId] depends on the current tile. */
        val direction: String = DEPENDS_ON,
        val reason: String = "",
    )

    @Serializable
    data class MissingPrerequisite(
        val name: String = "",
        val reason: String = "",
    )

    @Serializable
    data class LinkSuggestions(
        val links: List<SuggestedLink> = emptyList(),
        val missing: List<MissingPrerequisite> = emptyList(),
    )

    @Serializable
    data class NoteDraft(
        val title: String = "",
        val markdown: String = "",
    )

    @Serializable
    data class QuizQuestion(
        val type: String = MULTIPLE_CHOICE,
        val prompt: String = "",
        val options: List<String> = emptyList(),
        val correctIndex: Int = 0,
        val explanation: String = "",
        val referenceAnswer: String = "",
    ) {
        val isMultipleChoice: Boolean get() = type == MULTIPLE_CHOICE && options.size >= 2
    }

    @Serializable
    data class Quiz(val questions: List<QuizQuestion> = emptyList())

    @Serializable
    data class OpenGrade(
        val score: Float = 0f,
        val verdict: String = "",
        val feedback: String = "",
    )

    fun isConfigured(): Boolean = keyStore.resolve().isConfigured

    suspend fun suggestLinks(
        learning: LearningContext,
        candidates: List<BrainNode>,
        existingLinkIds: Set<String>,
    ): Result<LinkSuggestions> = withContext(Dispatchers.IO) {
        val candidateText = candidates.joinToString("\n") { node ->
            buildString {
                append(node.id).append(" | ").append(node.name)
                if (node.tags.isNotEmpty()) append(" | tags: ").append(node.tags.joinToString(", "))
            }
        }.ifBlank { "(none)" }
        val prompt = """${prompt("links")}

CURRENT TILE
${learning.text.take(MAX_CONTEXT_CHARS)}

OTHER LEARN TILES (id | name | tags)
$candidateText"""
        val schema = obj(
            "links" to arr(
                obj(
                    "tileId" to str(),
                    "direction" to enumStr(DEPENDS_ON, USED_IN),
                    "reason" to str(),
                ),
            ),
            "missing" to arr(obj("name" to str(), "reason" to str())),
        )
        request(listOf(prompt), schema, temperature = 0.3).mapCatching { raw ->
            val result = json.decodeFromString<LinkSuggestions>(raw)
            val validIds = candidates.map { it.id }.toSet()
            val existingNames = candidates.map { it.name.trim().lowercase() }.toSet() + learning.title.trim().lowercase()
            LinkSuggestions(
                links = result.links
                    .filter { it.tileId in validIds && it.tileId !in existingLinkIds }
                    .map { it.copy(direction = if (it.direction == USED_IN) USED_IN else DEPENDS_ON) }
                    .distinctBy { it.tileId },
                missing = result.missing
                    .map { it.copy(name = it.name.trim().take(80)) }
                    .filter { it.name.isNotBlank() && it.name.lowercase() !in existingNames }
                    .distinctBy { it.name.lowercase() }
                    .take(MAX_MISSING),
            )
        }
    }

    suspend fun draftNotes(learning: LearningContext): Result<NoteDraft> = withContext(Dispatchers.IO) {
        val prompt = """${prompt("draft")}

TILE
${learning.text.take(MAX_CONTEXT_CHARS)}"""
        val schema = obj("title" to str(), "markdown" to str())
        request(listOf(prompt), schema, temperature = 0.4, pdfs = learning.pdfs).mapCatching { raw ->
            val draft = json.decodeFromString<NoteDraft>(raw)
            if (draft.markdown.isBlank()) error("Gemini returned an empty draft")
            draft.copy(title = draft.title.trim().take(80).ifBlank { learning.title }, markdown = draft.markdown.trim())
        }
    }

    suspend fun generateQuiz(learning: LearningContext, count: Int): Result<List<QuizQuestion>> =
        withContext(Dispatchers.IO) {
            val total = count.coerceIn(1, 20)
            val multipleChoice = ceil(total * 0.6).toInt().coerceAtMost(total)
            val open = total - multipleChoice
            val prompt = """${prompt("quiz")}
Write exactly $total questions: $multipleChoice of type MULTIPLE_CHOICE and $open of type OPEN.

TILE
${learning.text.take(MAX_CONTEXT_CHARS)}"""
            val schema = obj(
                "questions" to arr(
                    obj(
                        "type" to enumStr(MULTIPLE_CHOICE, OPEN),
                        "prompt" to str(),
                        "options" to arr(str()),
                        "correctIndex" to int(),
                        "explanation" to str(),
                        "referenceAnswer" to str(),
                        required = listOf("type", "prompt", "explanation"),
                    ),
                ),
            )
            request(listOf(prompt), schema, temperature = 0.5, pdfs = learning.pdfs).mapCatching { raw ->
                val questions = json.decodeFromString<Quiz>(raw).questions
                    .filter { it.prompt.isNotBlank() }
                    .map { q ->
                        if (q.type == MULTIPLE_CHOICE && q.options.size >= 2) {
                            q.copy(correctIndex = q.correctIndex.coerceIn(0, q.options.lastIndex))
                        } else {
                            q.copy(type = OPEN, options = emptyList())
                        }
                    }
                if (questions.isEmpty()) error("Gemini returned no questions")
                questions
            }
        }

    suspend fun gradeOpenAnswer(
        question: String,
        referenceAnswer: String,
        userAnswer: String,
        learning: LearningContext,
    ): Result<OpenGrade> = withContext(Dispatchers.IO) {
        val prompt = """${prompt("grade")}

QUESTION
$question

REFERENCE ANSWER
$referenceAnswer

STUDENT ANSWER
$userAnswer

TILE NOTES (for context)
${learning.text.take(MAX_GRADE_CONTEXT_CHARS)}"""
        val schema = obj("score" to num(), "verdict" to enumStr("correct", "partial", "incorrect"), "feedback" to str())
        request(listOf(prompt), schema, temperature = 0.1).mapCatching { raw ->
            val grade = json.decodeFromString<OpenGrade>(raw)
            grade.copy(score = grade.score.coerceIn(0f, 1f), feedback = grade.feedback.trim())
        }
    }

    private fun request(
        texts: List<String>,
        schema: JsonObject,
        temperature: Double,
        pdfs: List<java.io.File> = emptyList(),
    ): Result<String> {
        val apiKey = keyStore.resolve().key
        if (apiKey.isBlank()) {
            return Result.failure(IllegalStateException(GeminiApiKeyStore.MISSING_KEY_MESSAGE))
        }
        val parts = buildJsonArray {
            texts.forEach { text -> add(buildJsonObject { put("text", text) }) }
            var budget = MAX_ATTACHMENT_BYTES
            for (pdf in pdfs) {
                val size = pdf.length()
                if (size <= 0 || size > budget) continue
                budget -= size
                add(
                    buildJsonObject {
                        put(
                            "inlineData",
                            buildJsonObject {
                                put("mimeType", "application/pdf")
                                put("data", Base64.encodeToString(pdf.readBytes(), Base64.NO_WRAP))
                            },
                        )
                    },
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
                        },
                    )
                },
            )
            put(
                "generationConfig",
                buildJsonObject {
                    put("temperature", temperature)
                    put("responseMimeType", "application/json")
                    put("responseSchema", schema)
                },
            )
        }
        return post(apiKey, json.encodeToString(JsonObject.serializer(), root))
    }

    private fun post(apiKey: String, body: String): Result<String> = try {
        val url = URL("https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$apiKey")
        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 30_000
            readTimeout = 180_000
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
            Result.failure(Exception(parseApiErrorMessage(responseText) ?: "Gemini request failed (HTTP $code)"))
        } else {
            extractModelJson(responseText)?.let { Result.success(it) }
                ?: Result.failure(Exception("Gemini returned no JSON content"))
        }
    } catch (e: Exception) {
        Log.e(TAG, "Gemini request failed", e)
        Result.failure(e)
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

    private fun str(): JsonObject = buildJsonObject { put("type", "STRING") }
    private fun num(): JsonObject = buildJsonObject { put("type", "NUMBER") }
    private fun int(): JsonObject = buildJsonObject { put("type", "INTEGER") }

    private fun enumStr(vararg values: String): JsonObject = buildJsonObject {
        put("type", "STRING")
        put("enum", JsonArray(values.map { JsonPrimitive(it) }))
    }

    private fun arr(items: JsonObject): JsonObject = buildJsonObject {
        put("type", "ARRAY")
        put("items", items)
    }

    private fun obj(vararg properties: Pair<String, JsonObject>, required: List<String>? = null): JsonObject =
        buildJsonObject {
            put("type", "OBJECT")
            put("properties", JsonObject(properties.toMap()))
            put("required", JsonArray((required ?: properties.map { it.first }).map { JsonPrimitive(it) }))
        }

    companion object {
        private const val TAG = "GeminiLearning"
        const val DEPENDS_ON = "DEPENDS_ON"
        const val USED_IN = "USED_IN"
        const val MULTIPLE_CHOICE = "MULTIPLE_CHOICE"
        const val OPEN = "OPEN"
        private const val MAX_CONTEXT_CHARS = 200_000
        private const val MAX_GRADE_CONTEXT_CHARS = 40_000
        private const val MAX_ATTACHMENT_BYTES = 15L * 1024L * 1024L
        private const val MAX_MISSING = 5




    }
}
