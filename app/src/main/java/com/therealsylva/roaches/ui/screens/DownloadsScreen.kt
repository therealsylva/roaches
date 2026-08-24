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
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
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
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.therealsylva.roaches.data.model.DownloadEntry
import com.therealsylva.roaches.data.model.DownloadState
import com.therealsylva.roaches.data.model.SeasonDownloadProgress
import com.therealsylva.roaches.ui.components.StateMessage
import com.therealsylva.roaches.ui.components.formatBytes
import com.therealsylva.roaches.ui.theme.RoachesColors
import com.therealsylva.roaches.ui.theme.RoachesShapes
import com.therealsylva.roaches.ui.theme.RoachesSpacing
import kotlinx.coroutines.delay

@Composable
fun DownloadsScreen(
    downloads: List<DownloadEntry>,
    seasonDownloads: List<SeasonDownloadProgress>,
    onRefresh: () -> Unit,
    onRemove: (DownloadEntry) -> Unit,
    onPlay: (DownloadEntry) -> Unit,
    onRetry: (DownloadEntry) -> Unit,
    onCancelSeason: (String) -> Unit,
    onRetrySeason: (String) -> Unit,
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
        if (downloads.isEmpty() && seasonDownloads.isEmpty()) {
            item {
                StateMessage(
                    title = "No downloads",
                    message = "Choose a source from any title, then tap its download action.",
                )
            }
        } else {
            items(seasonDownloads, key = { "season-${it.batchId}" }) { batch ->
                SeasonDownloadRow(
                    batch = batch,
                    onCancel = { onCancelSeason(batch.batchId) },
                    onRetry = { onRetrySeason(batch.batchId) },
                )
            }
            items(downloads, key = { it.downloadId }) { entry ->
                DownloadRow(entry, { onPlay(entry) }, { onRetry(entry) }, { onRemove(entry) })
            }
        }
    }
}

@Composable
private fun SeasonDownloadRow(
    batch: SeasonDownloadProgress,
    onCancel: () -> Unit,
    onRetry: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = RoachesSpacing.md, vertical = RoachesSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(RoachesSpacing.md),
    ) {
        AsyncImage(
            model = batch.media.posterUrl ?: batch.media.backdropUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            placeholder = ColorPainter(RoachesColors.Scrim),
            error = ColorPainter(RoachesColors.Scrim),
            modifier = Modifier
                .width(64.dp)
                .aspectRatio(2f / 3f)
                .clip(RoachesShapes.Tight)
                .background(RoachesColors.Scrim),
        )
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(RoachesSpacing.xs)) {
            Text(
                batch.media.title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                "Season ${batch.season}",
                style = MaterialTheme.typography.labelMedium,
                color = RoachesColors.InkMuted,
            )
            LinearProgressIndicator(
                progress = { batch.progress },
                modifier = Modifier.fillMaxWidth().height(3.dp),
                color = RoachesColors.Crawl,
                trackColor = RoachesColors.SurfaceQuiet,
            )
            Text(
                seasonStatus(batch),
                style = MaterialTheme.typography.labelMedium,
                color = if (batch.failedCount > 0) RoachesColors.Error else RoachesColors.InkMuted,
            )
        }
        if (batch.retryAvailable) {
            IconButton(onClick = onRetry) {
                Icon(Icons.Rounded.Refresh, contentDescription = "Retry queued or failed episodes")
            }
        }
        IconButton(onClick = onCancel) {
            Icon(Icons.Rounded.DeleteOutline, contentDescription = "Remove season download")
        }
    }
}

private fun seasonStatus(batch: SeasonDownloadProgress): String = when {
    batch.readyCount == batch.totalCount -> "${batch.readyCount} of ${batch.totalCount} ready"
    batch.statusMessage != null && batch.activeEpisode != null -> {
        "Episode ${batch.activeEpisode} · ${batch.statusMessage}"
    }
    batch.activeEpisode != null && batch.activeProgress > 0f -> {
        "Episode ${batch.activeEpisode} · ${(batch.activeProgress * 100).toInt()}% downloaded"
    }
    batch.failedCount > 0 -> "${batch.readyCount} ready · ${batch.failedCount} failed"
    else -> "${batch.readyCount} of ${batch.totalCount} ready · ${batch.queuedCount} queued"
}

@Composable
private fun DownloadRow(entry: DownloadEntry, onPlay: () -> Unit, onRetry: () -> Unit, onRemove: () -> Unit) {
    val playable = entry.state == DownloadState.Complete && !entry.localUri.isNullOrBlank()
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(enabled = playable, onClick = onPlay)
            .padding(horizontal = RoachesSpacing.md, vertical = RoachesSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(RoachesSpacing.md),
    ) {
        AsyncImage(
            model = entry.media.posterUrl ?: entry.media.backdropUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            placeholder = ColorPainter(RoachesColors.Scrim),
            error = ColorPainter(RoachesColors.Scrim),
            modifier = Modifier
                .width(64.dp)
                .aspectRatio(2f / 3f)
                .clip(RoachesShapes.Tight)
                .background(RoachesColors.Scrim),
        )
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(RoachesSpacing.xs)) {
            Text(
                entry.media.title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (entry.season > 0 && entry.episode > 0) {
                Text(
                    "S${entry.season} E${entry.episode} · ${entry.episodeTitle ?: "Episode ${entry.episode}"}",
                    style = MaterialTheme.typography.labelMedium,
                    color = RoachesColors.InkMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
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
                    DownloadState.Queued -> entry.statusMessage ?: "Queued"
                    DownloadState.Downloading -> "${(entry.progress * 100).toInt()}% downloaded"
                    DownloadState.Complete -> "Ready offline"
                    DownloadState.Failed -> entry.statusMessage ?: "Download failed"
                    DownloadState.Missing -> entry.statusMessage ?: "File unavailable"
                },
                style = MaterialTheme.typography.labelMedium,
                color = if (entry.state == DownloadState.Failed) RoachesColors.Error else RoachesColors.InkMuted,
            )
        }
        if (playable) {
            IconButton(onClick = onPlay) {
                Icon(Icons.Rounded.PlayArrow, contentDescription = "Play download")
            }
        }
        if (entry.state in setOf(DownloadState.Queued, DownloadState.Failed, DownloadState.Missing)) {
            IconButton(onClick = onRetry) {
                Icon(
                    Icons.Rounded.Refresh,
                    contentDescription = if (entry.state == DownloadState.Queued) {
                        "Restart queued download"
                    } else {
                        "Retry download"
                    },
                )
            }
        }
        IconButton(onClick = onRemove) {
            Icon(Icons.Rounded.DeleteOutline, contentDescription = "Remove download")
        }
    }
}
