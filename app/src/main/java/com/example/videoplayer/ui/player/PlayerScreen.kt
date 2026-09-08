package com.example.videoplayer.ui.player

import android.app.Activity
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.net.Uri
import android.view.MotionEvent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.example.videoplayer.data.model.Video
import com.example.videoplayer.ui.components.FormatUtils
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * 播放器界面：对应桌面版 PlayerWindow + PlayerToolbar。
 * 支持播放列表、上下集、进度记忆、音量、全屏。
 */
@OptIn(ExperimentalMaterial3Api::class, UnstableApi::class)
@Composable
fun PlayerScreen(
    collectionId: Long,
    startVideoId: Long?,
    startPositionMs: Long,
    onBack: () -> Unit,
    viewModel: PlayerViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // 创建 ExoPlayer（对应桌面版 QtMultimedia 播放器）
    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            playWhenReady = true
            repeatMode = Player.REPEAT_MODE_OFF
        }
    }

    // 释放播放器
    DisposableEffect(exoPlayer) {
        onDispose {
            viewModel.savePositionNow()
            exoPlayer.release()
        }
    }

    // 生命周期管理：后台暂停，前台继续
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> {
                    viewModel.savePositionNow()
                    exoPlayer.pause()
                }
                Lifecycle.Event.ON_STOP -> exoPlayer.pause()
                Lifecycle.Event.ON_RESUME -> {
                    if (state.isPlaying) exoPlayer.play()
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // 首次加载播放列表
    LaunchedEffect(collectionId) {
        viewModel.loadPlaylist(collectionId, startVideoId, startPositionMs)
    }

    // 当前视频切换时装载媒体
    val currentVideo = state.currentVideo
    LaunchedEffect(currentVideo?.id) {
        val video = currentVideo ?: return@LaunchedEffect
        exoPlayer.setMediaItem(MediaItem.fromUri(Uri.parse(video.fileUri)))
        if (state.positionMs > 0) exoPlayer.seekTo(state.positionMs)
        exoPlayer.prepare()
        exoPlayer.playWhenReady = true
    }

    // 播放进度轮询（1 秒一次；VM 内再按 500ms 变化阈值去抖）
    LaunchedEffect(exoPlayer, currentVideo?.id) {
        while (true) {
            delay(1_000)
            val duration = exoPlayer.duration.takeIf { it > 0 } ?: 0L
            viewModel.onPositionUpdate(
                positionMs = exoPlayer.currentPosition,
                durationMs = duration,
                isPlaying = exoPlayer.isPlaying
            )
        }
    }

    // 播放进度定期落库（每 5 秒探测一次，位移达阈值才真正写库）
    LaunchedEffect(currentVideo?.id) {
        while (true) {
            delay(5_000)
            viewModel.autoSave()
        }
    }

    // 播放完成自动下一集 + 监听错误/视频尺寸
    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) {
                    viewModel.onPlaybackEnded()
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                viewModel.onPlaybackError(error.errorCodeName + ": " + (error.message ?: "未知错误"))
            }

            override fun onVideoSizeChanged(videoSize: androidx.media3.common.VideoSize) {
                viewModel.onVideoSizeChanged(videoSize.width, videoSize.height)
            }
        }
        exoPlayer.addListener(listener)
        onDispose { exoPlayer.removeListener(listener) }
    }

    // 倍速联动
    LaunchedEffect(state.playbackSpeed) {
        exoPlayer.playbackParameters =
            androidx.media3.common.PlaybackParameters(state.playbackSpeed)
    }

    // 全屏：隐藏/显示系统栏
    LaunchedEffect(state.isFullscreen) {
        val window = (context as Activity).window
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        if (state.isFullscreen) {
            controller.hide(WindowInsetsCompat.Type.systemBars())
            controller.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        } else {
            controller.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    // 全屏：强制切换屏幕方向。
    // 通过 requestedOrientation 直接指定方向，可覆盖系统「自动旋转」关闭的限制。
    // 用 SENSOR_LANDSCAPE / SENSOR_PORTRAIT 支持正反两个方向。
    LaunchedEffect(state.isFullscreen, state.isPortraitVideo) {
        val activity = context as? Activity ?: return@LaunchedEffect
        activity.requestedOrientation = if (state.isFullscreen) {
            if (state.isPortraitVideo) {
                ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
            } else {
                ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            }
        } else {
            // 退出全屏交还系统控制（自动旋转关闭时会回到进入前的方向）
            ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }

        // 旋转动画期间系统栏可能重新出现，稍后再次隐藏
        if (state.isFullscreen) {
            delay(300)
            val controller = WindowCompat.getInsetsController(
                activity.window, activity.window.decorView
            )
            controller.hide(WindowInsetsCompat.Type.systemBars())
        }
    }

    // 离开播放页时恢复方向跟随系统，避免首页被锁在横屏
    DisposableEffect(Unit) {
        onDispose {
            (context as? Activity)?.requestedOrientation =
                ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    // 全屏状态下按返回键优先退出全屏，而非直接返回首页
    BackHandler(enabled = state.isFullscreen) {
        viewModel.toggleFullscreen()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // 视频画面
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    useController = false
                    resizeMode = androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT
                    setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
                    this.player = exoPlayer
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // 播放器控制层
        if (currentVideo != null) {
            PlayerControls(
                state = state,
                onBack = onBack,
                onTogglePlay = {
                    if (exoPlayer.isPlaying) exoPlayer.pause() else exoPlayer.play()
                },
                onSeek = { positionMs ->
                    exoPlayer.seekTo(positionMs)
                    viewModel.onPositionUpdate(
                        positionMs, exoPlayer.duration.takeIf { it > 0 } ?: 0L,
                        exoPlayer.isPlaying
                    )
                },
                onPrevious = viewModel::previous,
                onNext = viewModel::next,
                onTogglePlaylist = viewModel::togglePlaylist,
                onToggleFullscreen = viewModel::toggleFullscreen,
                onClosePlaylist = viewModel::closePlaylist,
                onSelectVideo = viewModel::playIndex,
                onSavePosition = viewModel::savePositionNow,
                onSpeedChange = viewModel::setPlaybackSpeed
            )
        }

        // 加载中指示
        if (state.loading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                androidx.compose.material3.CircularProgressIndicator(color = Color.White)
            }
        }

        // 播放失败提示（F-1）：给出明确信息与操作，避免永久黑屏
        state.error?.let { message ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.82f)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.ErrorOutline,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(56.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "无法播放该视频",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.7f),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 32.dp)
                    )
                    Spacer(modifier = Modifier.height(20.dp))
                    if (state.hasNext) {
                        Button(onClick = { viewModel.next() }) {
                            Text("播放下一集")
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                    TextButton(onClick = onBack) {
                        Text("返回", color = Color.White)
                    }
                }
            }
        }
    }
}

