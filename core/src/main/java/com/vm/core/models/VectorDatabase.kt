package com.vm.core.models

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

/** Bump together with a new Migration in VectorApplication and a fresh exported schema in core/schemas. */
const val VECTOR_DB_VERSION = 38

@Database(
    entities = [
        FoodEntry::class,
        HabitDefinition::class,
        HabitDayConfig::class,
        HabitLog::class,
        ActivityLabel::class,
        ActivityEntry::class,
        WorkoutSet::class,
        DailyHomeEntry::class,
        SleepEntry::class,
        SelfEntry::class,
        SelfCollection::class,
        SelfCollectionImage::class,
        ExerciseLibraryItem::class,
        MealLibraryItem::class,
        RoutinePreset::class,
        ActivityLibraryItem::class,
        BrainNode::class,
        BrainAsset::class,
        MemoryNote::class,
        LearningTile::class,
        LearningLink::class,
        AudioProgress::class,
        MotionTask::class,
        BrainFts::class,
        BrainTag::class,
        CalendarDraft::class,
        MotionScoreEvent::class
    ],
    version = VECTOR_DB_VERSION,
    exportSchema = true
)
@TypeConverters(
    AuditStatusTypeConverter::class,
    HabitStatusTypeConverter::class,
    ActivityTypeConverters::class,
    BrainTypeConverters::class
)
abstract class VectorDatabase : RoomDatabase() {
    abstract fun foodEntryDao(): FoodEntryDao
    abstract fun habitDefinitionDao(): HabitDefinitionDao
    abstract fun habitDayConfigDao(): HabitDayConfigDao
    abstract fun habitLogDao(): HabitLogDao
    abstract fun activityLabelDao(): ActivityLabelDao
    abstract fun activityEntryDao(): ActivityEntryDao
    abstract fun workoutSetDao(): WorkoutSetDao
    abstract fun dailyHomeEntryDao(): DailyHomeEntryDao
    abstract fun sleepEntryDao(): SleepEntryDao
    abstract fun selfEntryDao(): SelfEntryDao
    abstract fun selfCollectionDao(): SelfCollectionDao
    abstract fun selfCollectionImageDao(): SelfCollectionImageDao
    abstract fun exerciseLibraryItemDao(): ExerciseLibraryItemDao
    abstract fun mealLibraryItemDao(): MealLibraryItemDao
    abstract fun routinePresetDao(): RoutinePresetDao
    abstract fun activityLibraryItemDao(): ActivityLibraryItemDao
    abstract fun brainNodeDao(): BrainNodeDao
    abstract fun brainAssetDao(): BrainAssetDao
    abstract fun memoryNoteDao(): MemoryNoteDao
    abstract fun learningTileDao(): LearningTileDao
    abstract fun learningLinkDao(): LearningLinkDao
    abstract fun audioProgressDao(): AudioProgressDao
    abstract fun motionTaskDao(): MotionTaskDao
    abstract fun brainFtsDao(): BrainFtsDao
    abstract fun brainTagDao(): BrainTagDao
    abstract fun calendarDraftDao(): CalendarDraftDao
    abstract fun motionScoreEventDao(): MotionScoreEventDao
}
