package com.example.videoplayer.ui.home

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.videoplayer.data.model.VideoCollection
import com.example.videoplayer.data.model.VideoSearchResult
import com.example.videoplayer.ui.components.FormatUtils

/**
 * 搜索结果列表：分为「视频集」与「视频」两段。
 * 视频文件命中时可直接点击跳转到该集播放。
 */
@Composable
fun SearchResultList(
    query: String,
    collections: List<VideoCollection>,
    videos: List<VideoSearchResult>,
    videoCounts: Map<Long, Int>,
    lastWatched: Map<Long, Int>,
    onOpenCollection: (Long) -> Unit,
    onOpenVideo: (VideoSearchResult) -> Unit
) {
    if (query.isBlank()) {
        HintText("输入关键词，按视频集名称或视频文件名搜索")
        return
    }
    if (collections.isEmpty() && videos.isEmpty()) {
        HintText("没有找到与「$query」匹配的内容")
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 8.dp)
    ) {
        if (collections.isNotEmpty()) {
            item {
                SectionHeader("视频集（${collections.size}）")
            }
            items(collections, key = { "c_${it.id}" }) { collection ->
                val count = videoCounts[collection.id] ?: 0
                val watched = lastWatched[collection.id]
                ResultRow(
                    title = collection.name,
                    subtitle = buildString {
                        append("$count 个视频")
                        if (watched != null && count > 0) {
                            append(if (watched >= count) " · 已看完" else " · 看到 $watched/$count 集")
                        }
                    },
                    coverUri = collection.coverUri,
                    onClick = { onOpenCollection(collection.id) }
                )
            }
        }

        if (videos.isNotEmpty()) {
            item {
                SectionHeader("视频（${videos.size}）")
            }
            items(videos, key = { "v_${it.id}" }) { video ->
                ResultRow(
                    title = video.fileName,
                    subtitle = video.collectionName + " · " + progressLabel(
                        video.lastPosition, video.duration
                    ),
                    coverUri = null,
                    onClick = { onOpenVideo(video) }
                )
            }
        }
    }
}

/** 观看状态文案 */
private fun progressLabel(lastPosition: Long, duration: Long): String {
    if (lastPosition <= 0L) return "未看"
    if (duration > 0L) {
        val ratio = lastPosition.toFloat() / duration
        if (ratio >= 0.95f) return "已看完"
        return "看到 ${FormatUtils.formatDuration(lastPosition)} / ${FormatUtils.formatDuration(duration)}"
    }
    return "看到 ${FormatUtils.formatDuration(lastPosition)}"
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 4.dp)
    )
}

@Composable
private fun HintText(text: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ResultRow(
    title: String,
    subtitle: String,
    coverUri: String?,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(width = 64.dp, height = 40.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            if (!coverUri.isNullOrBlank()) {
                AsyncImage(
                    model = Uri.parse(coverUri),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Icon(
                    imageVector = Icons.Default.Movie,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Icon(
            imageVector = Icons.Default.PlayArrow,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(22.dp)
        )
    }
}
