package com.example.videoplayer.ui.home

import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.videoplayer.data.SettingsStore
import java.io.File

/**
 * 首次启动引导：明确询问是否在本机自动创建用于存放视频的文件夹。
 */
@Composable
fun LibrarySetupScreen(
    hasAllFilesAccess: Boolean,
    onCreateFolder: () -> Unit,
    onPickFolder: () -> Unit,
    onGrantAllFiles: () -> Unit,
    onSkip: () -> Unit
) {
    val defaultPath = defaultFolderPath()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.FolderOpen,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(72.dp)
        )
        Spacer(modifier = Modifier.height(20.dp))
        Text(
            text = "是否创建视频存放文件夹？",
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "本应用按「媒体库 / 视频集名称 / 视频文件」\n的层级组织视频。\n是否现在自动创建一个存放视频的文件夹？",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(28.dp))

        // 主选项：自动创建
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.CreateNewFolder,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.size(8.dp))
                    Text(
                        text = "自动创建（推荐）",
                        style = MaterialTheme.typography.titleSmall
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "将在以下位置创建文件夹：\n$defaultPath",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (!hasAllFilesAccess) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "需要「所有文件访问」权限，点击下方按钮前往开启。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = if (hasAllFilesAccess) onCreateFolder else onGrantAllFiles,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = if (hasAllFilesAccess) Icons.Default.CreateNewFolder
                        else Icons.Default.Lock,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = if (hasAllFilesAccess) "立即创建" else "授权并创建",
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 次选项：自行选择
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Folder,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.size(8.dp))
                    Text(
                        text = "使用我自己的文件夹",
                        style = MaterialTheme.typography.titleSmall
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "如果已有存放视频的目录，可手动指定。\n无需特殊权限。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedButton(
                    onClick = onPickFolder,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("选择文件夹")
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        TextButton(onClick = onSkip) {
            Text("暂时跳过，稍后再设置")
        }
    }
}

/** 默认创建的文件夹路径 */
fun defaultFolderPath(): String = runCatching {
    File(
        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES),
        SettingsStore.DEFAULT_FOLDER_NAME
    ).absolutePath
}.getOrDefault("Movies/${SettingsStore.DEFAULT_FOLDER_NAME}")

/**
 * 跳转到「所有文件访问」授权页。
 */
fun buildAllFilesAccessIntent(context: android.content.Context): Intent =
    Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
        data = Uri.fromParts("package", context.packageName, null)
    }
