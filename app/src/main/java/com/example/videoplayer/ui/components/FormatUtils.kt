package com.example.videoplayer.ui.components

import java.util.Locale

/**
 * 时间与文件大小格式化工具。
 */
object FormatUtils {

    /**
     * 毫秒转 mm:ss / h:mm:ss。
     */
    fun formatDuration(ms: Long): String {
        if (ms <= 0) return "00:00"
        val totalSeconds = ms / 1000
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return if (hours > 0) {
            String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format(Locale.US, "%02d:%02d", minutes, seconds)
        }
    }
}
