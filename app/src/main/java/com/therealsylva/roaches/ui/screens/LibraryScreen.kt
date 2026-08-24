package com.therealsylva.roaches.ui.screens

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.FileOpen
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.VideoFile
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.therealsylva.roaches.data.model.LocalMediaEntry
import com.therealsylva.roaches.data.model.MediaItem
import com.therealsylva.roaches.data.model.WatchEntry
import com.therealsylva.roaches.ui.components.PosterCard
import com.therealsylva.roaches.ui.components.SectionTitle
import com.therealsylva.roaches.ui.components.StateMessage
import com.therealsylva.roaches.ui.theme.RoachesColors
import com.therealsylva.roaches.ui.theme.RoachesShapes
import com.therealsylva.roaches.ui.theme.RoachesSpacing

@Composable
fun LibraryScreen(
    watchlist: List<MediaItem>,
    liked: List<MediaItem>,
    history: List<WatchEntry>,
    localMedia: List<LocalMediaEntry>,
    onImport: (String) -> Unit,
    onPlayLocal: (LocalMediaEntry) -> Unit,
    onRemoveLocal: (LocalMediaEntry) -> Unit,
    onOpen: (MediaItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            onImport(uri.toString())
        }
    }
    LazyColumn(
        modifier.statusBarsPadding(),
        contentPadding = PaddingValues(bottom = RoachesSpacing.xl),
    ) {
        item {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = RoachesSpacing.md, vertical = RoachesSpacing.lg),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Library", style = MaterialTheme.typography.headlineLarge)
                    Text(
                        "Saved, liked and local on this device",
                        style = MaterialTheme.typography.bodyMedium,
                        color = RoachesColors.InkMuted,
                    )
                }
                TextButton(onClick = { importer.launch(arrayOf("video/*")) }) {
                    Icon(Icons.Rounded.FileOpen, contentDescription = null)
                    Text("Import video")
                }
            }
        }
        if (watchlist.isEmpty() && liked.isEmpty() && history.isEmpty() && localMedia.isEmpty()) {
            item {
                StateMessage(
                    title = "Your shelf is empty",
                    message = "Save a title or begin watching and it will appear here.",
                )
            }
        }
        if (liked.isNotEmpty()) {
            item { SectionTitle("Liked", Modifier.padding(RoachesSpacing.md)) }
            item {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = RoachesSpacing.md),
                    horizontalArrangement = Arrangement.spacedBy(RoachesSpacing.sm),
                ) {
                    items(liked, key = { it.id }) { item -> PosterCard(item, { onOpen(item) }) }
                }
            }
            item { Spacer(Modifier.height(RoachesSpacing.lg)) }
        }
        if (watchlist.isNotEmpty()) {
            item { SectionTitle("Saved", Modifier.padding(RoachesSpacing.md)) }
            item {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = RoachesSpacing.md),
                    horizontalArrangement = Arrangement.spacedBy(RoachesSpacing.sm),
                ) {
                    items(watchlist, key = { it.id }) { item -> PosterCard(item, { onOpen(item) }) }
                }
            }
            item { Spacer(Modifier.height(RoachesSpacing.lg)) }
        }
        if (history.isNotEmpty()) {
            item { SectionTitle("Recently watched", Modifier.padding(RoachesSpacing.md)) }
            item {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = RoachesSpacing.md),
                    horizontalArrangement = Arrangement.spacedBy(RoachesSpacing.sm),
                ) {
                    items(history, key = { it.media.id }) { entry ->
                        PosterCard(entry.media, { onOpen(entry.media) }, progress = entry.progress)
                    }
                }
            }
        }
        if (localMedia.isNotEmpty()) {
            item { SectionTitle("Local media", Modifier.padding(RoachesSpacing.md)) }
            items(localMedia, key = LocalMediaEntry::id) { entry ->
                LocalMediaRow(
                    entry = entry,
                    onPlay = { onPlayLocal(entry) },
                    onRemove = { onRemoveLocal(entry) },
                )
            }
        }
    }
}

@Composable
private fun LocalMediaRow(entry: LocalMediaEntry, onPlay: () -> Unit, onRemove: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onPlay)
            .padding(horizontal = RoachesSpacing.md, vertical = RoachesSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(RoachesSpacing.sm),
    ) {
        androidx.compose.foundation.layout.Box(
            Modifier
                .size(52.dp)
                .clip(RoachesShapes.Tight)
                .background(RoachesColors.SurfaceQuiet),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.VideoFile, contentDescription = null, tint = RoachesColors.InkMuted)
        }
        Text(entry.title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        IconButton(onClick = onPlay) {
            Icon(Icons.Rounded.PlayArrow, contentDescription = "Play ${entry.title}")
        }
        IconButton(onClick = onRemove) {
            Icon(Icons.Rounded.DeleteOutline, contentDescription = "Remove ${entry.title}")
        }
    }
    HorizontalDivider(color = RoachesColors.SurfaceQuiet)
}
