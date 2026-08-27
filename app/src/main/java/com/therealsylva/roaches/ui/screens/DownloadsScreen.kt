package com.therealsylva.roaches.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.compose.AsyncImagePainter
import coil.compose.SubcomposeAsyncImage
import coil.compose.SubcomposeAsyncImageContent
import coil.request.ImageRequest
import coil.request.videoFrameMillis
import com.therealsylva.roaches.data.model.DownloadEntry
import com.therealsylva.roaches.data.model.DownloadMediaType
import com.therealsylva.roaches.data.model.DownloadState
import com.therealsylva.roaches.data.model.CobaltPrepareResult
import com.therealsylva.roaches.data.model.CobaltPreparedFile
import com.therealsylva.roaches.data.model.CobaltSaveRequest
import com.therealsylva.roaches.data.model.LinkAudioBitrate
import com.therealsylva.roaches.data.model.LinkDownloadMode
import com.therealsylva.roaches.data.model.LinkVideoQuality
import com.therealsylva.roaches.data.model.SeasonDownloadProgress
import com.therealsylva.roaches.ui.components.CobaltChallengeView
import com.therealsylva.roaches.ui.components.StateMessage
import com.therealsylva.roaches.ui.components.formatBytes
import com.therealsylva.roaches.ui.theme.RoachesColors
import com.therealsylva.roaches.ui.theme.RoachesShapes
import com.therealsylva.roaches.ui.theme.RoachesSpacing
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadsScreen(
    downloads: List<DownloadEntry>,
    seasonDownloads: List<SeasonDownloadProgress>,
    linkSaveVisible: Boolean,
    linkSaveUrl: String,
    linkDownloadMode: LinkDownloadMode,
    linkVideoQuality: LinkVideoQuality,
    linkAudioBitrate: LinkAudioBitrate,
    linkSaveLoading: Boolean,
    linkSaveError: String?,
    linkPicker: CobaltPrepareResult.Picker?,
    cobaltChallengeSiteKey: String?,
    cobaltChallengeRequest: CobaltSaveRequest?,
    cobaltChallengeNonce: Int,
    linkRetryTitle: String?,
    onRefresh: () -> Unit,
    onOpenLinkSave: () -> Unit,
    onDismissLinkSave: () -> Unit,
    onLinkUrl: (String) -> Unit,
    onLinkMode: (LinkDownloadMode) -> Unit,
    onLinkVideoQuality: (LinkVideoQuality) -> Unit,
    onLinkAudioBitrate: (LinkAudioBitrate) -> Unit,
    onSubmitLink: () -> Unit,
    onChallengeProgress: () -> Unit,
    onChallengeResult: (String) -> Unit,
    onChallengeError: (String) -> Unit,
    onRetryChallenge: () -> Unit,
    onSavePickerFile: (CobaltPreparedFile) -> Unit,
    onSaveAllPickerFiles: () -> Unit,
    onRemove: (DownloadEntry) -> Unit,
    onRename: (DownloadEntry, String) -> Unit,
    onPlay: (DownloadEntry) -> Unit,
    onShare: (DownloadEntry) -> Unit,
    onRetry: (DownloadEntry) -> Unit,
    onCancelSeason: (String) -> Unit,
    onRetrySeason: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var renameTarget by remember { mutableStateOf<DownloadEntry?>(null) }
    var renameName by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        while (true) {
            onRefresh()
            delay(2_000)
        }
    }
    Box(modifier) {
        LazyColumn(
            Modifier.fillMaxSize().statusBarsPadding(),
            contentPadding = PaddingValues(bottom = RoachesSpacing.xl),
        ) {
            item {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = RoachesSpacing.md, vertical = RoachesSpacing.lg),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(RoachesSpacing.md),
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Downloads", style = MaterialTheme.typography.headlineLarge)
                        Text(
                            "Stored privately in the Roaches app folder",
                            style = MaterialTheme.typography.bodyMedium,
                            color = RoachesColors.InkMuted,
                        )
                    }
                    TextButton(onClick = onOpenLinkSave) {
                        Icon(Icons.Rounded.Link, contentDescription = null)
                        Spacer(Modifier.width(RoachesSpacing.xs))
                        Text("Save link")
                    }
                }
            }
            if (downloads.isEmpty() && seasonDownloads.isEmpty()) {
                item {
                    StateMessage(
                        title = "No downloads",
                        message = "Download a title or save a supported media link for offline use.",
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
                    DownloadRow(
                        entry = entry,
                        onPlay = { onPlay(entry) },
                        onShare = { onShare(entry) },
                        onRename = {
                            renameTarget = entry
                            renameName = entry.visibleName
                        },
                        onRetry = { onRetry(entry) },
                        onRemove = { onRemove(entry) },
                    )
                }
            }
        }

        if (linkSaveVisible) {
            LinkSaveSheet(
                url = linkSaveUrl,
                mode = linkDownloadMode,
                videoQuality = linkVideoQuality,
                audioBitrate = linkAudioBitrate,
                loading = linkSaveLoading,
                error = linkSaveError,
                picker = linkPicker,
                challengeSiteKey = cobaltChallengeSiteKey,
                challengeRequest = cobaltChallengeRequest,
                challengeNonce = cobaltChallengeNonce,
                retryTitle = linkRetryTitle,
                onDismiss = onDismissLinkSave,
                onUrl = onLinkUrl,
                onMode = onLinkMode,
                onVideoQuality = onLinkVideoQuality,
                onAudioBitrate = onLinkAudioBitrate,
                onSubmit = onSubmitLink,
                onChallengeProgress = onChallengeProgress,
                onChallengeResult = onChallengeResult,
                onChallengeError = onChallengeError,
                onRetryChallenge = onRetryChallenge,
                onSavePickerFile = onSavePickerFile,
                onSaveAllPickerFiles = onSaveAllPickerFiles,
            )
        }

        renameTarget?.let { entry ->
            AlertDialog(
                onDismissRequest = { renameTarget = null },
                title = { Text("Rename file") },
                text = {
                    OutlinedTextField(
                        value = renameName,
                        onValueChange = { renameName = it.take(160) },
                        label = { Text("File name") },
                        supportingText = { Text("The existing file extension is kept.") },
                        singleLine = true,
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            onRename(entry, renameName)
                            renameTarget = null
                        },
                        enabled = renameName.isNotBlank(),
                    ) {
                        Text("Rename")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { renameTarget = null }) {
                        Text("Cancel")
                    }
                },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LinkSaveSheet(
    url: String,
    mode: LinkDownloadMode,
    videoQuality: LinkVideoQuality,
    audioBitrate: LinkAudioBitrate,
    loading: Boolean,
    error: String?,
    picker: CobaltPrepareResult.Picker?,
    challengeSiteKey: String?,
    challengeRequest: CobaltSaveRequest?,
    challengeNonce: Int,
    retryTitle: String?,
    onDismiss: () -> Unit,
    onUrl: (String) -> Unit,
    onMode: (LinkDownloadMode) -> Unit,
    onVideoQuality: (LinkVideoQuality) -> Unit,
    onAudioBitrate: (LinkAudioBitrate) -> Unit,
    onSubmit: () -> Unit,
    onChallengeProgress: () -> Unit,
    onChallengeResult: (String) -> Unit,
    onChallengeError: (String) -> Unit,
    onRetryChallenge: () -> Unit,
    onSavePickerFile: (CobaltPreparedFile) -> Unit,
    onSaveAllPickerFiles: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = RoachesColors.Surface,
        contentColor = RoachesColors.Ink,
    ) {
        when {
            challengeSiteKey != null && challengeRequest != null -> CobaltChallengeContent(
                siteKey = challengeSiteKey,
                request = challengeRequest,
                nonce = challengeNonce,
                retryTitle = retryTitle,
                loading = loading,
                error = error,
                onProgress = onChallengeProgress,
                onResult = onChallengeResult,
                onError = onChallengeError,
                onRetry = onRetryChallenge,
            )
            picker != null -> CobaltPickerContent(
                picker = picker,
                error = error,
                onSave = onSavePickerFile,
                onSaveAll = onSaveAllPickerFiles,
            )
            else -> LinkSaveForm(
                url = url,
                mode = mode,
                videoQuality = videoQuality,
                audioBitrate = audioBitrate,
                loading = loading,
                error = error,
                onUrl = onUrl,
                onMode = onMode,
                onVideoQuality = onVideoQuality,
                onAudioBitrate = onAudioBitrate,
                onSubmit = onSubmit,
            )
        }
    }
}

