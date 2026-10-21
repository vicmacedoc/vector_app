package com.vm.core.models

import kotlinx.serialization.Serializable

@Serializable
enum class BrainSection { MEMORY, MODEL, MOTION }

@Serializable
enum class BrainAssetSyncState {
    PENDING,
    WIFI_ONLY,
    UPLOADED,
    FAILED,
    DELETE_PENDING
}

@Serializable
enum class MemoryNoteKind { TEXT, AUDIO, IMAGE, LINK, PDF, VIDEO, FILE }

/**
 * Stored by name. Three groups on screen: Backlog (PRIORITY, BACKLOG "General"),
 * In Progress (IN_PROGRESS "Active", ON_HOLD) and Learned (TO_REVIEW "Review", LEARNED "Done").
 */
@Serializable
enum class LearningTileStatus { BACKLOG, PRIORITY, IN_PROGRESS, ON_HOLD, TO_REVIEW, LEARNED }

@Serializable
enum class MotionPriority {
    NONE, LOW, MEDIUM, HIGH;

    companion object {
        /** Also reads the pre-v38 P1–P4 names (P1 = HIGH … P4 = NONE). */
        fun parse(value: String?): MotionPriority = when (value?.uppercase()) {
            "P1" -> HIGH
            "P2" -> MEDIUM
            "P3" -> LOW
            "P4" -> NONE
            null -> NONE
            else -> entries.firstOrNull { it.name == value.uppercase() } ?: NONE
        }
    }
}

@Serializable
enum class MotionTaskStatus { OPEN, DONE, CANCELLED }

@Serializable
enum class MotionRecurrence { NONE, DAILY, WEEKLY, MONTHLY }
