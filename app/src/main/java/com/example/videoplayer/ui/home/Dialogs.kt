package com.example.videoplayer.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp

/**
 * 文本输入对话框（新建/重命名通用）。
 */
@Composable
fun TextInputDialog(
    title: String,
    initialValue: String = "",
    confirmText: String = "确定",
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var value by remember { mutableStateOf(TextFieldValue(initialValue)) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("请输入名称") }
            )
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(value.text.trim()) },
                enabled = value.text.isNotBlank()
            ) {
                Text(confirmText)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

/**
 * 将视频集移动到分组的对话框。
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun MoveToGroupDialog(
    groups: List<Long>,
    groupNames: Map<Long, String>,
    currentGroupId: Long?,
    onMove: (Long?) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("移动到分组") },
        text = {
            Column {
                ListItem(
                    headlineContent = { Text("未分组") },
                    leadingContent = {
                        if (currentGroupId == null) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    },
                    modifier = Modifier.clickable { onMove(null) }
                )
                groups.forEach { id ->
                    ListItem(
                        headlineContent = { Text(groupNames[id] ?: "分组") },
                        leadingContent = {
                            if (currentGroupId == id) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        },
                        modifier = Modifier.clickable { onMove(id) }
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

/**
 * 媒体库设置：更换目录、切换路径模式、重新扫描。
 */
@Composable
fun LibrarySettingsDialog(
    currentLabel: String?,
    useFileMode: Boolean,
    hasAllFilesAccess: Boolean,
    onChangeFolder: () -> Unit,
    onUsePathMode: () -> Unit,
    onRescan: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("媒体库设置") },
        text = {
            Column {
                Text(
                    text = "当前目录：\n${currentLabel ?: "未设置"}",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = if (useFileMode) {
                        "当前为「固定路径」模式：可直接用文件管理器往该目录放视频。"
                    } else {
                        "当前为「授权目录」模式：把视频按「子文件夹」放入即可自动识别为视频集。"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onRescan) { Text("重新扫描") }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onChangeFolder) { Text("换目录") }
                TextButton(
                    onClick = onUsePathMode,
                    enabled = !useFileMode || !hasAllFilesAccess
                ) {
                    Text(if (hasAllFilesAccess) "固定路径" else "授权+固定路径")
                }
            }
        }
    )
}

/**
 * 删除确认对话框。
 */
@Composable
fun ConfirmDialog(
    title: String,
    message: String,
    confirmText: String = "删除",
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(confirmText) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}
