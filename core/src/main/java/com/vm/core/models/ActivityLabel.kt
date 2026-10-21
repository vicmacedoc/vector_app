package com.vm.core.models

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Entity(tableName = "activity_labels")
@Serializable
data class ActivityLabel(
    @PrimaryKey val id: String,
    val name: String,
    val colorArgb: Int,
    val isSystem: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
) {
    companion object {
        const val SLEEP_LABEL_ID = "label_sleep"
        const val SLEEP_LABEL_NAME = "Sleep"
        /** Slate gray for sleep segments. */
        const val SLEEP_COLOR_ARGB: Int = 0xFF78909C.toInt()

        /**
         * Starter labels seeded once alongside Sleep. Unlike Sleep they are regular
         * (deletable) labels; list order is the display order.
         */
        val DEFAULT_LABELS: List<ActivityLabel> = listOf(
            "Meditative" to 0xFF424242,
            "Reflective" to 0xFF7B1FA2,
            "Useless" to 0xFFD81B60,
            "Leisure" to 0xFFD32F2F,
            "Social" to 0xFFF57C00,
            "Self-care" to 0xFFF9A825,
            "Duties" to 0xFF7CB342,
            "Work" to 0xFF2E7D32,
            "Study" to 0xFF00897B,
            "Project" to 0xFF1565C0,
            "Exercise" to 0xFF6D4C41,
        ).mapIndexed { index, (name, color) ->
            ActivityLabel(
                id = "label_default_${name.lowercase().replace("-", "_")}",
                name = name,
                colorArgb = color.toInt(),
                isSystem = false,
                createdAt = index + 1L,
            )
        }
    }
}
