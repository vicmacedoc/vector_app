package com.vm.vector.data

import com.vm.core.models.ActivityEntry
import com.vm.core.models.ActivityCompletion
import com.vm.core.models.ActivityLabel
import com.vm.core.models.ActivityType
import com.vm.core.models.ActivityLibraryItem
import java.util.Locale
import java.util.UUID
import kotlinx.serialization.Serializable

enum class ActivityImportSource { AI, GOOGLE_CALENDAR, LIBRARY, CLIPBOARD }

/** Clipboard JSON shape for one routine activity (single object or array item). */
@Serializable
data class ActivityClipboardJson(
    val title: String = "",
    val startTime: String = "09:00",
    val endTime: String = "10:00",
    val label: String = "",
    val color: String = "#1565C0",
    val completion: String = "NONE",
    val type: String = "PLANNED",
    val description: String = "",
)

/**
 * Parses clipboard activities for [mode]: one object for [CalendarAiMode.SINGLE], otherwise an array
 * or `{ "activities": [...] }`. Items without a title are rejected.
 */
fun parseActivityClipboardText(text: String, mode: CalendarAiMode): Result<List<ActivityClipboardJson>> =
    clipboardItemsFor(text, mode, wrapperKey = "activities", itemNoun = "activity").mapCatching { objects ->
        objects.mapIndexed { index, obj ->
            val item = calendarClipboardJson.decodeFromJsonElement(ActivityClipboardJson.serializer(), obj)
            require(item.title.isNotBlank()) {
                if (objects.size == 1) "Activity is missing a title" else "Activity ${index + 1} is missing a title"
            }
            item
        }
    }

fun ActivityClipboardJson.toImportDraft(): ActivityImportDraft = ActivityImportDraft(
    title = title,
    startTime = normalizeActivityHhMm(startTime, ActivityImportDefaults.START_TIME),
    endTime = normalizeActivityHhMm(endTime, ActivityImportDefaults.END_TIME),
    labelName = label.asOptionalImportValue(),
    type = parseActivityTypeOrDefault(type),
    description = description,
    source = ActivityImportSource.CLIPBOARD,
)

/**
 * Source-agnostic activity draft before Room persistence.
 * Blank / null / "None" fields fall back to [ActivityImportDefaults] on merge.
 */
data class ActivityImportDraft(
    val title: String = "",
    val startTime: String = ActivityImportDefaults.START_TIME,
    val endTime: String = ActivityImportDefaults.END_TIME,
    /** Label name from source; unmatched names keep the current default label. */
    val labelName: String? = null,
    val type: ActivityType = ActivityImportDefaults.TYPE,
    val description: String = "",
    val source: ActivityImportSource = ActivityImportSource.AI,
    val externalId: String? = null,
)

object ActivityImportDefaults {
    const val START_TIME = "09:00"
    const val END_TIME = "10:00"
    val TYPE: ActivityType = ActivityType.PLANNED
}

/** Treat blank, "null", and "none" as missing so the wizard keeps defaults. */
fun String?.asOptionalImportValue(): String? {
    val trimmed = this?.trim().orEmpty()
    if (trimmed.isEmpty()) return null
    if (trimmed.equals("null", ignoreCase = true)) return null
    if (trimmed.equals("none", ignoreCase = true)) return null
    return trimmed
}

fun isValidActivityHhMm(time: String?): Boolean {
    val parts = time?.trim()?.split(":") ?: return false
    if (parts.size < 2) return false
    val h = parts[0].toIntOrNull() ?: return false
    val m = parts[1].toIntOrNull() ?: return false
    return h in 0..23 && m in 0..59
}

fun normalizeActivityHhMm(raw: String?, fallback: String): String {
    val value = raw.asOptionalImportValue() ?: return fallback
    if (!isValidActivityHhMm(value)) return fallback
    val parts = value.split(":")
    val h = parts[0].toInt()
    val m = parts[1].toInt()
    return String.format(Locale.US, "%02d:%02d", h, m)
}

