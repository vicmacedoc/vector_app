package com.vm.core.models

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

/**
 * Tag library entry. Memory and Learn tiles keep separate libraries ([section] is MEMORY or MODEL);
 * tiles store tag names in [BrainNode.tags].
 */
@Entity(tableName = "brain_tag", indices = [Index(value = ["section"])])
@Serializable
data class BrainTag(
    @PrimaryKey val id: String,
    val name: String,
    val createdAt: Long = System.currentTimeMillis(),
    val section: BrainSection = BrainSection.MEMORY,
) {
    companion object {
        const val DEFAULT_ID_PREFIX = "tag_default_"
        const val MODEL_DEFAULT_ID_PREFIX = "tag_model_"

        /** Starter tags for classifying memories, seeded once; all deletable. */
        val DEFAULT_TAGS: List<BrainTag> = listOf(
            "Ideas",
            "Reminders",
            "Fun Facts",
            "Quotes",
            "To Learn",
            "Goals",
            "Projects",
            "Journal",
            "Work",
            "Personal",
            "People",
            "Places",
            "Travel",
            "Books",
            "Movies",
            "Music",
            "Recipes",
            "Health",
            "Fitness",
            "Finance",
            "Tech",
            "Science",
            "History",
            "Psychology",
            "Productivity",
            "Mindset",
        ).mapIndexed { index, name ->
            BrainTag(
                id = DEFAULT_ID_PREFIX + name.lowercase().replace(' ', '_'),
                name = name,
                createdAt = index + 1L,
            )
        }

        /** Starter knowledge fields for Learn tiles, seeded once; all deletable. */
        val MODEL_DEFAULT_TAGS: List<BrainTag> = listOf(
            "Philosophy",
            "Law",
            "Mathematics",
            "Statistics",
            "Physics",
            "Chemistry",
            "Biology",
            "Medicine",
            "Neuroscience",
            "Psychology",
            "Engineering",
            "Software",
            "AI",
            "Data",
            "Economics",
            "Finance",
            "Business",
            "History",
            "Politics",
            "Sociology",
            "Languages",
            "Literature",
            "Art",
            "Design",
            "Music Theory",
            "Astronomy",
            "Geography",
        ).mapIndexed { index, name ->
            BrainTag(
                id = MODEL_DEFAULT_ID_PREFIX + name.lowercase().replace(' ', '_'),
                name = name,
                createdAt = index + 1L,
                section = BrainSection.MODEL,
            )
        }
    }
}
