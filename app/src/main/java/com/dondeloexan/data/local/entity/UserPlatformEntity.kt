package com.dondeloexan.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "user_platforms")
data class UserPlatformEntity(
    @PrimaryKey @ColumnInfo(name = "platform_name") val platformName: String,
    @ColumnInfo(name = "is_active") val isActive: Boolean = true
)
