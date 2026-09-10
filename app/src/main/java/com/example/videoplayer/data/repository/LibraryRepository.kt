package com.example.videoplayer.data.repository

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import androidx.room.withTransaction
import com.example.videoplayer.data.db.AppDatabase
import com.example.videoplayer.data.model.Group
import com.example.videoplayer.data.model.LastWatched
import com.example.videoplayer.data.model.Video
import com.example.videoplayer.data.model.VideoCollection
import com.example.videoplayer.data.model.VideoCount
import com.example.videoplayer.data.model.VideoSearchResult
import com.example.videoplayer.media.CoverManager
import com.example.videoplayer.media.VideoScanner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File

/** 扫描结果统计 */
data class ScanResult(
    val collections: Int = 0,
    val videos: Int = 0,
    val addedCollections: Int = 0
)

/**
 * 媒体库仓库，对应桌面版各 Manager 的业务逻辑汇总。
 */
class LibraryRepository(
    private val context: Context,
    private val db: AppDatabase
) {
    private val groupDao = db.groupDao()
    private val collectionDao = db.collectionDao()
    private val videoDao = db.videoDao()

    // ---- 分组 ----

    fun observeGroups(): Flow<List<Group>> = groupDao.observeAll()

    suspend fun createGroup(name: String): Long {
        val groups = groupDao.getAll()
        val maxOrder = groups.maxOfOrNull { it.sortOrder } ?: -1
        return groupDao.insert(Group(name = name, sortOrder = maxOrder + 1))
    }

    suspend fun renameGroup(groupId: Long, newName: String) {
        groupDao.getById(groupId)?.let {
            groupDao.update(it.copy(name = newName))
        }
    }

    /**
     * 设置分组封面（取自组内某个视频集的封面）。
     */
    suspend fun setGroupCover(groupId: Long, coverUri: String?) {
        groupDao.updateCover(groupId, coverUri)
    }

    suspend fun deleteGroup(groupId: Long) {
        groupDao.deleteById(groupId) // 外键 SET_NULL，视频集回到未分组
    }

    // ---- 视频集 ----

    fun observeCollections(): Flow<List<VideoCollection>> = collectionDao.observeAll()

    fun observeCollectionsByGroup(groupId: Long?): Flow<List<VideoCollection>> =
        collectionDao.observeByGroup(groupId)

    suspend fun getCollection(id: Long): VideoCollection? = collectionDao.getById(id)

    /**
     * 导入文件夹为视频集。对应桌面版"新建视频集"。
     */
    suspend fun importFolder(folderUri: Uri, name: String? = null): Long = withContext(Dispatchers.IO) {
        collectionDao.getByFolderUri(folderUri.toString())?.let { existing ->
            syncVideos(existing, folderUri, fileMode = false)
            return@withContext existing.id
        }

        val collectionName = name?.takeIf { it.isNotBlank() }
            ?: folderUri.lastPathSegment?.substringAfterLast(':')
                ?.substringBeforeLast('/')?.ifBlank { null }
            ?: "视频集 ${System.currentTimeMillis() % 10000}"

        val id = collectionDao.insert(
            VideoCollection(name = collectionName, folderUri = folderUri.toString())
        )
        val saved = collectionDao.getById(id) ?: return@withContext id

        syncVideos(saved, folderUri, fileMode = false)
        ensureCover(saved, folderUri, fileMode = false)
        id
    }

    // ───────── 媒体库根目录扫描 ─────────

    /**
     * 扫描媒体库根目录（SAF 模式）：根目录下每个子目录 = 一个视频集。
     * 根目录内直接存放的视频归入"未整理"视频集。
     */
    suspend fun scanLibraryRoot(rootUri: Uri): ScanResult = withContext(Dispatchers.IO) {
        val root = DocumentFile.fromTreeUri(context, rootUri) ?: return@withContext ScanResult()
        var collectionCount = 0
        var videoCount = 0
        var added = 0

        // 1. 子目录各自成为一个视频集
        for (child in root.listFiles()) {
            if (!child.isDirectory) continue
            val name = child.name ?: continue
            val uriStr = child.uri.toString()
            val existing = collectionDao.getByFolderUri(uriStr)
            val collection = existing ?: run {
                val id = collectionDao.insert(
                    VideoCollection(name = name, folderUri = uriStr)
                )
                collectionDao.getById(id)
            } ?: continue

            val count = syncVideos(collection, child.uri, fileMode = false)
            if (count > 0) {
                ensureCover(collection, child.uri, fileMode = false)
                collectionCount++
                videoCount += count
                if (existing == null) added++
            } else if (existing != null) {
                // 目录已无视频，移除空视频集
                collectionDao.deleteById(collection.id)
            }
        }

        // 2. 根目录直接存放的视频归入"未整理"
        val looseVideos = VideoScanner.scanFolder(
            context,
            VideoCollection(id = -1L, name = "", folderUri = root.uri.toString()),
            root.uri
        )
        if (looseVideos.isNotEmpty()) {
            val uriStr = root.uri.toString()
            val existing = collectionDao.getByFolderUri(uriStr)
            val collection = existing ?: run {
                val id = collectionDao.insert(
                    VideoCollection(name = "未整理", folderUri = uriStr)
                )
                collectionDao.getById(id)
            }
            if (collection != null) {
                val count = syncVideos(collection, root.uri, fileMode = false)
                ensureCover(collection, root.uri, fileMode = false)
                collectionCount++
                videoCount += count
                if (existing == null) added++
            }
        }

        ScanResult(collectionCount, videoCount, added)
    }

    /**
     * 扫描媒体库根目录（文件路径模式）：直接遍历真实文件系统。
     * 需要"所有文件访问"权限，行为与桌面版一致。
     */
    suspend fun scanLibraryPath(rootPath: String): ScanResult = withContext(Dispatchers.IO) {
        val root = File(rootPath)
        if (!root.isDirectory) return@withContext ScanResult()

        var collectionCount = 0
        var videoCount = 0
        var added = 0

        // 1. 子目录各自成为一个视频集
        root.listFiles()
            ?.filter { it.isDirectory && !it.name.startsWith(".") }
            ?.sortedBy { it.name }
            ?.forEach { dir ->
                val uriStr = Uri.fromFile(dir).toString()
                val existing = collectionDao.getByFolderUri(uriStr)
                val collection = existing ?: run {
                    val id = collectionDao.insert(
                        VideoCollection(name = dir.name, folderUri = uriStr)
                    )
                    collectionDao.getById(id)
                } ?: return@forEach

                val count = syncVideos(collection, Uri.fromFile(dir), fileMode = true)
                if (count > 0) {
                    ensureCover(collection, Uri.fromFile(dir), fileMode = true)
                    collectionCount++
                    videoCount += count
                    if (existing == null) added++
                } else if (existing != null) {
                    collectionDao.deleteById(collection.id)
                }
            }

        // 2. 根目录直接存放的视频归入"未整理"
        val looseCount = root.listFiles()?.count { it.isFile && VideoScanner.isVideoFile(it) } ?: 0
        if (looseCount > 0) {
            val uriStr = Uri.fromFile(root).toString()
            val existing = collectionDao.getByFolderUri(uriStr)
            val collection = existing ?: run {
                val id = collectionDao.insert(
                    VideoCollection(name = "未整理", folderUri = uriStr)
                )
                collectionDao.getById(id)
            }
            if (collection != null) {
                val count = syncVideos(collection, Uri.fromFile(root), fileMode = true)
                ensureCover(collection, Uri.fromFile(root), fileMode = true)
                collectionCount++
                videoCount += count
                if (existing == null) added++
            }
        }

        ScanResult(collectionCount, videoCount, added)
    }

    /**
     * 同步目录内的视频到数据库：新增新文件、移除已删除文件、修正排序。
     * 返回同步后的视频数量。
     */
    private suspend fun syncVideos(
        collection: VideoCollection,
        dirUri: Uri,
        fileMode: Boolean
    ): Int {
        val scanned = if (fileMode) {
            val dir = dirUri.path?.let { File(it) }?.takeIf { it.isDirectory }
                ?: return 0
            VideoScanner.scanDirectory(collection.id, dir)
        } else {
            VideoScanner.scanFolder(context, collection, dirUri)
        }

        val existing = videoDao.getByCollection(collection.id).associateBy { it.fileUri }
        val scannedUris = scanned.map { it.fileUri }.toSet()

        // 所有差异写操作放进单个事务，避免一次扫描产生几千次 DB round-trip（P-1）
        db.withTransaction {
            // 移除已不存在的文件
            existing.values
                .filter { it.fileUri !in scannedUris }
                .forEach { videoDao.deleteById(it.id) }

            // 新增文件
            val newVideos = scanned
                .filter { it.fileUri !in existing.keys }
                .mapIndexed { index, v -> v.copy(sortOrder = index) }
            if (newVideos.isNotEmpty()) videoDao.insertAll(newVideos)

            // 修正已有文件的排序（文件名改名/新增导致顺序变化）
            scanned.forEachIndexed { index, v ->
                val old = existing[v.fileUri]
                if (old != null && old.sortOrder != index) {
                    videoDao.update(old.copy(sortOrder = index))
                }
            }
        }
        return scanned.size
    }

    /**
     * 确保视频集有封面：目录内 cover 图片优先，否则截取首个视频首帧。
     */
    private suspend fun ensureCover(
        collection: VideoCollection,
        dirUri: Uri,
        fileMode: Boolean
    ) {
        if (!collection.coverUri.isNullOrBlank() && coverExists(collection.coverUri)) return

        val firstVideo = videoDao.getByCollection(collection.id).firstOrNull()
            ?: return
        val cover = if (fileMode) {
            // 文件模式优先查找目录内封面图片
            val dir = dirUri.path?.let { File(it) }
            val coverFile = dir?.let { VideoScanner.findCoverFile(it) }
            coverFile?.let { Uri.fromFile(it).toString() }
                ?: CoverManager.extractVideoCover(
                    context,
                    "collection_${collection.id}",
                    Uri.parse(firstVideo.fileUri)
                )
        } else {
            CoverManager.ensureCollectionCover(
                context,
                collection.id,
                dirUri,
                Uri.parse(firstVideo.fileUri),
                collection.coverUri
            )
        }
        if (cover != null) {
            collectionDao.update(collection.copy(coverUri = cover))
        }
    }

    private fun coverExists(coverUri: String): Boolean {
        return if (coverUri.startsWith("file://") || coverUri.startsWith("/")) {
            val path = coverUri.removePrefix("file://")
            File(path).exists()
        } else {
            true // content:// 交由加载器处理
        }
    }

    suspend fun renameCollection(collectionId: Long, newName: String) {
        collectionDao.getById(collectionId)?.let {
            collectionDao.update(
                it.copy(name = newName, updatedAt = System.currentTimeMillis())
            )
        }
    }

    suspend fun moveToGroup(collectionId: Long, groupId: Long?) {
        collectionDao.moveToGroup(collectionId, groupId)
    }

    /**
     * 删除视频集及其视频记录。
     */
    suspend fun deleteCollection(collectionId: Long) {
        collectionDao.deleteById(collectionId)
    }

    // ---- 视频 ----

    fun observeVideoCounts(): Flow<List<VideoCount>> = videoDao.observeCounts()

    /** 各视频集「看到第几集」 */
    fun observeLastWatched(): Flow<List<LastWatched>> = videoDao.observeLastWatched()

    /** 按视频文件名搜索（含所属视频集） */
    suspend fun searchVideos(query: String): List<VideoSearchResult> {
        val q = query.trim()
        return if (q.isBlank()) emptyList() else videoDao.searchVideos(q)
    }

    /**
     * 补全缺失的视频时长（用于进度可视化）。
     * 只处理有观看记录但缺时长的条目，并限制数量以免阻塞。
     */
    suspend fun ensureDurations(collectionId: Long, limit: Int = 30) {
        val targets = videoDao.getMissingDurations(collectionId).take(limit)
        targets.forEach { video ->
            val duration = CoverManager.extractDuration(context, Uri.parse(video.fileUri))
            if (duration > 0) videoDao.updateDuration(video.id, duration)
        }
    }

    fun observeVideos(collectionId: Long): Flow<List<Video>> =
        videoDao.observeByCollection(collectionId)

    suspend fun getVideos(collectionId: Long): List<Video> =
        videoDao.getByCollection(collectionId)

    suspend fun getVideo(id: Long): Video? = videoDao.getById(id)

    /**
     * 保存播放进度（毫秒），并更新"最近播放"。
     * - 同一事务内写入两张表（S-2，避免进程中断导致不一致）；
     * - lastVideoId 未变化时不写 collections 行（P-6，减少无效写与下游刷新）。
     */
    suspend fun savePlaybackPosition(collectionId: Long, videoId: Long, positionMs: Long) {
        db.withTransaction {
            videoDao.updateLastPosition(videoId, positionMs)
            if (collectionDao.getById(collectionId)?.lastVideoId != videoId) {
                collectionDao.updateLastVideo(collectionId, videoId)
            }
        }
    }

    /**
     * 视频首次播放时补全时长与封面。
     */
    suspend fun enrichVideo(video: Video, uri: Uri) {
        if (video.duration == 0L) {
            val duration = CoverManager.extractDuration(context, uri)
            if (duration > 0) videoDao.updateDuration(video.id, duration)
        }
        if (video.coverUri == null) {
            val cover = CoverManager.extractVideoCover(context, "${video.id}", uri)
            if (cover != null) videoDao.updateCover(video.id, cover)
        }
    }
}
