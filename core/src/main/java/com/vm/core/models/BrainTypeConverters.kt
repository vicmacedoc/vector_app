package com.vm.core.models

import androidx.room.TypeConverter
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class BrainTypeConverters {
    private val json = Json { ignoreUnknownKeys = true }

    @TypeConverter
    fun fromBrainSection(value: BrainSection): String = value.name

    @TypeConverter
    fun toBrainSection(value: String): BrainSection =
        runCatching { BrainSection.valueOf(value) }.getOrDefault(BrainSection.MEMORY)

    @TypeConverter
    fun fromSyncState(value: BrainAssetSyncState): String = value.name

    @TypeConverter
    fun toSyncState(value: String): BrainAssetSyncState =
        runCatching { BrainAssetSyncState.valueOf(value) }.getOrDefault(BrainAssetSyncState.PENDING)

    @TypeConverter
    fun fromNoteKind(value: MemoryNoteKind): String = value.name

    @TypeConverter
    fun toNoteKind(value: String): MemoryNoteKind =
        runCatching { MemoryNoteKind.valueOf(value) }.getOrDefault(MemoryNoteKind.TEXT)

    @TypeConverter
    fun fromTileStatus(value: LearningTileStatus): String = value.name

    @TypeConverter
    fun toTileStatus(value: String): LearningTileStatus =
        runCatching { LearningTileStatus.valueOf(value) }.getOrDefault(LearningTileStatus.BACKLOG)

    @TypeConverter
    fun fromMotionPriority(value: MotionPriority): String = value.name

    @TypeConverter
    fun toMotionPriority(value: String): MotionPriority = MotionPriority.parse(value)

    @TypeConverter
    fun fromScoreSource(value: MotionScoreSource): String = value.name

    @TypeConverter
    fun toScoreSource(value: String): MotionScoreSource =
        runCatching { MotionScoreSource.valueOf(value) }.getOrDefault(MotionScoreSource.TASK)

    @TypeConverter
    fun fromMotionStatus(value: MotionTaskStatus): String = value.name

    @TypeConverter
    fun toMotionStatus(value: String): MotionTaskStatus =
        runCatching { MotionTaskStatus.valueOf(value) }.getOrDefault(MotionTaskStatus.OPEN)

    @TypeConverter
    fun fromRecurrence(value: MotionRecurrence): String = value.name

    @TypeConverter
    fun toRecurrence(value: String): MotionRecurrence =
        runCatching { MotionRecurrence.valueOf(value) }.getOrDefault(MotionRecurrence.NONE)

    @TypeConverter
    fun fromStringList(value: List<String>): String = json.encodeToString(value)

    @TypeConverter
    fun toStringList(value: String): List<String> =
        runCatching { json.decodeFromString<List<String>>(value) }.getOrDefault(emptyList())

    @TypeConverter
    fun fromBookmarks(value: List<AudioBookmark>): String = json.encodeToString(value)

    @TypeConverter
    fun toBookmarks(value: String): List<AudioBookmark> =
        runCatching { json.decodeFromString<List<AudioBookmark>>(value) }.getOrDefault(emptyList())
}