/** 长按判定为长按的时长（与系统长按超时一致） */
private const val LONG_PRESS_TIMEOUT_MS = 400L

/** 触摸移动容差（px），超出则不视为点击/长按 */
private const val TOUCH_SLOP = 24f

/** 加速区占比：竖屏取下方 1/4，横屏取右侧 1/4 */
private const val FAST_ZONE_RATIO = 0.75f

/**
 * 判断触摸点是否落在快进区域。
 * - 竖屏：屏幕从上往下 3/4 以下（下方区域）
 * - 横屏：屏幕右侧区域
 */
private fun inFastZone(
    x: Float,
    y: Float,
    width: Float,
    height: Float,
    landscape: Boolean
): Boolean {
    if (width <= 0f || height <= 0f) return false
    return if (landscape) {
        x >= width * FAST_ZONE_RATIO
    } else {
        y >= height * FAST_ZONE_RATIO
    }
}

/** 倍速格式化：2.0 → "2"，1.5 → "1.5" */
private fun formatSpeed(speed: Float): String {
    val rounded = (speed * 100).roundToInt() / 100f
    return if (rounded % 1f == 0f) rounded.toInt().toString() else rounded.toString()
}

/**
 * 播放器控制层。
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalComposeUiApi::class)
@Composable
private fun BoxScope.PlayerControls(
    state: PlayerUiState,
    onBack: () -> Unit,
    onTogglePlay: () -> Unit,
    onSeek: (Long) -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onTogglePlaylist: () -> Unit,
    onToggleFullscreen: () -> Unit,
    onClosePlaylist: () -> Unit,
    onSelectVideo: (Int) -> Unit,
    onSavePosition: () -> Unit,
    onSpeedChange: (Float) -> Unit
) {
    var showControls by remember { mutableStateOf(true) }

    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val view = LocalView.current
    val scope = rememberCoroutineScope()

    // 控制栏尺寸：横屏时屏幕高度有限，压缩控制栏避免遮挡画面
    val barPaddingV = if (isLandscape) 2.dp else 8.dp
    val sliderHeight = if (isLandscape) 22.dp else 40.dp
    val playBtn = if (isLandscape) 36.dp else 56.dp
    val playIcon = if (isLandscape) 30.dp else 48.dp
    val skipBtn = if (isLandscape) 32.dp else 44.dp
    val skipIcon = if (isLandscape) 24.dp else 32.dp
    val smallBtn = if (isLandscape) 30.dp else 40.dp
    val smallIcon = if (isLandscape) 20.dp else 28.dp

    // 手势区域尺寸（用于判断长按落在哪个区域）
    var areaSize by remember { mutableStateOf(IntSize.Zero) }
    // 按下起点，用于判断是否为点击（移动超过容差则不算）
    var pressStart by remember { mutableStateOf<Offset?>(null) }
    // 长按加速是否已激活
    var fastActive by remember { mutableStateOf(false) }
    var longPressJob by remember { mutableStateOf<Job?>(null) }

    // 松手或被取消时结束加速
    val stopFast = {
        longPressJob?.cancel()
        longPressJob = null
        if (fastActive) {
            fastActive = false
            onSpeedChange(PlayerViewModel.SPEED_NORMAL)
        }
        Unit
    }

    // 离开播放页时确保恢复常速（直接调用回调，避免捕获过期的状态）
    DisposableEffect(Unit) {
        onDispose { onSpeedChange(PlayerViewModel.SPEED_NORMAL) }
    }

    // 中央手势区：点击切换控制栏，指定区域长按触发倍速
    Box(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { areaSize = it }
            .pointerInteropFilter { event ->
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        if (event.pointerCount == 1) {
                            pressStart = Offset(event.x, event.y)
                            val start = pressStart
                            longPressJob = scope.launch {
                                delay(LONG_PRESS_TIMEOUT_MS)
                                // 长按达成：只有落在加速区才生效
                                if (start != null && inFastZone(
                                        x = start.x,
                                        y = start.y,
                                        width = areaSize.width.toFloat(),
                                        height = areaSize.height.toFloat(),
                                        landscape = isLandscape
                                    )
                                ) {
                                    fastActive = true
                                    view.performHapticFeedback(
                                        android.view.HapticFeedbackConstants.LONG_PRESS
                                    )
                                    onSpeedChange(PlayerViewModel.SPEED_FAST)
                                }
                            }
                        }
                        true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val start = pressStart
                        if (start != null && !fastActive) {
                            val dx = event.x - start.x
                            val dy = event.y - start.y
                            // 移动超出容差则取消长按候选
                            if (dx * dx + dy * dy > TOUCH_SLOP * TOUCH_SLOP) {
                                longPressJob?.cancel()
                                longPressJob = null
                            }
                        }
                        true
                    }
                    MotionEvent.ACTION_UP -> {
                        val start = pressStart
                        val wasFast = fastActive
                        longPressJob?.cancel()
                        longPressJob = null
                        if (wasFast) {
                            fastActive = false
                            onSpeedChange(PlayerViewModel.SPEED_NORMAL)
                        } else if (start != null) {
                            // 未触发长按且位移很小 → 视为点击
                            val dx = event.x - start.x
                            val dy = event.y - start.y
                            if (dx * dx + dy * dy <= TOUCH_SLOP * TOUCH_SLOP) {
                                showControls = !showControls
                            }
                        }
                        pressStart = null
                        true
                    }
                    MotionEvent.ACTION_CANCEL -> {
                        stopFast()
                        pressStart = null
                        true
                    }
                    else -> true
                }
            }
    )

    // 顶部栏：返回 + 标题
    if (showControls) {
        Row(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.6f))
                .statusBarsPadding()
                .padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { onSavePosition(); onBack() }) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "返回",
                    tint = Color.White
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = state.collectionName,
                    style = MaterialTheme.typography.titleSmall,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = state.currentVideo?.fileName ?: "",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.7f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text(
                text = "${state.currentIndex + 1} / ${state.playlist.size}",
                style = MaterialTheme.typography.labelMedium,
                color = Color.White.copy(alpha = 0.8f),
                modifier = Modifier.padding(horizontal = 12.dp)
            )
        }
    }

    // 底部控制栏
    if (showControls) {
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.6f))
                .navigationBarsPadding()
                .padding(horizontal = 8.dp, vertical = barPaddingV)
        ) {
            // 进度条 + 时间
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = FormatUtils.formatDuration(state.positionMs),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White
                )
                Slider(
                    value = state.positionMs.toFloat().coerceIn(0f, state.durationMs.toFloat().coerceAtLeast(1f)),
                    onValueChange = { onSeek(it.toLong()) },
                    valueRange = 0f..state.durationMs.toFloat().coerceAtLeast(1f),
                    modifier = Modifier
                        .weight(1f)
                        .height(sliderHeight)
                        .padding(horizontal = if (isLandscape) 4.dp else 8.dp),
                    enabled = state.durationMs > 0
                )
                Text(
                    text = FormatUtils.formatDuration(state.durationMs),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White
                )
            }

            // 控制按钮行
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                ControlButton(
                    onClick = onPrevious,
                    icon = Icons.Default.SkipPrevious,
                    contentDescription = "上一集",
                    buttonSize = skipBtn,
                    iconSize = skipIcon
                )
                ControlButton(
                    onClick = onTogglePlay,
                    icon = if (state.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (state.isPlaying) "暂停" else "播放",
                    buttonSize = playBtn,
                    iconSize = playIcon
                )
                ControlButton(
                    onClick = onNext,
                    icon = Icons.Default.SkipNext,
                    contentDescription = "下一集",
                    buttonSize = skipBtn,
                    iconSize = skipIcon
                )
                ControlButton(
                    onClick = onTogglePlaylist,
                    icon = Icons.AutoMirrored.Filled.PlaylistPlay,
                    contentDescription = "播放列表",
                    buttonSize = smallBtn,
                    iconSize = smallIcon
                )
                ControlButton(
                    onClick = onToggleFullscreen,
                    icon = if (state.isFullscreen) Icons.Default.FullscreenExit
                    else Icons.Default.Fullscreen,
                    contentDescription = "全屏",
                    buttonSize = smallBtn,
                    iconSize = smallIcon
                )
            }
        }
    }

    // 倍速悬浮提示：放在最后声明以确保绘制在最上层；
    // 并根据控制栏是否可见自动下移，避免被顶部栏遮挡。
    if (state.playbackSpeed != PlayerViewModel.SPEED_NORMAL) {
        val topPadding = if (showControls) 72.dp else 12.dp
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(top = topPadding)
        ) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = Color.Black.copy(alpha = 0.72f),
                contentColor = Color.White,
                shadowElevation = 4.dp
            ) {
                Text(
                    text = "»${formatSpeed(state.playbackSpeed)}倍速",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                )
            }
        }
    }

    // 播放列表抽屉
    if (state.showList) {
        ModalBottomSheet(
            onDismissRequest = onClosePlaylist,
            sheetState = rememberModalBottomSheetState()
        ) {
            PlaylistSheet(
                playlist = state.playlist,
                currentIndex = state.currentIndex,
                onSelect = onSelectVideo,
                onDismiss = onClosePlaylist
            )
        }
    }
}

/**
 * 控制栏按钮。
 *
 * 不使用 IconButton —— 它有 48dp 最小触摸尺寸限制，横屏时无法压缩，
 * 会挤占大量画面高度。这里自行控制尺寸，横屏时更紧凑。
 */
@Composable
private fun ControlButton(
    onClick: () -> Unit,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    buttonSize: androidx.compose.ui.unit.Dp,
    iconSize: androidx.compose.ui.unit.Dp,
    tint: Color = Color.White
) {
    Box(
        modifier = Modifier
            .size(buttonSize)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(iconSize)
        )
    }
}

/**
 * 播放列表弹层。
 */
@Composable
private fun PlaylistSheet(
    playlist: List<Video>,
    currentIndex: Int,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    Column(modifier = Modifier.height(420.dp)) {
        Text(
            text = "播放列表（${playlist.size}）",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
        )
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            itemsIndexed(playlist, key = { _, video -> video.id }) { index, video ->
                val isCurrent = index == currentIndex
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            onSelect(index)
                            onDismiss()
                        }
                        .background(
                            if (isCurrent) {
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                            } else {
                                Color.Transparent
                            }
                        )
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (isCurrent) Icons.Default.PlayArrow else Icons.AutoMirrored.Filled.PlaylistPlay,
                        contentDescription = null,
                        tint = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = video.fileName,
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (isCurrent) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (video.lastPosition > 0) {
                            Text(
                                text = "上次播放到 ${FormatUtils.formatDuration(video.lastPosition)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}