@Composable
private fun LinkSaveForm(
    url: String,
    mode: LinkDownloadMode,
    videoQuality: LinkVideoQuality,
    audioBitrate: LinkAudioBitrate,
    loading: Boolean,
    error: String?,
    onUrl: (String) -> Unit,
    onMode: (LinkDownloadMode) -> Unit,
    onVideoQuality: (LinkVideoQuality) -> Unit,
    onAudioBitrate: (LinkAudioBitrate) -> Unit,
    onSubmit: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .heightIn(max = 620.dp)
            .verticalScroll(rememberScrollState())
            .imePadding()
            .navigationBarsPadding()
            .padding(horizontal = RoachesSpacing.md, vertical = RoachesSpacing.sm),
        verticalArrangement = Arrangement.spacedBy(RoachesSpacing.md),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(RoachesSpacing.xs)) {
            Text("Save a media link", style = MaterialTheme.typography.headlineMedium)
            Text(
                "Paste a link supported by Cobalt. Roaches saves the resulting file to Downloads.",
                style = MaterialTheme.typography.bodyMedium,
                color = RoachesColors.InkMuted,
            )
        }
        OutlinedTextField(
            value = url,
            onValueChange = onUrl,
            modifier = Modifier.fillMaxWidth(),
            enabled = !loading,
            singleLine = true,
            label = { Text("Media link") },
            placeholder = { Text("https://") },
            isError = error != null,
        )
        LinkChoiceRow(
            label = "Save as",
            values = LinkDownloadMode.entries.toList(),
            selected = mode,
            valueLabel = LinkDownloadMode::label,
            onSelect = onMode,
            enabled = !loading,
        )
        if (mode == LinkDownloadMode.Video) {
            LinkChoiceRow(
                label = "Video quality",
                values = LinkVideoQuality.entries.toList(),
                selected = videoQuality,
                valueLabel = LinkVideoQuality::label,
                onSelect = onVideoQuality,
                enabled = !loading,
            )
        } else {
            LinkChoiceRow(
                label = "Audio quality",
                values = LinkAudioBitrate.entries.toList(),
                selected = audioBitrate,
                valueLabel = LinkAudioBitrate::label,
                onSelect = onAudioBitrate,
                enabled = !loading,
            )
        }
        error?.let {
            Text(it, style = MaterialTheme.typography.bodyMedium, color = RoachesColors.Error)
        }
        Button(
            onClick = onSubmit,
            enabled = !loading && url.isNotBlank(),
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
        ) {
            if (loading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
                Spacer(Modifier.width(RoachesSpacing.xs))
                Text("Preparing")
            } else {
                Text("Save")
            }
        }
        Spacer(Modifier.height(RoachesSpacing.sm))
    }
}

