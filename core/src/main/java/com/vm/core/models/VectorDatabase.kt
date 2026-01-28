package com.vm.core.models

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [DietEntry::class],
    version = 2,
    exportSchema = false
)
@TypeConverters(AuditStatusTypeConverter::class)
abstract class VectorDatabase : RoomDatabase() {
    abstract fun dietEntryDao(): DietEntryDao
}
