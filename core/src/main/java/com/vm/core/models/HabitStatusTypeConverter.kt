package com.vm.core.models

import androidx.room.TypeConverter

class HabitStatusTypeConverter {
    @TypeConverter
    fun fromStatus(status: HabitStatus): String = status.name

    @TypeConverter
    fun toStatus(value: String): HabitStatus =
        runCatching { HabitStatus.valueOf(value) }.getOrDefault(HabitStatus.NONE)
}