@Composable
private fun <T> LinkChoiceRow(
    label: String,
    values: List<T>,
    selected: T,
    valueLabel: (T) -> String,
    onSelect: (T) -> Unit,
    enabled: Boolean,
) {
    Column(verticalArrangement = Arrangement.spacedBy(RoachesSpacing.xs)) {
        Text(label, style = MaterialTheme.typography.labelLarge)
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(RoachesSpacing.xs),
        ) {
            values.forEach { value ->
                FilterChip(
                    selected = value == selected,
                    onClick = { onSelect(value) },
                    enabled = enabled,
                    label = { Text(valueLabel(value)) },
                )
            }
        }
    }
}

@Composable
private fun CobaltChallengeContent(
    siteKey: String,
    request: CobaltSaveRequest,
    nonce: Int,
    retryTitle: String?,
    loading: Boolean,
    error: String?,
    onProgress: () -> Unit,
    onResult: (String) -> Unit,
    onError: (String) -> Unit,
    onRetry: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = RoachesSpacing.md, vertical = RoachesSpacing.sm),
        verticalArrangement = Arrangement.spacedBy(RoachesSpacing.md),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(RoachesSpacing.xs)) {
            Text(
                retryTitle?.let { "Refreshing $it" } ?: "Preparing download",
                style = MaterialTheme.typography.headlineMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                if (loading) {
                    "Roaches is preparing your link. Keep this open for a moment."
                } else {
                    "The connection check runs automatically. Complete the box only if Cloudflare shows one."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = RoachesColors.InkMuted,
            )
        }
        CobaltChallengeView(
            siteKey = siteKey,
            request = request,
            nonce = nonce,
            onProgress = onProgress,
            onResult = onResult,
            onError = onError,
            modifier = Modifier.fillMaxWidth().height(96.dp),
        )
        if (error == null) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(RoachesSpacing.sm),
            ) {
                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                Text(
                    if (loading) "Preparing link" else "Checking connection",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
        error?.let {
            Text(it, style = MaterialTheme.typography.bodyMedium, color = RoachesColors.Error)
            TextButton(onClick = onRetry, enabled = !loading) { Text("Reload check") }
        }
        Spacer(Modifier.height(RoachesSpacing.sm))
    }
}

