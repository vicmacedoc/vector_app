package com.vm.core.models

import androidx.room.TypeConverter

object RoutineTypeConverter {
    @JvmStatic
    @TypeConverter
    fun fromRoutineType(type: RoutineType): String {
        return type.name
    }
    
    @JvmStatic
    @TypeConverter
    fun toRoutineType(type: String): RoutineType {
        return RoutineType.valueOf(type)
    }
}
