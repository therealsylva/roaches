package com.therealsylva.roaches.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.therealsylva.roaches.data.model.DownloadEntry
import com.therealsylva.roaches.data.model.DownloadState
import com.therealsylva.roaches.data.model.MediaItem
import com.therealsylva.roaches.ui.components.StateMessage
import com.therealsylva.roaches.ui.components.formatBytes
import com.therealsylva.roaches.ui.theme.RoachesColors
import com.therealsylva.roaches.ui.theme.RoachesShapes
import com.therealsylva.roaches.ui.theme.RoachesSpacing
import kotlinx.coroutines.delay

@Composable
fun DownloadsScreen(
    downloads: List<DownloadEntry>,
    onRefresh: () -> Unit,
    onRemove: (DownloadEntry) -> Unit,
    onOpen: (MediaItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    LaunchedEffect(Unit) {
        while (true) {
            onRefresh()
            delay(2_000)
        }
    }
    LazyColumn(
        modifier.statusBarsPadding(),
        contentPadding = PaddingValues(bottom = RoachesSpacing.xl),
    ) {
        item {
            Column(Modifier.padding(horizontal = RoachesSpacing.md, vertical = RoachesSpacing.lg)) {
                Text("Downloads", style = MaterialTheme.typography.headlineLarge)
                Text(
                    "Stored privately in the Roaches app folder",
                    style = MaterialTheme.typography.bodyMedium,
                    color = RoachesColors.InkMuted,
                )
            }
        }
        if (downloads.isEmpty()) {
            item {
                StateMessage(
                    title = "No downloads",
                    message = "Choose a source from any title, then tap its download action.",
                )
            }
        } else {
            items(downloads, key = { it.downloadId }) { entry ->
                DownloadRow(entry, { onOpen(entry.media) }, { onRemove(entry) })
            }
        }
    }
}

@Composable
private fun DownloadRow(entry: DownloadEntry, onOpen: () -> Unit, onRemove: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen)
            .padding(horizontal = RoachesSpacing.md, vertical = RoachesSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(RoachesSpacing.md),
    ) {
        AsyncImage(
            model = entry.media.posterUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .width(64.dp)
                .aspectRatio(2f / 3f)
                .clip(RoachesShapes.Tight)
                .background(RoachesColors.SurfaceQuiet),
        )
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(RoachesSpacing.xs)) {
            Text(
                entry.media.title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                listOfNotNull(entry.source.technicalLabel, formatBytes(entry.source.sizeBytes)).joinToString(" · "),
                style = MaterialTheme.typography.labelMedium,
                color = RoachesColors.InkMuted,
                maxLines = 1,
            )
            if (entry.state == DownloadState.Downloading || entry.state == DownloadState.Queued) {
                LinearProgressIndicator(
                    progress = { entry.progress },
                    modifier = Modifier.fillMaxWidth().height(3.dp),
                    color = RoachesColors.Crawl,
                    trackColor = RoachesColors.SurfaceQuiet,
                )
            }
            Text(
                when (entry.state) {
                    DownloadState.Queued -> "Queued"
                    DownloadState.Downloading -> "${(entry.progress * 100).toInt()}% downloaded"
                    DownloadState.Complete -> "Ready offline"
                    DownloadState.Failed -> "Download failed"
                    DownloadState.Missing -> "File unavailable"
                },
                style = MaterialTheme.typography.labelMedium,
                color = if (entry.state == DownloadState.Failed) RoachesColors.Error else RoachesColors.InkMuted,
            )
        }
        IconButton(onClick = onRemove) {
            Icon(Icons.Rounded.DeleteOutline, contentDescription = "Remove download")
        }
    }
}
