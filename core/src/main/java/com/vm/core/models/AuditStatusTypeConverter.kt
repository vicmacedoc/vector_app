package com.vm.core.models

import androidx.room.TypeConverter

object AuditStatusTypeConverter {
    @JvmStatic
    @TypeConverter
    fun fromAuditStatus(status: AuditStatus): String {
        return status.name
    }
    
    @JvmStatic
    @TypeConverter
    fun toAuditStatus(status: String): AuditStatus {
        return AuditStatus.valueOf(status)
    }
}