@Composable
private fun CobaltPickerContent(
    picker: CobaltPrepareResult.Picker,
    error: String?,
    onSave: (CobaltPreparedFile) -> Unit,
    onSaveAll: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = RoachesSpacing.md, vertical = RoachesSpacing.sm),
        verticalArrangement = Arrangement.spacedBy(RoachesSpacing.md),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(RoachesSpacing.xs)) {
            Text("Choose what to save", style = MaterialTheme.typography.headlineMedium)
            Text(
                "This link contains more than one media item.",
                style = MaterialTheme.typography.bodyMedium,
                color = RoachesColors.InkMuted,
            )
        }
        if (picker.items.isNotEmpty()) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(RoachesSpacing.sm)) {
                items(picker.items, key = { it.selection?.index ?: it.url }) { file ->
                    CobaltPickerTile(file = file, onClick = { onSave(file) })
                }
            }
        }
        picker.audio?.let { audio ->
            TextButton(onClick = { onSave(audio) }) {
                Icon(Icons.Rounded.MusicNote, contentDescription = null)
                Spacer(Modifier.width(RoachesSpacing.xs))
                Text("Save accompanying audio")
            }
        }
        if (picker.items.size > 1) {
            Button(
                onClick = onSaveAll,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            ) { Text("Save all ${picker.items.size} items") }
        }
        error?.let {
            Text(it, style = MaterialTheme.typography.bodyMedium, color = RoachesColors.Error)
        }
        Spacer(Modifier.height(RoachesSpacing.sm))
    }
}

