package com.vm.vector.data

/** What a Calendar AI or clipboard request should produce for the selected day. */
enum class CalendarAiMode {
    /** Exactly one new item, added to the day. */
    SINGLE,

    /** One or more new items, added to the day. */
    MULTIPLE,

    /** The whole day, replacing every item on it. */
    PLAN_SCRATCH,

    /** The whole day, starting from that weekday's preset, replacing every item on it. */
    PLAN_PRESET;

    val isPlan: Boolean get() = this == PLAN_SCRATCH || this == PLAN_PRESET
}

/**
 * Grounding for Calendar AI prompts.
 * [existingItems] is the day's current content (used by add modes to avoid duplicates and clashes);
 * [presetJson] is the weekday's preset slice (used by [CalendarAiMode.PLAN_PRESET]);
 * [targets] is a short human-readable target line, e.g. the preset's macro totals.
 */
data class CalendarDayContext(
    val existingItems: String? = null,
    val presetJson: String? = null,
    val targets: String? = null,
)

/** Shared prompt section describing the day for [mode]. */
internal fun CalendarDayContext.promptSection(mode: CalendarAiMode, itemNoun: String): String = buildString {
    when (mode) {
        CalendarAiMode.SINGLE, CalendarAiMode.MULTIPLE -> {
            append("\n\n$itemNoun already on this day (do not repeat them; avoid time clashes):\n")
            append(existingItems?.takeIf { it.isNotBlank() } ?: "(none)")
        }
        CalendarAiMode.PLAN_SCRATCH -> Unit
        CalendarAiMode.PLAN_PRESET -> {
            append("\n\nThis weekday's preset (the starting plan):\n")
            append(presetJson?.takeIf { it.isNotBlank() } ?: "(no preset for this weekday)")
        }
    }
    targets?.takeIf { it.isNotBlank() && mode == CalendarAiMode.PLAN_PRESET }?.let {
        append("\nPreset targets: ")
        append(it)
    }
}
