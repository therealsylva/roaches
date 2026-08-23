package com.therealsylva.roaches.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.therealsylva.roaches.data.model.MediaItem
import com.therealsylva.roaches.data.model.WatchEntry
import com.therealsylva.roaches.ui.components.PosterCard
import com.therealsylva.roaches.ui.components.SectionTitle
import com.therealsylva.roaches.ui.components.StateMessage
import com.therealsylva.roaches.ui.theme.RoachesColors
import com.therealsylva.roaches.ui.theme.RoachesSpacing

@Composable
fun LibraryScreen(
    watchlist: List<MediaItem>,
    history: List<WatchEntry>,
    onOpen: (MediaItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier.statusBarsPadding(),
        contentPadding = PaddingValues(bottom = RoachesSpacing.xl),
    ) {
        item {
            Column(Modifier.padding(horizontal = RoachesSpacing.md, vertical = RoachesSpacing.lg)) {
                Text("Library", style = MaterialTheme.typography.headlineLarge)
                Text(
                    "Saved and recently watched on this device",
                    style = MaterialTheme.typography.bodyMedium,
                    color = RoachesColors.InkMuted,
                )
            }
        }
        if (watchlist.isEmpty() && history.isEmpty()) {
            item {
                StateMessage(
                    title = "Your shelf is empty",
                    message = "Save a title or begin watching and it will appear here.",
                )
            }
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
    }
}
