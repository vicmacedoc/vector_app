package com.vm.core.models

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [DietEntry::class, RoutineEntry::class],
    version = 4,
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
}
