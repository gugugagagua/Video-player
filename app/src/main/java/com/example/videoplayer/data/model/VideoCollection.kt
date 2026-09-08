package com.example.videoplayer.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 视频集，对应桌面版 collections 表。
 * folderUri 为 SAF tree Uri 字符串；coverUri 为封面 Uri（content:// 或 file://）。
 */
@Entity(
    tableName = "collections",
    foreignKeys = [
        ForeignKey(
            entity = Group::class,
            parentColumns = ["id"],
            childColumns = ["groupId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [Index("groupId"), Index("name")]
)
data class VideoCollection(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val folderUri: String,
    val coverUri: String? = null,
    val groupId: Long? = null,
    val lastVideoId: Long? = null,
    val sortOrder: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
