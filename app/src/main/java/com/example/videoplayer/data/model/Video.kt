package com.example.videoplayer.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 视频条目，对应桌面版 videos 表。
 * fileUri 为 content:// document Uri；lastPosition 记录上次播放位置（毫秒）。
 */
@Entity(
    tableName = "videos",
    foreignKeys = [
        ForeignKey(
            entity = VideoCollection::class,
            parentColumns = ["id"],
            childColumns = ["collectionId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("collectionId")]
)
data class Video(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val collectionId: Long,
    val fileName: String,
    val fileUri: String,
    val duration: Long = 0,
    val coverUri: String? = null,
    val sortOrder: Int = 0,
    val lastPosition: Long = 0,
    val addedAt: Long = System.currentTimeMillis()
)
