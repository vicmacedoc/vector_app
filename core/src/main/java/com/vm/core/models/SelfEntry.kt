package com.vm.core.models

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

/**
 * Per-date self log: twelve 1–5 scales (see [SelfScale]), the day's intention and
 * the journal (structured prompts + free text + Drive audio).
 *
 * Every field is optional so a day can be logged in five seconds or in five minutes.
 */
@Entity(tableName = "self_entry")
@Serializable
data class SelfEntry(
    @PrimaryKey val date: String,  // YYYY-MM-DD
    val mood: Int? = null,         // 1 = sad (red), 5 = happy (green)
    val energy: Int? = null,
    val stress: Int? = null,
    val cognition: Int? = null,
    val motivation: Int? = null,
    val physical: Int? = null,
    /** Subjective sleep quality, as opposed to the measured duration on the Home entry. */
    val sleepQuality: Int? = null,
    val social: Int? = null,
    val selfEsteem: Int? = null,
    val actions: Int? = null,
    val external: Int? = null,
    val future: Int? = null,
    val intention: String? = null,
    /** "What fuelled me" prompt. */
    val highlight: String? = null,
    /** "What drained me" prompt. */
    val lowlight: String? = null,
    val gratitude: String? = null,
    val journalText: String? = null,
    val journalAudioDriveFileId: String? = null,
    val timestamp: Long = System.currentTimeMillis()
) {
    /** True when the day carries any self signal at all. */
    fun hasAnyContent(): Boolean =
        SelfScale.entries.any { it.valueIn(this) != null } ||
            !intention.isNullOrBlank() ||
            hasReflection()

    /** Any written or spoken reflection, as opposed to a tap-only day. */
    fun hasReflection(): Boolean =
        !journalText.isNullOrBlank() ||
            !journalAudioDriveFileId.isNullOrBlank() ||
            !highlight.isNullOrBlank() ||
            !lowlight.isNullOrBlank() ||
            !gratitude.isNullOrBlank()

    /** Words across every written field; a proxy for how much was processed that day. */
    fun writtenWordCount(): Int =
        listOfNotNull(journalText, highlight, lowlight, gratitude)
            .sumOf { text -> text.trim().split(WHITESPACE).count { it.isNotBlank() } }

    companion object {
        private val WHITESPACE = Regex("\\s+")
    }
}