@Composable
private fun CobaltPickerTile(file: CobaltPreparedFile, onClick: () -> Unit) {
    Column(
        Modifier
            .width(112.dp)
            .clickable(onClick = onClick),
        verticalArrangement = Arrangement.spacedBy(RoachesSpacing.xs),
    ) {
        val image = file.thumbnailUrl ?: file.url.takeIf {
            file.mediaType in setOf(DownloadMediaType.Image, DownloadMediaType.Gif)
        }
        if (image != null) {
            AsyncImage(
                model = image,
                contentDescription = "${file.mediaType.label} preview",
                contentScale = ContentScale.Crop,
                placeholder = ColorPainter(RoachesColors.SurfaceQuiet),
                error = ColorPainter(RoachesColors.SurfaceQuiet),
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(RoachesShapes.Tight)
                    .background(RoachesColors.SurfaceQuiet),
            )
        } else {
            Box(
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(RoachesShapes.Tight)
                    .background(RoachesColors.SurfaceQuiet),
                contentAlignment = Alignment.Center,
            ) {
                Icon(file.mediaType.icon(), contentDescription = null, modifier = Modifier.size(32.dp))
            }
        }
        Text(
            file.mediaType.label,
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
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
private fun DownloadRow(
    entry: DownloadEntry,
    onPlay: () -> Unit,
    onShare: () -> Unit,
    onRename: () -> Unit,
    onRetry: () -> Unit,
    onRemove: () -> Unit,
) {
    val playable = entry.state == DownloadState.Complete && !entry.localUri.isNullOrBlank()
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(enabled = playable, onClick = onPlay)
            .padding(horizontal = RoachesSpacing.md, vertical = RoachesSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(RoachesSpacing.md),
    ) {
        DownloadArtwork(entry)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(RoachesSpacing.xs)) {
            Text(
                entry.visibleName,
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
                downloadTechnicalLabel(entry),
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
            Row(Modifier.padding(top = RoachesSpacing.xs)) {
                if (playable) {
                    IconButton(onClick = onPlay) {
                        Icon(
                            if (entry.mediaType == DownloadMediaType.Video) Icons.Rounded.PlayArrow else entry.mediaType.icon(),
                            contentDescription = if (entry.mediaType == DownloadMediaType.Video) {
                                "Play download"
                            } else {
                                "Open download"
                            },
                        )
                    }
                }
                if (entry.canShareVideo) {
                    IconButton(onClick = onShare) {
                        Icon(Icons.Rounded.Share, contentDescription = "Share downloaded video")
                    }
                }
                if (entry.canRenameFile) {
                    IconButton(onClick = onRename) {
                        Icon(Icons.Rounded.Edit, contentDescription = "Rename downloaded file")
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
    }
}

@Composable
private fun DownloadArtwork(entry: DownloadEntry) {
    val artwork = entry.media.posterUrl ?: entry.media.backdropUrl
    val isVideo = entry.mediaType == DownloadMediaType.Video
    val videoUri = entry.localUri.takeIf { isVideo && entry.state == DownloadState.Complete }
    val artworkModifier = Modifier
        .width(if (isVideo) 96.dp else 64.dp)
        .aspectRatio(if (isVideo) 16f / 9f else 1f)
        .clip(RoachesShapes.Tight)
        .background(RoachesColors.SurfaceQuiet)
    Box(artworkModifier, contentAlignment = Alignment.Center) {
        if (videoUri != null) {
            val context = LocalContext.current
            val request = remember(entry.downloadId, videoUri) {
                ImageRequest.Builder(context)
                    .data(videoUri)
                    .videoFrameMillis(1_000)
                    .build()
            }
            SubcomposeAsyncImage(
                model = request,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            ) {
                if (painter.state is AsyncImagePainter.State.Success) {
                    SubcomposeAsyncImageContent()
                } else {
                    DownloadArtworkFallback(artwork, entry.mediaType)
                }
            }
        } else {
            DownloadArtworkFallback(artwork, entry.mediaType)
        }
    }
}

@Composable
private fun DownloadArtworkFallback(artwork: String?, mediaType: DownloadMediaType) {
    if (artwork != null) {
        AsyncImage(
            model = artwork,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            placeholder = ColorPainter(RoachesColors.Scrim),
            error = ColorPainter(RoachesColors.Scrim),
            modifier = Modifier.fillMaxSize(),
        )
    } else {
        Icon(mediaType.icon(), contentDescription = null, modifier = Modifier.size(30.dp))
    }
}

private fun downloadTechnicalLabel(entry: DownloadEntry): String {
    if (!entry.isLinkSave) {
        return listOfNotNull(entry.source.technicalLabel, formatBytes(entry.source.sizeBytes))
            .joinToString(" · ")
    }
    return buildList {
        add(entry.mediaType.label)
        entry.source.codec?.takeIf(String::isNotBlank)?.let(::add)
        entry.source.audio?.takeIf(String::isNotBlank)?.let(::add)
        formatBytes(entry.source.sizeBytes)?.let(::add)
    }.distinct().joinToString(" · ")
}

private fun DownloadMediaType.icon(): ImageVector = when (this) {
    DownloadMediaType.Video -> Icons.Rounded.Movie
    DownloadMediaType.Audio -> Icons.Rounded.MusicNote
    DownloadMediaType.Image, DownloadMediaType.Gif -> Icons.Rounded.Image
}
