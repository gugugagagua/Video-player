package com.example.videoplayer.media

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/**
 * 封面管理器，对应桌面版 CoverManager。
 * 封面优先级：文件夹内 cover.png > 视频首帧提取 > 无封面。
 */
object CoverManager {

    private const val COVER_DIR = "covers"

    /** 帧总像素上限：超过则走缩放抽帧（≈4M 像素，约 2K） */
    private const val MAX_FRAME_PIXELS = 4_000_000L

    /** 缩放抽帧时的最长边（封面显示尺寸足够） */
    private const val MAX_FRAME_EDGE = 1920

    /**
     * 获取视频集封面：
     * 1. 查找文件夹中的 cover.png/poster.png 等约定文件；
     * 2. 否则取第一个视频的第一帧提取封面。
     */
    suspend fun ensureCollectionCover(
        context: Context,
        collectionId: Long,
        folderUri: Uri,
        firstVideoUri: Uri?,
        existingCover: String?
    ): String? = withContext(Dispatchers.IO) {
        // 已有封面则直接使用
        existingCover?.let { return@withContext it }

        // 1. 文件夹内 cover 文件
        val folderCover = VideoScanner.findCoverInFolder(context, folderUri)
        if (folderCover != null) return@withContext folderCover.toString()

        // 2. 从视频首帧提取
        firstVideoUri?.let { uri ->
            extractFrame(context, uri)?.let { bitmap ->
                saveCover(context, "collection_${collectionId}", bitmap)?.let {
                    return@withContext it.toString()
                }
            }
        }
        null
    }

    /**
     * 提取视频第一帧作为封面。
     */
    suspend fun extractVideoCover(
        context: Context,
        videoKey: String,
        videoUri: Uri
    ): String? = withContext(Dispatchers.IO) {
        extractFrame(context, videoUri)?.let { bitmap ->
            saveCover(context, "video_$videoKey", bitmap)?.toString()
        }
    }

    private fun extractFrame(context: Context, uri: Uri): Bitmap? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            val frame = try {
                // 超过像素上限时用缩放抽帧，避免 4K/8K 视频解码大图导致 OOM（S-3）
                if (needsDownScale(retriever) && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                    retriever.getScaledFrameAtTime(
                        1_000_000L,
                        MediaMetadataRetriever.OPTION_CLOSEST_SYNC,
                        MAX_FRAME_EDGE,
                        MAX_FRAME_EDGE
                    ) ?: retriever.getFrameAtTime()
                } else {
                    // 取 1 秒处帧，部分文件第 0 帧可能是黑屏
                    retriever.getFrameAtTime(1_000_000L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                        ?: retriever.getFrameAtTime()
                }
            } catch (e: Throwable) {
                null
            }
            frame
        } catch (e: Throwable) {
            null
        } finally {
            runCatching { retriever.release() }
        }
    }

    /** 判断是否需要缩小帧尺寸（防止超大分辨率 OOM） */
    private fun needsDownScale(retriever: MediaMetadataRetriever): Boolean {
        return try {
            val w = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
                ?.toLongOrNull() ?: 0L
            val h = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
                ?.toLongOrNull() ?: 0L
            w > 0 && h > 0 && w * h > MAX_FRAME_PIXELS
        } catch (e: Throwable) {
            false
        }
    }

    private fun saveCover(context: Context, fileName: String, bitmap: Bitmap): File? {
        return try {
            val dir = File(context.filesDir, COVER_DIR).apply { mkdirs() }
            // 统一以 .jpg 存储（内容是 JPEG），避免扩展名与实际格式不符
            val file = File(dir, fileName.substringBeforeLast('.').ifBlank { "cover" } + ".jpg")
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)
            }
            file
        } catch (e: Exception) {
            null
        }
    }

    /**
     * 提取视频时长（毫秒）。
     */
    suspend fun extractDuration(context: Context, uri: Uri): Long = withContext(Dispatchers.IO) {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, uri)
            val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            duration?.toLongOrNull() ?: 0L
        } catch (e: Exception) {
            0L
        } finally {
            runCatching { retriever.release() }
        }
    }
}
