package com.example.videoplayer.ui.home

import android.app.Application
import android.net.Uri
import android.os.Build
import android.os.Environment
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.videoplayer.VideoPlayerApp
import com.example.videoplayer.data.SettingsStore
import com.example.videoplayer.data.model.Group
import com.example.videoplayer.data.model.VideoCollection
import com.example.videoplayer.data.model.VideoSearchResult
import com.example.videoplayer.data.repository.LibraryRepository
import com.example.videoplayer.media.ConfirmedImport
import com.example.videoplayer.media.ImportDestination
import com.example.videoplayer.media.NameMerger
import com.example.videoplayer.media.PendingImport
import com.example.videoplayer.media.VideoImporter
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File

/** 首页网格中的一项：分组卡片（文件夹）或视频集卡片 */
sealed interface GridItem {
    val key: String

    /** 分组文件夹卡片 */
    data class GroupItem(
        val group: Group,
        val collectionCount: Int
    ) : GridItem {
        override val key: String get() = "g_${group.id}"
    }

    /** 视频集卡片 */
    data class CollectionItem(val collection: VideoCollection) : GridItem {
        override val key: String get() = "c_${collection.id}"
    }
}

data class HomeUiState(
    val groups: List<Group> = emptyList(),
    /** 当前所在分组层级，null = 根层（显示全部） */
    val currentGroupId: Long? = null,
    val currentGroupName: String? = null,
    /** 当前层级要展示的卡片 */
    val gridItems: List<GridItem> = emptyList(),
    /** 全部视频集（含所属分组），用于"分组封面选择"等场景 */
    val allCollections: List<VideoCollection> = emptyList(),
    val videoCounts: Map<Long, Int> = emptyMap(),
    val isImporting: Boolean = false,
    val isScanning: Boolean = false,
    val hasLibraryRoot: Boolean = false,
    val libraryRootLabel: String? = null,
    val useFileMode: Boolean = false,
    val message: String? = null,
    /** 是否显示首次启动引导 */
    val showSetup: Boolean = false,
    /** 待确认命名的导入项 */
    val pendingImports: List<PendingImport> = emptyList(),
    /** 智能归并建议（公共部分相同的文件） */
    val mergeGroups: List<NameMerger.MergeGroup> = emptyList(),
    /** 是否正在执行导入 */
    val isImportingFiles: Boolean = false,
    /** 各视频集「看到第几集」（collectionId → 集号，1 起） */
    val lastWatched: Map<Long, Int> = emptyMap(),
    /** 搜索关键词 */
    val searchQuery: String = "",
    /** 搜索命中的视频集 */
    val searchCollections: List<VideoCollection> = emptyList(),
    /** 搜索命中的视频文件 */
    val searchVideos: List<VideoSearchResult> = emptyList(),
    /** 是否处于搜索模式 */
    val isSearching: Boolean = false
)

class HomeViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as VideoPlayerApp
    private val settings: SettingsStore = app.settings
    private val repository = LibraryRepository(application, app.database)

    /** 媒体库根目录状态，合并为单个流以便参与组合 */
    private data class RootState(
        val hasRoot: Boolean = false,
        val label: String? = null,
        val fileMode: Boolean = false
    )

    /** 当前所在分组层级：null 表示根层 */
    private val currentGroupId = MutableStateFlow<Long?>(null)
    private val isImporting = MutableStateFlow(false)
    private val isScanning = MutableStateFlow(false)
    private val rootState = MutableStateFlow(RootState())
    private val message = MutableStateFlow<String?>(null)
    private val pendingImports = MutableStateFlow<List<PendingImport>>(emptyList())
    private val mergeGroups = MutableStateFlow<List<NameMerger.MergeGroup>>(emptyList())
    private val isImportingFiles = MutableStateFlow(false)
    private val setupVisible = MutableStateFlow(false)

    /** 搜索状态 */
    private val searchQuery = MutableStateFlow("")
    private val searchVideoResults = MutableStateFlow<List<VideoSearchResult>>(emptyList())
    private val isSearching = MutableStateFlow(false)
    private var searchJob: Job? = null

    private val importer = VideoImporter(application)

    val uiState: StateFlow<HomeUiState> = combine(
        repository.observeGroups(),
        currentGroupId,
        repository.observeCollections(),
        repository.observeVideoCounts(),
        rootState
    ) { groups, groupId, allCollections, counts, root ->
        // 根层：分组文件夹 + 未分组的视频集；分组层：该分组内的视频集
        val items: List<GridItem> = if (groupId == null) {
            groups.map { g ->
                GridItem.GroupItem(g, allCollections.count { it.groupId == g.id })
            } + allCollections.filter { it.groupId == null }.map { GridItem.CollectionItem(it) }
        } else {
            allCollections.filter { it.groupId == groupId }
                .map { GridItem.CollectionItem(it) }
        }

        HomeUiState(
            groups = groups,
            currentGroupId = groupId,
            currentGroupName = groups.firstOrNull { it.id == groupId }?.name,
            gridItems = items,
            allCollections = allCollections,
            videoCounts = counts.associate { it.collectionId to it.count },
            hasLibraryRoot = root.hasRoot,
            libraryRootLabel = root.label,
            useFileMode = root.fileMode
        )
    }
        .combine(isImporting) { state, importing -> state.copy(isImporting = importing) }
        .combine(isScanning) { state, scanning -> state.copy(isScanning = scanning) }
        .combine(message) { state, msg -> state.copy(message = msg) }
        .combine(pendingImports) { state, pending -> state.copy(pendingImports = pending) }
        .combine(mergeGroups) { state, groups -> state.copy(mergeGroups = groups) }
        .combine(isImportingFiles) { state, importing -> state.copy(isImportingFiles = importing) }
        .combine(setupVisible) { state, setup -> state.copy(showSetup = setup) }
        .combine(repository.observeLastWatched()) { state, watched ->
            state.copy(lastWatched = watched.associate { it.collectionId to it.sortOrder + 1 })
        }
        .combine(searchQuery) { state, q -> state.copy(searchQuery = q) }
        .combine(searchVideoResults) { state, videos ->
            // 视频集按名称在内存过滤（数量有限，避免额外查询）
            val matchedCollections = if (state.searchQuery.isBlank()) emptyList()
            else state.allCollections.filter {
                it.name.contains(state.searchQuery, ignoreCase = true)
            }
            state.copy(searchCollections = matchedCollections, searchVideos = videos)
        }
        .combine(isSearching) { state, searching -> state.copy(isSearching = searching) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    init {
        refreshRootState()
        setupVisible.value = shouldShowSetup()
        // 已设置媒体库目录时，启动自动扫描一次（与桌面版打开即可见的行为一致）
        if (settings.autoScanOnLaunch && rootState.value.hasRoot) {
            scanLibrary()
        }
    }

    // ───────── 搜索 ─────────

    /** 进入搜索模式 */
    fun openSearch() {
        isSearching.value = true
    }

    /** 退出搜索模式并清空关键词 */
    fun closeSearch() {
        searchJob?.cancel()
        isSearching.value = false
        searchQuery.value = ""
        searchVideoResults.value = emptyList()
    }

    /**
     * 实时搜索：视频集名走内存过滤，视频文件名走数据库模糊匹配（防抖 250ms）。
     */
    fun setSearchQuery(query: String) {
        searchQuery.value = query
        searchJob?.cancel()
        if (query.isBlank()) {
            searchVideoResults.value = emptyList()
            return
        }
        searchJob = viewModelScope.launch {
            delay(250)
            val results = runCatching { repository.searchVideos(query) }.getOrDefault(emptyList())
            // 关键词已变化则丢弃过期结果
            if (searchQuery.value == query) searchVideoResults.value = results
        }
    }

    /** 是否处于根层（未进入任何分组） */
    fun isAtRoot(): Boolean = currentGroupId.value == null

    /** 是否显示首次启动引导 */
    private fun shouldShowSetup(): Boolean =
        !settings.setupCompleted && !rootState.value.hasRoot

    /** 跳过引导（之后不再自动弹出） */
    fun skipSetup() {
        settings.setupCompleted = true
        setupVisible.value = false
    }

    /** 引导流程完成（已创建或已选择目录） */
    private fun completeSetup() {
        settings.setupCompleted = true
        setupVisible.value = false
    }

    /** 同步当前媒体库根目录状态 */
    fun refreshRootState() {
        val fileMode = settings.useFileMode
        val uri = settings.libraryRootUri
        val path = settings.libraryRootPath
        rootState.value = RootState(
            hasRoot = if (fileMode) !path.isNullOrBlank() else !uri.isNullOrBlank(),
            label = settings.currentRootLabel(),
            fileMode = fileMode
        )
    }

    /** 是否具备"所有文件访问"权限（API 30+ 才有此能力） */
    fun hasAllFilesAccess(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && Environment.isExternalStorageManager()

    /**
     * 进入分组（逐层打开，类似进入文件夹）。
     */
    fun enterGroup(groupId: Long) {
        currentGroupId.value = groupId
    }

    /**
     * 返回上一层（回到全部）。
     */
    fun exitGroup() {
        currentGroupId.value = null
    }

    // ───────── 媒体库根目录 ─────────

    /**
     * 设置媒体库根目录（SAF 模式）。授权一次后可持续访问。
     */
    fun setLibraryRoot(uri: Uri) {
        viewModelScope.launch {
            runCatching {
                getApplication<Application>().contentResolver.takePersistableUriPermission(
                    uri,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
            settings.useFileMode = false
            settings.libraryRootUri = uri.toString()
            refreshRootState()
            completeSetup()
            scanLibrary()
        }
    }

    /**
     * 启用文件路径模式并创建默认媒体库目录。
     * 需要"所有文件访问"权限，行为与桌面版一致。
     */
    fun enableFileMode() {
        val target = defaultPublicMediaDir()
            ?: File(
                getApplication<Application>().getExternalFilesDir(null),
                SettingsStore.DEFAULT_FOLDER_NAME
            ).apply { mkdirs() }

        settings.useFileMode = true
        settings.libraryRootPath = target.absolutePath
        refreshRootState()
        completeSetup()
        scanLibrary()
    }

    /**
     * 公共媒体目录下的默认媒体库目录（Movies/视频集播放器）。
     * 需要「所有文件访问」权限才能写入，失败时返回 null。
     */
    private fun defaultPublicMediaDir(): File? = runCatching {
        val movies = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES)
        File(movies, SettingsStore.DEFAULT_FOLDER_NAME).takeIf { it.mkdirs() || it.isDirectory }
    }.getOrNull()

    /** 文件路径模式下手动指定目录 */
    fun setLibraryPath(path: String) {
        settings.useFileMode = true
        settings.libraryRootPath = path
        refreshRootState()
        scanLibrary()
    }

    /**
     * 扫描媒体库根目录：子目录自动成为视频集。
     */
    fun scanLibrary() {
        viewModelScope.launch {
            val fileMode = settings.useFileMode
            val path = settings.libraryRootPath?.takeIf { it.isNotBlank() }
            val uri = settings.libraryRootUri?.takeIf { it.isNotBlank() }

            // 校验根目录与权限，取出非空值
            val targetPath: String?
            val targetUri: Uri?
            if (fileMode) {
                if (path == null) return@launch
                if (!hasAllFilesAccess()) {
                    message.value = "需要「所有文件访问」权限才能读取该目录"
                    return@launch
                }
                targetPath = path
                targetUri = null
            } else {
                if (uri == null) return@launch
                targetPath = null
                targetUri = Uri.parse(uri)
            }

            isScanning.value = true
            val result = runCatching {
                if (targetPath != null) repository.scanLibraryPath(targetPath)
                else repository.scanLibraryRoot(requireNotNull(targetUri))
            }
            isScanning.value = false

            result.onSuccess { res ->
                message.value = if (res.collections > 0) {
                    "扫描完成：${res.collections} 个视频集 / ${res.videos} 个视频"
                } else {
                    "未找到视频，请把视频按「媒体库/视频集名/视频文件」的层级放置"
                }
            }.onFailure {
                message.value = "扫描失败：${it.message ?: "无法访问目录"}"
            }
        }
    }

    /**
     * 导入单个文件夹为视频集。
     */
    fun importFolder(uri: Uri) {
        viewModelScope.launch {
            isImporting.value = true
            runCatching {
                getApplication<Application>().contentResolver.takePersistableUriPermission(
                    uri,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
                repository.importFolder(uri)
            }.onFailure {
                message.value = "导入失败：${it.message ?: "无法访问目录"}"
            }
            isImporting.value = false
        }
    }

    fun clearMessage() {
        message.value = null
    }

    // ───────── 导入文件 / 压缩包 ─────────

    /**
     * 分析选中的文件（视频或压缩包），弹出命名确认对话框。
     */
    fun prepareImport(uris: List<Uri>) {
        if (uris.isEmpty()) return
        if (!rootState.value.hasRoot) {
            message.value = "请先设置媒体库目录"
            return
        }
        viewModelScope.launch {
            val pending = importer.analyze(uris)
            if (pending.isEmpty()) {
                message.value = "未识别到视频或压缩包（支持 zip / rar）"
                return@launch
            }
            // 智能归并：提取公共部分，识别季/部等分开标识
            mergeGroups.value = NameMerger.suggest(pending.map { it.suggestedName })
            pendingImports.value = pending
        }
    }

    /** 取消导入 */
    fun cancelImport() {
        pendingImports.value = emptyList()
        mergeGroups.value = emptyList()
    }

    /**
     * 确认导入：按「媒体库 / 视频集名称 / 视频文件」规则归类存放。
     */
    fun confirmImport(names: List<String>) {
        val pending = pendingImports.value
        if (pending.isEmpty()) return
        val destination = currentDestination() ?: run {
            message.value = "媒体库目录不可用，请重新设置"
            return
        }

        val items = pending.mapIndexed { index, item ->
            ConfirmedImport(
                pending = item,
                collectionName = names.getOrNull(index)?.trim().orEmpty()
                    .ifBlank { item.suggestedName }
            )
        }
        pendingImports.value = emptyList()
        mergeGroups.value = emptyList()

        viewModelScope.launch {
            isImportingFiles.value = true
            val result = runCatching { importer.import(items, destination) }
            isImportingFiles.value = false

            result.onSuccess { res ->
                message.value = when {
                    res.importedVideos > 0 ->
                        "导入完成：${res.createdCollections} 个视频集 / ${res.importedVideos} 个视频"
                    res.failures.isNotEmpty() ->
                        "导入未成功：${res.failures.take(2).joinToString("；")}"
                    else -> "未导入任何视频"
                }
                // 重新扫描以入库
                scanLibrary()
            }.onFailure {
                message.value = "导入失败：${it.message ?: "无法写入目录"}"
            }
        }
    }

    /** 当前媒体库根目录对应的导入目标 */
    private fun currentDestination(): ImportDestination? {
        return if (settings.useFileMode) {
            settings.libraryRootPath?.takeIf { it.isNotBlank() }
                ?.let { ImportDestination.FilePath(it) }
        } else {
            settings.libraryRootUri?.takeIf { it.isNotBlank() }
                ?.let { ImportDestination.SafTree(Uri.parse(it)) }
        }
    }

    fun createGroup(name: String) {
        viewModelScope.launch { repository.createGroup(name) }
    }

    fun renameGroup(groupId: Long, newName: String) {
        viewModelScope.launch { repository.renameGroup(groupId, newName) }
    }

    /**
     * 设置分组封面（传组内视频集的封面，null 为清除）。
     */
    fun setGroupCover(groupId: Long, coverUri: String?) {
        viewModelScope.launch { repository.setGroupCover(groupId, coverUri) }
    }

    fun deleteGroup(groupId: Long) {
        viewModelScope.launch {
            repository.deleteGroup(groupId)
            // 删除的正是当前所在分组时，退回根层
            if (currentGroupId.value == groupId) currentGroupId.value = null
        }
    }

    fun renameCollection(collectionId: Long, newName: String) {
        viewModelScope.launch { repository.renameCollection(collectionId, newName) }
    }

    fun moveToGroup(collectionId: Long, groupId: Long?) {
        viewModelScope.launch { repository.moveToGroup(collectionId, groupId) }
    }

    fun deleteCollection(collectionId: Long) {
        viewModelScope.launch { repository.deleteCollection(collectionId) }
    }
}
