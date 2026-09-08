package com.example.videoplayer.media

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.example.videoplayer.data.model.Video
import com.example.videoplayer.data.model.VideoCollection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 视频文件扫描器，对应桌面版 VideoManager。
 * 通过 SAF 的 DocumentFile 遍历目录，识别视频文件并按自然顺序排序。
 */
object VideoScanner {

    private val VIDEO_EXTENSIONS = setOf(
        "mp4", "mkv", "avi", "mov", "wmv", "flv", "webm",
        "m4v", "mpg", "mpeg", "3gp", "ts", "m2ts", "rmvb", "rm"
    )

    private val COVER_NAMES = setOf("cover", "poster", "folder", "thumbnail")

    /** 视频文件扩展名 */
    val videoExtensions: Set<String> get() = VIDEO_EXTENSIONS

    /**
     * 在目录树中查找封面文件（cover.png 等约定文件名）。
     */
    suspend fun findCoverInFolder(context: Context, folderUri: Uri): Uri? =
        withContext(Dispatchers.IO) {
        val folder = DocumentFile.fromTreeUri(context, folderUri) ?: return@withContext null

        for (child in folder.listFiles()) {
            if (!child.isDirectory) {
                val name = child.name ?: continue
                val base = name.substringBeforeLast('.').lowercase()
                if (base in COVER_NAMES) {
                    return@withContext child.uri
                }
            }
        }
        null
    }

    /**
     * 扫描目录下（单层）的所有视频文件，返回按自然顺序排序的列表。
     * 对应桌面版扫描文件夹生成视频集。
     */
    suspend fun scanFolder(
        context: Context,
        collection: VideoCollection,
        folderUri: Uri
    ): List<Video> = withContext(Dispatchers.IO) {
        val folder = DocumentFile.fromTreeUri(context, folderUri) ?: return@withContext emptyList()

        val videos = folder.listFiles()
            .filter { !it.isDirectory && it.isVideoFile() }
            .map { doc ->
                Video(
                    collectionId = collection.id,
                    fileName = doc.name ?: doc.uri.lastPathSegment ?: "未命名",
                    fileUri = doc.uri.toString(),
                    sortOrder = 0
                )
            }
            .sortedWith(naturalOrderComparator())
        videos
    }

    private fun DocumentFile.isVideoFile(): Boolean {
        val name = name ?: return false
        val ext = name.substringAfterLast('.', "").lowercase()
        return ext in VIDEO_EXTENSIONS
    }

    /** 文件模式：判断是否为视频文件 */
    fun isVideoFile(file: java.io.File): Boolean {
        val ext = file.name.substringAfterLast('.', "").lowercase()
        return ext in VIDEO_EXTENSIONS
    }

    /** 按文件名判断是否为视频 */
    fun isVideoName(name: String): Boolean {
        val ext = name.substringAfterLast('.', "").lowercase()
        return ext in VIDEO_EXTENSIONS
    }

    /** 根据文件名推断 MIME 类型，供 SAF 创建文件使用 */
    fun mimeTypeOf(fileName: String): String {
        return when (fileName.substringAfterLast('.', "").lowercase()) {
            "mp4", "m4v" -> "video/mp4"
            "mkv" -> "video/x-matroska"
            "webm" -> "video/webm"
            "avi" -> "video/x-msvideo"
            "mov" -> "video/quicktime"
            "wmv" -> "video/x-ms-wmv"
            "flv" -> "video/x-flv"
            "3gp" -> "video/3gpp"
            "mpg", "mpeg" -> "video/mpeg"
            "ts" -> "video/mp2ts"
            else -> "video/*"
        }
    }

    /** 文件模式：查找目录内的封面图片 */
    suspend fun findCoverFile(dir: java.io.File): java.io.File? = withContext(Dispatchers.IO) {
        dir.listFiles()?.firstOrNull { f ->
            f.isFile && f.nameWithoutExtension.lowercase() in COVER_NAMES
        }
    }

    /**
     * 文件模式：扫描目录下的视频文件（自然排序）。
     * 以 file:// Uri 作为唯一标识，便于直接交给 ExoPlayer 播放。
     */
    suspend fun scanDirectory(
        collectionId: Long,
        dir: java.io.File
    ): List<Video> = withContext(Dispatchers.IO) {
        dir.listFiles()
            ?.filter { it.isFile && isVideoFile(it) }
            ?.map { file ->
                Video(
                    collectionId = collectionId,
                    fileName = file.name,
                    fileUri = android.net.Uri.fromFile(file).toString(),
                    sortOrder = 0
                )
            }
            ?.sortedWith(naturalOrderComparator())
            ?: emptyList()
    }

    /** 自然排序：对数字进行数值比较（集数排序更符合直觉） */
    private fun naturalOrderComparator(): Comparator<Video> = Comparator { a, b ->
        compareNatural(a.fileName, b.fileName)
    }

    private fun compareNatural(a: String, b: String): Int {
        val pa = a.split(Regex("(?<=\\D)(?=\\d)|(?<=\\d)(?=\\D)"))
        val pb = b.split(Regex("(?<=\\D)(?=\\d)|(?<=\\d)(?=\\D)"))
        val len = minOf(pa.size, pb.size)
        for (i in 0 until len) {
            val ca = pa[i]
            val cb = pb[i]
            val cmp = if (ca.all { it.isDigit() } && cb.all { it.isDigit() }) {
                val na = ca.trimStart('0')
                val nb = cb.trimStart('0')
                when {
                    na.length != nb.length -> na.length.compareTo(nb.length)
                    else -> na.compareTo(nb)
                }
            } else {
                ca.compareTo(cb, ignoreCase = true)
            }
            if (cmp != 0) return cmp
        }
        return pa.size.compareTo(pb.size)
    }
}
