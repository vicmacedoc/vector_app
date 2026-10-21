package com.vm.vector.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject

internal val calendarClipboardJson = Json {
    ignoreUnknownKeys = true
    isLenient = true
}

/**
 * Splits clipboard JSON into item objects according to [mode]:
 * - [CalendarAiMode.SINGLE]: exactly one object (no array, no `{ "<wrapperKey>": [...] }` wrapper).
 * - Other modes: an array of objects, or a wrapper object holding that array under [wrapperKey].
 */
internal fun clipboardItemsFor(
    text: String,
    mode: CalendarAiMode,
    wrapperKey: String,
    itemNoun: String,
): Result<List<JsonObject>> = runCatching {
    val trimmed = text.trim()
    require(trimmed.isNotEmpty()) { "Clipboard is empty" }
    val element = runCatching { calendarClipboardJson.parseToJsonElement(trimmed) }
        .getOrElse { throw IllegalArgumentException("Clipboard is not valid JSON") }
    val wrapped = (element as? JsonObject)?.get(wrapperKey) as? JsonArray
    if (mode == CalendarAiMode.SINGLE) {
        when {
            element is JsonArray || wrapped != null ->
                throw IllegalArgumentException("Clipboard holds a list. Use Add multiple instead.")
            element !is JsonObject -> throw IllegalArgumentException("Expected one JSON object for a $itemNoun")
        }
        listOf(element as JsonObject)
    } else {
        val array = when {
            element is JsonArray -> element
            wrapped != null -> wrapped
            element is JsonObject ->
                throw IllegalArgumentException(
                    "Clipboard holds a single $itemNoun. Use a JSON array or { \"$wrapperKey\": [...] }, or Add a single $itemNoun."
                )
            else -> throw IllegalArgumentException("Expected a JSON array of ${itemNoun}s")
        }
        val objects = array.map {
            it as? JsonObject ?: throw IllegalArgumentException("Every list item must be a JSON object")
        }
        require(objects.isNotEmpty()) { "The list is empty" }
        objects
    }
}
