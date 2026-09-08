package com.example.videoplayer.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 分组，对应桌面版 groups 表。
 * coverUri 为分组封面（可选，取自组内视频集的封面），为空时显示文件夹图标。
 */
@Entity(tableName = "groups")
data class Group(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val sortOrder: Int = 0,
    val coverUri: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)
