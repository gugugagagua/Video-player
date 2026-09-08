package com.example.videoplayer.ui.home

import android.net.Uri
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.videoplayer.data.model.VideoCollection

/**
 * 分组封面选择对话框：
 * 列出该分组内的视频集，用户点击其中一个，即用它的封面作为分组封面。
 */
@Composable
fun GroupCoverDialog(
    groupName: String,
    collections: List<VideoCollection>,
    currentCover: String?,
    onPick: (String?) -> Unit,
    onDismiss: () -> Unit
) {
    val available = collections.filter { !it.coverUri.isNullOrBlank() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("设置「$groupName」封面") },
        text = {
            if (collections.isEmpty()) {
                Text("该分组还没有视频集。\n先为分组添加视频集，\n再回来选择封面。")
            } else if (available.isEmpty()) {
                Text("组内视频集都没有封面，无法选择。\n请先为视频集生成封面（文件夹中有 cover 图或截取首帧）。")
            } else {
                Column {
                    Text(
                        text = "选择任一视频集，使用它的封面：",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 96.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 300.dp)
                    ) {
                        items(available, key = { it.id }) { collection ->
                            GroupCoverOption(
                                collection = collection,
                                isCurrent = collection.coverUri != null &&
                                    collection.coverUri == currentCover,
                                onPick = { collection.coverUri?.let(onPick) }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (currentCover != null) {
                TextButton(onClick = { onPick(null) }) { Text("清除封面") }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("关闭") }
        }
    )
}

@Composable
private fun GroupCoverOption(
    collection: VideoCollection,
    isCurrent: Boolean,
    onPick: () -> Unit
) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onPick)
            .border(
                width = if (isCurrent) 2.dp else 1.dp,
                color = if (isCurrent) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.outlineVariant,
                shape = RoundedCornerShape(8.dp)
            )
            .padding(4.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(6.dp))
        ) {
            AsyncImage(
                model = Uri.parse(collection.coverUri),
                contentDescription = collection.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxWidth()
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = collection.name,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 2.dp)
        )
    }
}
