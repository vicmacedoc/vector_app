package com.vm.core.models

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [
        DietEntry::class,
        RoutineEntry::class,
        WorkoutSet::class,
        DailyHomeEntry::class,
        DiaryEntry::class,
        DiaryCollection::class,
        DiaryCollectionImage::class
    ],
    version = 16,
    exportSchema = false
)
@TypeConverters(
    AuditStatusTypeConverter::class,
    RoutineTypeConverter::class,
    RoutineStatusTypeConverter::class
)
abstract class VectorDatabase : RoomDatabase() {
    abstract fun dietEntryDao(): DietEntryDao
    abstract fun routineEntryDao(): RoutineEntryDao
    abstract fun workoutSetDao(): WorkoutSetDao
    abstract fun dailyHomeEntryDao(): DailyHomeEntryDao
    abstract fun diaryEntryDao(): DiaryEntryDao
    abstract fun diaryCollectionDao(): DiaryCollectionDao
    abstract fun diaryCollectionImageDao(): DiaryCollectionImageDao
}
