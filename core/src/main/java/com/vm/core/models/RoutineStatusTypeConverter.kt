package com.vm.core.models

import androidx.room.TypeConverter

object RoutineStatusTypeConverter {
    @JvmStatic
    @TypeConverter
    fun fromRoutineStatus(status: RoutineStatus): String {
        return status.name
    }
    
    @JvmStatic
    @TypeConverter
    fun toRoutineStatus(status: String): RoutineStatus {
        return RoutineStatus.valueOf(status)
    }
}