fun parseActivityTypeOrDefault(raw: String?, fallback: ActivityType = ActivityImportDefaults.TYPE): ActivityType {
    val value = raw.asOptionalImportValue() ?: return fallback
    return runCatching { ActivityType.valueOf(value.uppercase(Locale.US)) }.getOrDefault(fallback)
}

/**
 * Resolve a draft against existing labels and form defaults.
 * Unmatched / blank labels clear [labelName] so the wizard keeps the default label chip.
 */
fun mergeActivityImportDraft(
    draft: ActivityImportDraft,
    labels: List<ActivityLabel>,
    defaultTitle: String = "",
    defaultStart: String = ActivityImportDefaults.START_TIME,
    defaultEnd: String = ActivityImportDefaults.END_TIME,
    defaultType: ActivityType = ActivityImportDefaults.TYPE,
    defaultDescription: String = "",
): ActivityImportDraft {
    val title = draft.title.asOptionalImportValue() ?: defaultTitle
    val startTime = normalizeActivityHhMm(draft.startTime, defaultStart)
    val endTime = normalizeActivityHhMm(draft.endTime, defaultEnd)
    val labelName = draft.labelName.asOptionalImportValue()
    val matchedLabel = labelName?.let { name ->
        labels.find { it.name.equals(name, ignoreCase = true) }
    }
    val type = draft.type.takeIf {
        it == ActivityType.PLANNED || it == ActivityType.NEW
    } ?: defaultType
    val description = draft.description.asOptionalImportValue() ?: defaultDescription
    return draft.copy(
        title = title,
        startTime = startTime,
        endTime = endTime,
        labelName = matchedLabel?.name,
        type = type,
        description = description,
    )
}

fun resolveImportLabelId(
    draft: ActivityImportDraft,
    labels: List<ActivityLabel>,
    defaultLabelId: String,
): String {
    val name = draft.labelName.asOptionalImportValue() ?: return defaultLabelId
    return labels.find { it.name.equals(name, ignoreCase = true) }?.id ?: defaultLabelId
}

fun ActivityImportDraft.toActivityEntry(
    date: String,
    labels: List<ActivityLabel>,
    defaultLabelId: String,
): ActivityEntry? {
    val trimmedTitle = title.trim()
    if (trimmedTitle.isBlank()) return null
    val labelId = resolveImportLabelId(this, labels, defaultLabelId).ifBlank {
        labels.firstOrNull()?.id.orEmpty()
    }
    if (labelId.isBlank()) return null
    return ActivityEntry(
        id = "act_${UUID.randomUUID()}",
        date = date,
        title = trimmedTitle,
        startTime = normalizeActivityHhMm(startTime, ActivityImportDefaults.START_TIME),
        endTime = normalizeActivityHhMm(endTime, ActivityImportDefaults.END_TIME),
        adjustedStartTime = null,
        adjustedEndTime = null,
        labelId = labelId,
        completion = ActivityCompletion.NONE,
        type = type,
        description = description.trim(),
    )
}

fun ActivityImportDraft.validationError(
    labels: List<ActivityLabel>,
    defaultLabelId: String,
): String? {
    if (title.trim().isBlank()) return "Title is required"
    if (!isValidActivityHhMm(startTime)) return "Start time is invalid"
    if (!isValidActivityHhMm(endTime)) return "End time is invalid"
    val labelId = resolveImportLabelId(this, labels, defaultLabelId)
    if (labelId.isBlank() || labels.none { it.id == labelId }) {
        return "Select a label"
    }
    return null
}

fun ActivityLibraryItem.toImportDraft(): ActivityImportDraft = ActivityImportDraft(
    title = title,
    startTime = normalizeActivityHhMm(startTime, ActivityImportDefaults.START_TIME),
    endTime = normalizeActivityHhMm(endTime, ActivityImportDefaults.END_TIME),
    labelName = labelName.asOptionalImportValue(),
    type = type,
    description = description,
    source = ActivityImportSource.LIBRARY,
)
