package com.example.videoplayer.ui.home

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DriveFolderUpload
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.videoplayer.data.model.Group
import com.example.videoplayer.data.model.VideoCollection
import com.example.videoplayer.ui.components.CollectionCard
import com.example.videoplayer.ui.components.EmptyState
import com.example.videoplayer.ui.components.GroupCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onOpenCollection: (collectionId: Long, startVideoId: Long?, startPositionMs: Long) -> Unit,
    viewModel: HomeViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val snackbarHostState = remember { SnackbarHostState() }

    // 对话框状态
    var showCreateGroup by remember { mutableStateOf(false) }
    var editingGroup by remember { mutableStateOf<Group?>(null) }
    var actionCollection by remember { mutableStateOf<VideoCollection?>(null) }
    var showRenameCollection by remember { mutableStateOf(false) }
    var showMoveCollection by remember { mutableStateOf(false) }
    var showDeleteCollection by remember { mutableStateOf(false) }
    var showDeleteGroup by remember { mutableStateOf<Group?>(null) }
    var showLibrarySettings by remember { mutableStateOf(false) }
    var showImportMenu by remember { mutableStateOf(false) }
    var showMoreMenu by remember { mutableStateOf(false) }
    /** 长按的分组（重命名 / 删除） */
    var actionGroup by remember { mutableStateOf<Group?>(null) }
    /** 设置封面目标分组 */
    var coverTarget by remember { mutableStateOf<Group?>(null) }

    // 提示消息
    LaunchedEffect(uiState.message) {
        uiState.message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessage()
        }
    }

    // 从授权页返回时刷新权限状态
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.refreshRootState()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // SAF 文件夹选择器：设置媒体库根目录 / 单独导入
    val rootPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) viewModel.setLibraryRoot(uri)
    }

    // 多文件选择器：一次导入多个视频或压缩包
    val filesPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) viewModel.prepareImport(uris)
    }

    val folderPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) viewModel.importFolder(uri)
    }

    // 所有文件访问授权
    val allFilesLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (viewModel.hasAllFilesAccess()) viewModel.enableFileMode()
    }

    // 首次启动引导：询问是否自动创建视频存放文件夹
    if (uiState.showSetup) {
        LibrarySetupScreen(
            hasAllFilesAccess = viewModel.hasAllFilesAccess(),
            onCreateFolder = { viewModel.enableFileMode() },
            onPickFolder = { rootPicker.launch(null) },
            onGrantAllFiles = {
                runCatching {
                    allFilesLauncher.launch(buildAllFilesAccessIntent(context))
                }
            },
            onSkip = { viewModel.skipSetup() }
        )
        return
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    if (uiState.isSearching) {
                        TextField(
                            value = uiState.searchQuery,
                            onValueChange = viewModel::setSearchQuery,
                            placeholder = { Text("搜索视频集 / 视频文件") },
                            singleLine = true,
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else {
                        Column {
                            Text("视频集")
                            uiState.libraryRootLabel?.let { label ->
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                },
                navigationIcon = {
                    if (uiState.isSearching) {
                        IconButton(onClick = { viewModel.closeSearch() }) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "退出搜索"
                            )
                        }
                    }
                },
                actions = {
                    if (uiState.isSearching) {
                        if (uiState.searchQuery.isNotEmpty()) {
                            IconButton(onClick = { viewModel.setSearchQuery("") }) {
                                Icon(Icons.Default.Close, contentDescription = "清除")
                            }
                        }
                        return@TopAppBar
                    }
                    IconButton(onClick = { viewModel.openSearch() }) {
                        Icon(Icons.Default.Search, contentDescription = "搜索")
                    }
                    // 导入：视频文件 / 压缩包 / 文件夹
                    IconButton(onClick = { showImportMenu = true }) {
                        Icon(Icons.Default.Download, contentDescription = "导入")
                    }
                    DropdownMenu(
                        expanded = showImportMenu,
                        onDismissRequest = { showImportMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("导入视频文件 / 压缩包") },
                            leadingIcon = {
                                Icon(Icons.Default.Movie, contentDescription = null)
                            },
                            onClick = {
                                showImportMenu = false
                                filesPicker.launch(arrayOf("*/*"))
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("导入文件夹") },
                            leadingIcon = {
                                Icon(Icons.Default.DriveFolderUpload, contentDescription = null)
                            },
                            onClick = {
                                showImportMenu = false
                                folderPicker.launch(null)
                            }
                        )
                    }
                    IconButton(
                        onClick = { viewModel.scanLibrary() },
                        enabled = !uiState.isScanning
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = "扫描媒体库")
                    }
                    IconButton(onClick = { showLibrarySettings = true }) {
                        Icon(Icons.Default.FolderOpen, contentDescription = "媒体库设置")
                    }
                    IconButton(onClick = { showMoreMenu = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "更多")
                    }
                    DropdownMenu(
                        expanded = showMoreMenu,
                        onDismissRequest = { showMoreMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("新建分组") },
                            leadingIcon = {
                                Icon(Icons.Default.CreateNewFolder, contentDescription = null)
                            },
                            onClick = {
                                showMoreMenu = false
                                showCreateGroup = true
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("重新扫描") },
                            leadingIcon = { Icon(Icons.Default.Refresh, contentDescription = null) },
                            onClick = {
                                showMoreMenu = false
                                viewModel.scanLibrary()
                            }
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { folderPicker.launch(null) },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text(if (uiState.isImporting) "导入中…" else "导入文件夹") },
                expanded = !uiState.isImporting
            )
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding)) {
            if (uiState.isSearching) {
                SearchResultList(
                    query = uiState.searchQuery,
                    collections = uiState.searchCollections,
                    videos = uiState.searchVideos,
                    videoCounts = uiState.videoCounts,
                    lastWatched = uiState.lastWatched,
                    onOpenCollection = { id -> onOpenCollection(id, null, 0L) },
                    onOpenVideo = { video -> onOpenCollection(video.collectionId, video.id, 0L) }
                )
            } else if (uiState.gridItems.isEmpty()) {
                EmptyState(
                    title = if (uiState.currentGroupId != null) "该分组还没有视频集" else "还没有视频集",
                    subtitle = if (uiState.hasLibraryRoot) {
                        "把视频按「媒体库/视频集名/视频文件」的层级放入，\n然后点击右上角刷新按钮扫描"
                    } else {
                        "点击右下角「导入文件夹」\n选择存放视频的目录开始使用"
                    }
                )
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 160.dp),
                    contentPadding = PaddingValues(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // 分组层顶部显示面包屑：← 返回全部 / 分组名
                    if (uiState.currentGroupId != null) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            GroupBreadcrumb(
                                groupName = uiState.currentGroupName ?: "分组",
                                onBack = viewModel::exitGroup
                            )
                        }
                    }
                    items(uiState.gridItems, key = { it.key }) { item ->
                        when (item) {
                            is GridItem.GroupItem -> GroupCard(
                                group = item.group,
                                collectionCount = item.collectionCount,
                                onClick = { viewModel.enterGroup(item.group.id) },
                                onLongClick = { actionGroup = item.group }
                            )
                            is GridItem.CollectionItem -> {
                                val count = uiState.videoCounts[item.collection.id] ?: 0
                                val watched = uiState.lastWatched[item.collection.id]
                                CollectionCard(
                                    collection = item.collection,
                                    videoCount = count,
                                    watchedIndex = watched,
                                    isFinished = watched != null && count > 0 && watched >= count,
                                    onClick = {
                                        onOpenCollection(item.collection.id, null, 0L)
                                    },
                                    onLongClick = { actionCollection = item.collection }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // ---- 导入命名确认对话框 ----
    if (uiState.pendingImports.isNotEmpty()) {
        ImportNamingDialog(
            items = uiState.pendingImports,
            merges = uiState.mergeGroups,
            onConfirm = { names -> viewModel.confirmImport(names) },
            onDismiss = { viewModel.cancelImport() }
        )
    }

    // ---- 媒体库设置对话框 ----
    if (showLibrarySettings) {
        LibrarySettingsDialog(
            currentLabel = uiState.libraryRootLabel,
            useFileMode = uiState.useFileMode,
            hasAllFilesAccess = viewModel.hasAllFilesAccess(),
            onChangeFolder = { rootPicker.launch(null); showLibrarySettings = false },
            onUsePathMode = {
                if (viewModel.hasAllFilesAccess()) {
                    viewModel.enableFileMode()
                } else {
                    runCatching { allFilesLauncher.launch(buildAllFilesAccessIntent(context)) }
                }
                showLibrarySettings = false
            },
            onRescan = { viewModel.scanLibrary(); showLibrarySettings = false },
            onDismiss = { showLibrarySettings = false }
        )
    }

    // ---- 分组管理对话框 ----
    if (showCreateGroup) {
        TextInputDialog(
            title = "新建分组",
            onConfirm = { name ->
                viewModel.createGroup(name)
                showCreateGroup = false
            },
            onDismiss = { showCreateGroup = false }
        )
    }

    // ---- 分组长按操作（重命名 / 删除） ----
    actionGroup?.let { group ->
        DropdownMenu(
            expanded = true,
            onDismissRequest = { actionGroup = null }
        ) {
            DropdownMenuItem(
                text = { Text("打开分组") },
                leadingIcon = { Icon(Icons.Default.Folder, contentDescription = null) },
                onClick = {
                    viewModel.enterGroup(group.id)
                    actionGroup = null
                }
            )
            DropdownMenuItem(
                text = { Text("设置封面") },
                leadingIcon = { Icon(Icons.Default.Image, contentDescription = null) },
                onClick = {
                    coverTarget = group
                    actionGroup = null
                }
            )
            DropdownMenuItem(
                text = { Text("重命名分组") },
                leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                onClick = {
                    editingGroup = group
                    actionGroup = null
                }
            )
            DropdownMenuItem(
                text = { Text("删除分组") },
                leadingIcon = {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error
                    )
                },
                onClick = {
                    showDeleteGroup = group
                    actionGroup = null
                }
            )
        }
    }

    editingGroup?.let { group ->
        TextInputDialog(
            title = "重命名分组",
            initialValue = group.name,
            confirmText = "保存",
            onConfirm = { name ->
                viewModel.renameGroup(group.id, name)
                editingGroup = null
            },
            onDismiss = { editingGroup = null }
        )
    }

    showDeleteGroup?.let { group ->
        ConfirmDialog(
            title = "删除分组",
            message = "删除「${group.name}」？\n分组内的视频集将移至「未分组」。",
            onConfirm = {
                viewModel.deleteGroup(group.id)
                showDeleteGroup = null
            },
            onDismiss = { showDeleteGroup = null }
        )
    }

    // ---- 分组封面选择对话框 ----
    coverTarget?.let { group ->
        GroupCoverDialog(
            groupName = group.name,
            collections = uiState.allCollections.filter { it.groupId == group.id },
            currentCover = group.coverUri,
            onPick = { coverUri ->
                viewModel.setGroupCover(group.id, coverUri)
                coverTarget = null
            },
            onDismiss = { coverTarget = null }
        )
    }

    // ---- 视频集操作对话框 ----
    actionCollection?.let { collection ->
        CollectionActionMenu(
            onPlay = {
                onOpenCollection(collection.id, null, 0L)
                actionCollection = null
            },
            onRename = { showRenameCollection = true },
            onMove = { showMoveCollection = true },
            onDelete = { showDeleteCollection = true },
            onDismiss = { actionCollection = null }
        )
    }

    if (showRenameCollection) {
        actionCollection?.let { collection ->
            TextInputDialog(
                title = "重命名视频集",
                initialValue = collection.name,
                confirmText = "保存",
                onConfirm = { name ->
                    viewModel.renameCollection(collection.id, name)
                    showRenameCollection = false
                    actionCollection = null
                },
                onDismiss = {
                    showRenameCollection = false
                    actionCollection = null
                }
            )
        }
    }

    if (showMoveCollection) {
        actionCollection?.let { collection ->
            MoveToGroupDialog(
                groups = uiState.groups.map { it.id },
                groupNames = uiState.groups.associate { it.id to it.name },
                currentGroupId = collection.groupId,
                onMove = { groupId ->
                    viewModel.moveToGroup(collection.id, groupId)
                    showMoveCollection = false
                    actionCollection = null
                },
                onDismiss = {
                    showMoveCollection = false
                    actionCollection = null
                }
            )
        }
    }

    if (showDeleteCollection) {
        actionCollection?.let { collection ->
            ConfirmDialog(
                title = "删除视频集",
                message = "删除「${collection.name}」？\n仅移除库中的记录，不会删除设备上的文件。",
                onConfirm = {
                    viewModel.deleteCollection(collection.id)
                    showDeleteCollection = false
                    actionCollection = null
                },
                onDismiss = {
                    showDeleteCollection = false
                    actionCollection = null
                }
            )
        }
    }
}

/**
 * 分组层顶部面包屑：← 返回全部 / 分组名
 */
@Composable
private fun GroupBreadcrumb(
    groupName: String,
    onBack: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TextButton(onClick = onBack) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Text(" 返回全部", modifier = Modifier.padding(start = 4.dp))
        }
        Text(
            text = "/ $groupName",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/** 视频集长按操作菜单 */
@Composable
private fun CollectionActionMenu(
    onPlay: () -> Unit,
    onRename: () -> Unit,
    onMove: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit
) {
    DropdownMenu(
        expanded = true,
        onDismissRequest = onDismiss
    ) {
        DropdownMenuItem(
            text = { Text("播放") },
            leadingIcon = {
                Icon(Icons.Default.PlayArrow, contentDescription = null)
            },
            onClick = onPlay
        )
        DropdownMenuItem(
            text = { Text("重命名") },
            leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
            onClick = onRename
        )
        DropdownMenuItem(
            text = { Text("移动到分组") },
            leadingIcon = { Icon(Icons.Default.DriveFolderUpload, contentDescription = null) },
            onClick = onMove
        )
        DropdownMenuItem(
            text = { Text("删除") },
            leadingIcon = {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
            },
            onClick = onDelete
        )
    }
}
