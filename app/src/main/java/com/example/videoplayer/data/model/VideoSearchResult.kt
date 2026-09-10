package com.example.videoplayer.data.model

/**
 * 视频搜索结果（含所属视频集名称与观看进度），用于按文件名搜索。
 */
data class VideoSearchResult(
    val id: Long,
    val fileName: String,
    val collectionId: Long,
    val collectionName: String,
    val lastPosition: Long = 0,
    val duration: Long = 0
)
