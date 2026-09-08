package com.example.videoplayer.media

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.documentfile.provider.DocumentFile
import com.github.junrar.Archive
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import kotlin.math.min

/**
 * 导入目标（媒体库根目录）。
 * - SafTree：SAF 授权目录模式
 * - FilePath：固定路径模式（需所有文件访问权限）
 */
sealed class ImportDestination {
    data class SafTree(val uri: Uri) : ImportDestination()
    data class FilePath(val path: String) : ImportDestination()
}

/** 压缩包类型 */
enum class ArchiveType { NONE, ZIP, RAR }

/** 待确认的导入项（已提取建议名称） */
data class PendingImport(
    val sourceUri: Uri,
    /** 自动提取的文件名（去掉扩展名），作为视频集名称建议 */
    val suggestedName: String,
    val archiveType: ArchiveType
) {
    val isArchive: Boolean get() = archiveType != ArchiveType.NONE
}

/** 用户确认后的导入项 */
data class ConfirmedImport(
    val pending: PendingImport,
    val collectionName: String
)

/** 导入结果 */
data class ImportResult(
    val createdCollections: Int = 0,
    val importedVideos: Int = 0,
    val skipped: Int = 0,
    val failures: List<String> = emptyList()
)

/**
 * 视频导入器：把外部视频文件或压缩包，按
 * 「媒体库根目录 / 视频集名称 / 视频文件」的规则归类存放。
 *
 * 支持一次导入多个文件；压缩包（zip / rar）会解压并只提取其中的视频。
 */
class VideoImporter(private val context: Context) {

    private val contentResolver get() = context.contentResolver

    /**
     * 分析选中的文件：过滤出视频与压缩包，并提取建议的视频集名称。
     */
    suspend fun analyze(uris: List<Uri>): List<PendingImport> = withContext(Dispatchers.IO) {
        uris.mapNotNull { uri ->
            val displayName = queryDisplayName(uri) ?: return@mapNotNull null
            val ext = displayName.substringAfterLast('.', "").lowercase()
            val archiveType = when (ext) {
                "zip" -> ArchiveType.ZIP
                "rar" -> ArchiveType.RAR
                else -> ArchiveType.NONE
            }
            // 既不是视频也不是支持的压缩包则忽略
            if (archiveType == ArchiveType.NONE && !VideoScanner.isVideoName(displayName)) {
                return@mapNotNull null
            }
            PendingImport(
                sourceUri = uri,
                suggestedName = displayName.substringBeforeLast('.').ifBlank { displayName },
                archiveType = archiveType
            )
        }
    }

    /**
     * 执行导入：在媒体库根目录下为每个条目建立「视频集」子目录并放入视频。
     */
    suspend fun import(
        items: List<ConfirmedImport>,
        destination: ImportDestination
    ): ImportResult = withContext(Dispatchers.IO) {
        var created = 0
        var imported = 0
        var skipped = 0
        val failures = mutableListOf<String>()
        // 同名目录只计一次（多个文件归并到同一视频集时避免重复计数）
        val countedDirs = mutableSetOf<String>()

        items.forEach { item ->
            val name = item.collectionName.trim().ifBlank { item.pending.suggestedName }
            val dir = createCollectionDir(destination, name)
            if (dir == null) {
                failures.add("无法创建目录：$name")
                return@forEach
            }
            if (countedDirs.add(name)) created++

            when (item.pending.archiveType) {
                ArchiveType.NONE -> {
                    val uri = item.pending.sourceUri
                    val fileName = queryDisplayName(uri) ?: "video.mp4"
                    val ok = copyTo(uri, dir, fileName, destination)
                    if (ok) imported++ else {
                        skipped++
                        failures.add("导入失败：$fileName")
                    }
                }
                ArchiveType.ZIP -> {
                    val result = extractZip(item.pending.sourceUri, dir, destination)
                    imported += result.first
                    if (result.first == 0) {
                        skipped++
                        failures.add("压缩包内没有视频：${name}")
                    }
                }
                ArchiveType.RAR -> {
                    val result = extractRar(item.pending.sourceUri, dir, destination)
                    imported += result
                    if (result == 0) {
                        skipped++
                        failures.add("压缩包内没有视频：${name}")
                    }
                }
            }
        }

        ImportResult(created, imported, skipped, failures)
    }

    // ───────── 目录与文件写入 ─────────

    /** 在媒体库根目录下创建（或复用）视频集目录，返回目录句柄 */
    private fun createCollectionDir(
        destination: ImportDestination,
        name: String
    ): Any? {
        return when (destination) {
            is ImportDestination.FilePath -> {
                val dir = File(destination.path, sanitize(name))
                if ((dir.exists() && dir.isDirectory) || dir.mkdirs()) dir else null
            }
            is ImportDestination.SafTree -> {
                val root = DocumentFile.fromTreeUri(context, destination.uri) ?: return null
                root.listFiles().firstOrNull { it.isDirectory && it.name == sanitize(name) }
                    ?: root.createDirectory(sanitize(name))
            }
        }
    }

