package com.example.videoplayer.data

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

/**
 * 轻量设置存储：媒体库根目录等。
 */
class SettingsStore(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** SAF 模式下授权的媒体库根目录 tree Uri */
    var libraryRootUri: String?
        get() = prefs.getString(KEY_ROOT_URI, null)
        set(value) = prefs.edit { putString(KEY_ROOT_URI, value) }

    /** 文件路径模式下的媒体库根目录绝对路径 */
    var libraryRootPath: String?
        get() = prefs.getString(KEY_ROOT_PATH, null)
        set(value) = prefs.edit { putString(KEY_ROOT_PATH, value) }

    /** 是否使用文件路径模式（需要「所有文件访问」权限） */
    var useFileMode: Boolean
        get() = prefs.getBoolean(KEY_FILE_MODE, false)
        set(value) = prefs.edit { putBoolean(KEY_FILE_MODE, value) }

    /** 启动时自动扫描媒体库 */
    var autoScanOnLaunch: Boolean
        get() = prefs.getBoolean(KEY_AUTO_SCAN, true)
        set(value) = prefs.edit { putBoolean(KEY_AUTO_SCAN, value) }

    /** 是否已完成首次启动引导（创建或选择过媒体库目录，或主动跳过） */
    var setupCompleted: Boolean
        get() = prefs.getBoolean(KEY_SETUP_DONE, false)
        set(value) = prefs.edit { putBoolean(KEY_SETUP_DONE, value) }

    fun clearLibraryRoot() {
        prefs.edit {
            remove(KEY_ROOT_URI)
            remove(KEY_ROOT_PATH)
        }
    }

    /** 当前生效的媒体库根目录描述（用于界面展示） */
    fun currentRootLabel(): String? =
        if (useFileMode) libraryRootPath else libraryRootUri?.let { uri ->
            val decoded = android.net.Uri.decode(uri)
            decoded.substringAfterLast(':', decoded)
        }

    companion object {
        private const val PREFS_NAME = "video_player_settings"
        private const val KEY_ROOT_URI = "library_root_uri"
        private const val KEY_ROOT_PATH = "library_root_path"
        private const val KEY_FILE_MODE = "use_file_mode"
        private const val KEY_AUTO_SCAN = "auto_scan_on_launch"
        private const val KEY_SETUP_DONE = "setup_completed"

        /** 文件路径模式下的默认媒体库目录名 */
        const val DEFAULT_FOLDER_NAME = "视频集播放器"
    }
}
