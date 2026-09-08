package com.example.videoplayer.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CallSplit
import androidx.compose.material.icons.automirrored.filled.MergeType
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.videoplayer.media.NameMerger
import com.example.videoplayer.media.PendingImport

/** 对话框中的一条导入条目（可能是一个文件，也可能是归并后的多个文件） */
private data class ImportEntry(
    val key: String,
    val name: String,
    val memberIndices: List<Int>
) {
    val isMerged: Boolean get() = memberIndices.size > 1
}

/**
 * 导入确认对话框。
 *
 * - 自动提取的名称已填入，可逐个修改
 * - 若检测到多个文件属于同一视频集（公共部分相同），会合并为一条并标注
 * - 每条归并项都可点「拆分」按钮还原为独立导入
 */
@Composable
fun ImportNamingDialog(
    items: List<PendingImport>,
    merges: List<NameMerger.MergeGroup>,
    onConfirm: (List<String>) -> Unit,
    onDismiss: () -> Unit
) {
    // 初始条目：归并组 + 未归并的独立项
    val entries = remember(items, merges) {
        mutableStateListOf<ImportEntry>().apply {
            val groupedIndices = merges.flatMap { it.memberIndices }.toSet()
            merges.forEach { group ->
                add(
                    ImportEntry(
                        key = "g_${group.memberIndices.first()}",
                        name = group.suggestedName,
                        memberIndices = group.memberIndices
                    )
                )
            }
            items.indices.filter { it !in groupedIndices }.forEach { index ->
                add(
                    ImportEntry(
                        key = "s_$index",
                        name = NameMerger.baseNameOf(items[index].suggestedName)
                            .ifBlank { items[index].suggestedName },
                        memberIndices = listOf(index)
                    )
                )
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.AutoMirrored.Filled.MergeType, contentDescription = null) },
        title = { Text("确认导入") },
        text = {
            Column {
                if (merges.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f))
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.MergeType,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "智能归并：检测到 ${merges.sumOf { it.memberIndices.size }} 个文件" +
                                "可能属于同一视频集，已合并为 ${merges.size} 项。" +
                                "可修改名称，或点右侧按钮拆分。",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                }

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 340.dp)
                ) {
                    items(entries, key = { it.key }) { entry ->
                        ImportEntryRow(
                            entry = entry,
                            items = items,
                            onNameChange = { newName ->
                                val idx = entries.indexOf(entry)
                                if (idx >= 0) entries[idx] = entry.copy(name = newName)
                            },
                            onSplit = {
                                val idx = entries.indexOf(entry)
                                if (idx >= 0 && entry.isMerged) {
                                    entries.removeAt(idx)
                                    entry.memberIndices.reversed().forEach { i ->
                                        entries.add(
                                            idx,
                                            ImportEntry(
                                                key = "s_$i",
                                                name = NameMerger.baseNameOf(items[i].suggestedName)
                                                    .ifBlank { items[i].suggestedName },
                                                memberIndices = listOf(i)
                                            )
                                        )
                                    }
                                }
                            }
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    // 展开为「每个待导入项 → 视频集名称」
                    val names = MutableList(items.size) { "" }
                    entries.forEach { entry ->
                        entry.memberIndices.forEach { index ->
                            names[index] = entry.name.trim().ifBlank { items[index].suggestedName }
                        }
                    }
                    onConfirm(names)
                },
                enabled = entries.all { it.name.isNotBlank() }
            ) {
                Text("确认导入")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

@Composable
private fun ImportEntryRow(
    entry: ImportEntry,
    items: List<PendingImport>,
    onNameChange: (String) -> Unit,
    onSplit: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = when {
                entry.isMerged -> Icons.Default.Folder
                items[entry.memberIndices.first()].isArchive -> Icons.Default.FolderZip
                else -> Icons.Default.Movie
            },
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        OutlinedTextField(
            value = entry.name,
            onValueChange = onNameChange,
            singleLine = true,
            modifier = Modifier.weight(1f),
            label = {
                Text(
                    text = if (entry.isMerged) {
                        "合并 ${entry.memberIndices.size} 个文件"
                    } else if (items[entry.memberIndices.first()].isArchive) {
                        "压缩包（解压为视频集）"
                    } else {
                        "视频"
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        )
        if (entry.isMerged) {
            IconButton(onClick = onSplit) {
                Icon(
                    Icons.AutoMirrored.Filled.CallSplit,
                    contentDescription = "拆分为独立导入",
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
    if (entry.isMerged) {
        Text(
            text = "来自：" + entry.memberIndices
                .joinToString("、") { items[it].suggestedName },
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 28.dp, top = 2.dp)
        )
    }
}