    /** 打开目标目录下的输出流 */
    private fun openTarget(
        dir: Any,
        destination: ImportDestination,
        fileName: String
    ): OutputStream? {
        return when (destination) {
            is ImportDestination.FilePath -> {
                val parent = dir as File
                val file = uniqueFile(parent, fileName)
                FileOutputStream(file)
            }
            is ImportDestination.SafTree -> {
                val parent = dir as DocumentFile
                val mime = VideoScanner.mimeTypeOf(fileName)
                val doc = parent.createFile(mime, fileName) ?: return null
                contentResolver.openOutputStream(doc.uri)
            }
        }
    }

    /** 复制单个视频文件到视频集目录 */
    private fun copyTo(
        sourceUri: Uri,
        dir: Any,
        fileName: String,
        destination: ImportDestination
    ): Boolean {
        return try {
            val input = contentResolver.openInputStream(sourceUri) ?: return false
            val output = openTarget(dir, destination, fileName) ?: return false
            input.use { i -> output.use { o -> i.copyTo(o, BUFFER_SIZE) } }
            true
        } catch (e: Exception) {
            false
        }
    }

    // ───────── 压缩包解压 ─────────

    /** 解压 ZIP 内的视频，返回导入数量 */
    private fun extractZip(
        sourceUri: Uri,
        dir: Any,
        destination: ImportDestination
    ): Pair<Int, Int> {
        var count = 0
        var total = 0
        try {
            contentResolver.openInputStream(sourceUri)?.use { raw ->
                ZipInputStream(raw.buffered(BUFFER_SIZE)).use { zis ->
                    var entry: ZipEntry? = zis.nextEntry
                    while (entry != null) {
                        val name = entry.name.substringAfterLast('/')
                        if (!entry.isDirectory && VideoScanner.isVideoName(name)) {
                            total++
                            if (writeEntry(dir, destination, name, zis)) count++
                        }
                        zis.closeEntry()
                        entry = zis.nextEntry
                    }
                }
            }
        } catch (e: Exception) {
            return count to total
        }
        return count to total
    }

    /** 解压 RAR 内的视频，返回导入数量 */
    private fun extractRar(
        sourceUri: Uri,
        dir: Any,
        destination: ImportDestination
    ): Int {
        var count = 0
        try {
            contentResolver.openInputStream(sourceUri)?.use { raw ->
                val archive = Archive(raw)
                try {
                    var header = archive.nextFileHeader()
                    while (header != null) {
                        val name = header.fileNameString.substringAfterLast('\\')
                            .substringAfterLast('/')
                        if (!header.isDirectory && VideoScanner.isVideoName(name)) {
                            archive.getInputStream(header)?.use { input ->
                                if (writeEntry(dir, destination, name, input)) count++
                            }
                        }
                        header = archive.nextFileHeader()
                    }
                } finally {
                    runCatching { archive.close() }
                }
            }
        } catch (e: Exception) {
            return count
        }
        return count
    }

    private fun writeEntry(
        dir: Any,
        destination: ImportDestination,
        fileName: String,
        input: InputStream
    ): Boolean {
        return try {
            val output = openTarget(dir, destination, fileName) ?: return false
            output.use { o -> input.copyTo(o, BUFFER_SIZE) }
            true
        } catch (e: Exception) {
            false
        }
    }

    // ───────── 工具方法 ─────────

    /** 去掉文件名中的非法字符 */
    private fun sanitize(name: String): String =
        name.replace(Regex("[\\\\/:*?\"<>|]"), "_").trim().ifBlank { "未命名" }

    private fun uniqueFile(parent: File, fileName: String): File {
        var file = File(parent, fileName)
        if (!file.exists()) return file
        val base = fileName.substringBeforeLast('.')
        val ext = fileName.substringAfterLast('.', "")
        var i = 1
        while (file.exists() && i < 1000) {
            file = File(parent, if (ext.isBlank()) "${base}_$i" else "${base}_$i.$ext")
            i++
        }
        return file
    }

    /** 查询 content Uri 对应的显示文件名 */
    private fun queryDisplayName(uri: Uri): String? {
        if (uri.scheme == "content") {
            contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0) return cursor.getString(idx)
                }
            }
        }
        return uri.lastPathSegment
            ?.substringAfterLast('/')
            ?.substringAfterLast(':')
            ?.takeIf { it.isNotBlank() }
    }

    private companion object {
        const val BUFFER_SIZE = 64 * 1024
    }
}
