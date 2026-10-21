package com.vm.core.models

import androidx.room.TypeConverter

class ActivityTypeConverters {
    @TypeConverter
    fun fromCompletion(value: ActivityCompletion): String = value.name

    @TypeConverter
    fun toCompletion(value: String): ActivityCompletion =
        runCatching { ActivityCompletion.valueOf(value) }.getOrDefault(ActivityCompletion.NONE)

    @TypeConverter
    fun fromType(value: ActivityType): String = value.name

    @TypeConverter
    fun toType(value: String): ActivityType =
        runCatching { ActivityType.valueOf(value) }.getOrDefault(ActivityType.NEW)
}
