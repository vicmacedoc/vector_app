package com.vm.core.models

import kotlinx.serialization.Serializable

/**
 * The 1–5 self-rating scales logged per day. Each one names both poles so the
 * number keeps the same meaning across days, and across the Calendar and Analysis.
 *
 * Every scale runs worst to best, so level 5 and the green end of the mood ramp
 * always mean the same thing. Stress and Physical read inverted for that reason.
 */
@Serializable
enum class SelfScale(
    val id: String,
    val label: String,
    val lowLabel: String,
    val highLabel: String,
) {
    MOOD("mood", "Mood", "Sad", "Happy"),
    ENERGY("energy", "Energy", "Lethargic", "Wired"),
    STRESS("stress", "Stress", "Overwhelmed", "Relaxed"),
    COGNITION("cognition", "Cognition", "Scattered", "Focused"),
    MOTIVATION("motivation", "Motivation", "Apathetic", "Driven"),
    PHYSICAL("physical", "Physical", "Exhausted", "Fresh"),
    SLEEP("sleep", "Sleep", "Restless", "Restorative"),
    SOCIAL("social", "Social", "Withdrawn", "Connected"),
    SELF_ESTEEM("self_esteem", "Self-esteem", "Insecure", "Confident"),
    ACTIONS("actions", "Actions", "Shameful", "Proud"),
    EXTERNAL("external", "External", "Resentful", "Grateful"),
    FUTURE("future", "Future", "Pessimistic", "Optimistic");

    fun valueIn(entry: SelfEntry): Int? = when (this) {
        MOOD -> entry.mood
        ENERGY -> entry.energy
        STRESS -> entry.stress
        COGNITION -> entry.cognition
        MOTIVATION -> entry.motivation
        PHYSICAL -> entry.physical
        SLEEP -> entry.sleepQuality
        SOCIAL -> entry.social
        SELF_ESTEEM -> entry.selfEsteem
        ACTIONS -> entry.actions
        EXTERNAL -> entry.external
        FUTURE -> entry.future
    }

    fun applyTo(entry: SelfEntry, value: Int?): SelfEntry {
        val level = value?.coerceIn(1, 5)
        return when (this) {
            MOOD -> entry.copy(mood = level)
            ENERGY -> entry.copy(energy = level)
            STRESS -> entry.copy(stress = level)
            COGNITION -> entry.copy(cognition = level)
            MOTIVATION -> entry.copy(motivation = level)
            PHYSICAL -> entry.copy(physical = level)
            SLEEP -> entry.copy(sleepQuality = level)
            SOCIAL -> entry.copy(social = level)
            SELF_ESTEEM -> entry.copy(selfEsteem = level)
            ACTIONS -> entry.copy(actions = level)
            EXTERNAL -> entry.copy(external = level)
            FUTURE -> entry.copy(future = level)
        }
    }

    companion object {
        fun fromId(id: String?): SelfScale? =
            id?.let { value -> entries.firstOrNull { it.id.equals(value.trim(), ignoreCase = true) } }

        /** Scales other than [MOOD], which the Calendar renders with its emoji row. */
        val secondary: List<SelfScale> get() = entries.filter { it != MOOD }
    }
}
