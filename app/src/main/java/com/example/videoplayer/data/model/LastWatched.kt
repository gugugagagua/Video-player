package com.example.videoplayer.data.model

/**
 * 某视频集「看到第几集」。
 * sortOrder 为该集在视频集内的排序下标（从 0 开始），展示时 +1 即为集号。
 */
data class LastWatched(
    val collectionId: Long,
    val sortOrder: Int
)
