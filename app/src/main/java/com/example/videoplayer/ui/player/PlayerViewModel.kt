package com.example.videoplayer.ui.player

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.videoplayer.VideoPlayerApp
import com.example.videoplayer.data.model.Video
import com.example.videoplayer.data.repository.LibraryRepository
import kotlin.math.abs
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PlayerUiState(
    val collectionId: Long = 0L,
    val collectionName: String = "",
    val playlist: List<Video> = emptyList(),
    val currentIndex: Int = 0,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val isPlaying: Boolean = false,
    val showList: Boolean = false,
    val isFullscreen: Boolean = false,
    val loading: Boolean = true,
    /** 当前视频是否为竖屏视频（宽 < 高），决定全屏方向 */
    val isPortraitVideo: Boolean = false,
    /** 当前播放倍速（1f 为正常速度） */
    val playbackSpeed: Float = 1f,
    /** 播放失败信息（非空表示当前媒体无法播放） */
    val error: String? = null
) {
    val currentVideo: Video? get() = playlist.getOrNull(currentIndex)

    val hasNext: Boolean get() = currentIndex < playlist.lastIndex
}

class PlayerViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = LibraryRepository(
        application,
        (application as VideoPlayerApp).database
    )

    private val _uiState = MutableStateFlow(PlayerUiState(collectionId = -1))
    val uiState: StateFlow<PlayerUiState> = _uiState.asStateFlow()

    /** 上次实际落库的进度，用于节流自动保存（P-6） */
    private var lastSavedPositionMs = 0L

    /**
     * 进入播放器：加载播放列表，定位到目标视频，恢复上次进度。
     */
    fun loadPlaylist(collectionId: Long, startVideoId: Long? = null, startPositionMs: Long = 0L) {
        viewModelScope.launch {
            val collection = repository.getCollection(collectionId) ?: return@launch
            val videos = repository.getVideos(collectionId)
            if (videos.isEmpty()) return@launch

            val startIndex = when {
                startVideoId != null -> videos.indexOfFirst { it.id == startVideoId }.takeIf { it >= 0 } ?: 0
                else -> videos.indexOfFirst { it.id == collection.lastVideoId }.takeIf { it >= 0 } ?: 0
            }

            // 仅补全当前视频的时长与封面，避免对整个视频集做逐帧提取
            videos.getOrNull(startIndex)?.let { video ->
                viewModelScope.launch {
                    repository.enrichVideo(video, Uri.parse(video.fileUri))
                }
            }

            // 续播位置：优先使用调用方指定的位置，否则取该视频上次播放位置
            // （忽略 <3s 的零碎进度，与桌面版逻辑一致）
            val resumePosition = startPositionMs.takeIf { it > 0 }
                ?: videos.getOrNull(startIndex)?.lastPosition?.takeIf { it > 3_000L }
                ?: 0L
            lastSavedPositionMs = resumePosition

            _uiState.update {
                it.copy(
                    collectionId = collectionId,
                    collectionName = collection.name,
                    playlist = videos,
                    currentIndex = startIndex,
                    positionMs = resumePosition,
                    loading = false,
                    error = null
                )
            }
        }
    }

    fun playIndex(index: Int) {
        // 先落库当前视频进度（S-1），再切换，避免丢失最后几秒
        savePositionNow()
        _uiState.update {
            it.copy(
                currentIndex = index,
                positionMs = 0L,
                loading = true,
                playbackSpeed = 1f, // 切换视频恢复常速
                error = null
            )
        }
        lastSavedPositionMs = 0L
    }

    /**
     * 设置播放倍速。SPEED_FAST 为长按加速时使用的倍速。
     */
    fun setPlaybackSpeed(speed: Float) {
        _uiState.update { it.copy(playbackSpeed = speed.coerceIn(0.25f, 8f)) }
    }

    /** 长按加速时的目标倍速 */
    companion object {
        const val SPEED_FAST = 2f
        const val SPEED_NORMAL = 1f
    }

    fun next() {
        val s = _uiState.value
        if (s.currentIndex < s.playlist.lastIndex) playIndex(s.currentIndex + 1)
    }

    fun previous() {
        val s = _uiState.value
        if (s.currentIndex > 0) playIndex(s.currentIndex - 1)
    }

    /**
     * 进度更新。位置变化不足 500ms 时不再发状态，抑制高频重组（P-3）。
     */
    fun onPositionUpdate(positionMs: Long, durationMs: Long, isPlaying: Boolean) {
        val s = _uiState.value
        val changed = s.loading ||
            isPlaying != s.isPlaying ||
            durationMs != s.durationMs ||
            abs(positionMs - s.positionMs) >= 500L
        if (!changed) return
        _uiState.update {
            it.copy(
                positionMs = positionMs,
                durationMs = durationMs,
                isPlaying = isPlaying,
                loading = false
            )
        }
    }

    fun onSeek(positionMs: Long) {
        _uiState.update { it.copy(positionMs = positionMs) }
    }

    /**
     * 播放失败回调（F-1）：给出明确提示，避免永久黑屏/转圈。
     */
    fun onPlaybackError(message: String?) {
        _uiState.update { it.copy(loading = false, error = message ?: "无法播放该视频") }
    }

    fun togglePlaylist() {
        val open = !_uiState.value.showList
        _uiState.update { it.copy(showList = open) }
        // 打开列表时刷新进度与时长，保证「已看/未看」与进度条准确
        if (open) refreshPlaylist()
    }

    /**
     * 刷新播放列表数据：补全缺失时长（限量）后重新读取，
     * 使列表中的观看进度可视化立即生效。
     */
    private fun refreshPlaylist() {
        viewModelScope.launch {
            val collectionId = _uiState.value.collectionId
            if (collectionId <= 0L) return@launch
            runCatching { repository.ensureDurations(collectionId, limit = 30) }
            val videos = runCatching { repository.getVideos(collectionId) }.getOrNull()
                ?: return@launch
            val currentId = _uiState.value.currentVideo?.id
            _uiState.update { s ->
                val idx = videos.indexOfFirst { it.id == currentId }
                    .takeIf { it >= 0 } ?: s.currentIndex
                s.copy(playlist = videos, currentIndex = idx)
            }
        }
    }

    fun closePlaylist() {
        _uiState.update { it.copy(showList = false) }
    }

    fun toggleFullscreen() {
        _uiState.update { it.copy(isFullscreen = !it.isFullscreen) }
    }

    /**
     * 更新当前视频尺寸，用于判断全屏时应采用横屏还是竖屏。
     */
    fun onVideoSizeChanged(width: Int, height: Int) {
        if (width <= 0 || height <= 0) return
        val portrait = height > width
        if (portrait != _uiState.value.isPortraitVideo) {
            _uiState.update { it.copy(isPortraitVideo = portrait) }
        }
    }

    /**
     * 播放状态变化回调（播放完成时自动下一集）。
     */
    fun onPlaybackEnded() {
        val s = _uiState.value
        if (s.hasNext) {
            next()
        } else {
            // 最后一集播放完毕，回到开头
            _uiState.update { it.copy(positionMs = 0L) }
            lastSavedPositionMs = 0L
        }
    }

    /**
     * 立即保存当前进度（切集 / 离开播放页 / 应用退后台时调用）。
     */
    fun savePositionNow() {
        doSave(_uiState.value.positionMs)
    }

    /**
     * 周期性自动保存：位移达到阈值才真正写库（P-6，减少写放大）。
     * 由 UI 每 5 秒调用一次。
     */
    fun autoSave() {
        val s = _uiState.value
        if (s.positionMs - lastSavedPositionMs >= 3_000L) {
            doSave(s.positionMs)
        }
    }

    private fun doSave(positionMs: Long) {
        val s = _uiState.value
        val video = s.currentVideo ?: return
        if (positionMs <= 0L) return
        lastSavedPositionMs = positionMs
        viewModelScope.launch {
            repository.savePlaybackPosition(s.collectionId, video.id, positionMs)
        }
    }
}
