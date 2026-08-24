package com.therealsylva.roaches.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.BookmarkBorder
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.therealsylva.roaches.data.model.MediaItem
import com.therealsylva.roaches.data.model.SourceIntent
import com.therealsylva.roaches.data.model.StreamSource
import com.therealsylva.roaches.ui.RoachesUiState
import com.therealsylva.roaches.ui.components.ArtworkScrim
import com.therealsylva.roaches.ui.components.LoadingState
import com.therealsylva.roaches.ui.components.PrimaryWatchButton
import com.therealsylva.roaches.ui.components.PosterCard
import com.therealsylva.roaches.ui.components.SectionTitle
import com.therealsylva.roaches.ui.components.StateMessage
import com.therealsylva.roaches.ui.components.formatBytes
import com.therealsylva.roaches.ui.theme.RoachesColors
import com.therealsylva.roaches.ui.theme.RoachesShapes
import com.therealsylva.roaches.ui.theme.RoachesSpacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailsScreen(
    state: RoachesUiState,
    saved: Boolean,
    liked: Boolean,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onWatch: () -> Unit,
    onDownloadRequest: () -> Unit,
    onDownloadSeasonRequest: () -> Unit,
    onToggleSaved: () -> Unit,
    onToggleLiked: () -> Unit,
    onSeason: (Int) -> Unit,
    onEpisode: (Int) -> Unit,
    onDismissSources: () -> Unit,
    onRetrySources: () -> Unit,
    onPlay: (StreamSource) -> Unit,
    onDownload: (StreamSource) -> Unit,
    onDownloadSeason: (StreamSource) -> Unit,
    onOpenRelated: (MediaItem) -> Unit,
) {
    val item = state.details?.item ?: state.detailsSeed
    Box(Modifier.fillMaxSize().background(RoachesColors.Canvas)) {
        if (item != null) {
            LazyColumn(contentPadding = PaddingValues(bottom = RoachesSpacing.xxl)) {
                item(key = "details-hero") {
                    DetailsHero(
                        item = item,
                        state = state,
                        saved = saved,
                        liked = liked,
                        onBack = onBack,
                        onWatch = onWatch,
                        onDownload = onDownloadRequest,
                        onToggleSaved = onToggleSaved,
                        onToggleLiked = onToggleLiked,
                    )
                }
                when {
                    state.detailsLoading -> item { LoadingState("Loading title details") }
                    state.detailsError != null -> item {
                        StateMessage(
                            title = "Details unavailable",
                            message = state.detailsError,
                            action = "Try again",
                            onAction = onRetry,
                        )
                    }
                    state.details != null -> {
                        val details = state.details
                        if (details.synopsis.isNotBlank()) {
                            item {
                                Column(
                                    Modifier.padding(horizontal = RoachesSpacing.md, vertical = RoachesSpacing.lg),
                                    verticalArrangement = Arrangement.spacedBy(RoachesSpacing.sm),
                                ) {
                                    SectionTitle("Story")
                                    Text(
                                        details.synopsis,
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = RoachesColors.InkMuted,
                                    )
                                }
                            }
                        }
                        val facts = listOfNotNull(
                            details.genres.takeIf { it.isNotEmpty() }?.joinToString(" · "),
                            details.director?.takeIf(String::isNotBlank)?.let { "Directed by $it" },
                            details.cast?.takeIf(String::isNotBlank)?.let { "Starring $it" },
                            details.country?.takeIf(String::isNotBlank),
                        )
                        if (facts.isNotEmpty()) {
                            item {
                                Column(
                                    Modifier.padding(horizontal = RoachesSpacing.md),
                                    verticalArrangement = Arrangement.spacedBy(RoachesSpacing.xs),
                                ) {
                                    facts.forEach { fact ->
                                        Text(fact, style = MaterialTheme.typography.bodyMedium, color = RoachesColors.InkMuted)
                                    }
                                }
                            }
                        }
                        if (details.seasons.isNotEmpty()) {
                            item {
                                EpisodePicker(
                                    state = state,
                                    onSeason = onSeason,
                                    onEpisode = onEpisode,
                                    onWatch = onWatch,
                                    onDownloadSeason = onDownloadSeasonRequest,
                                )
                            }
                        }
                        if (state.related.isNotEmpty()) {
                            item {
                                SectionTitle(
                                    "You might also like",
                                    Modifier.padding(
                                        start = RoachesSpacing.md,
                                        end = RoachesSpacing.md,
                                        top = RoachesSpacing.xl,
                                        bottom = RoachesSpacing.sm,
                                    ),
                                )
                            }
                            item {
                                LazyRow(
                                    contentPadding = PaddingValues(horizontal = RoachesSpacing.md),
                                    horizontalArrangement = Arrangement.spacedBy(RoachesSpacing.sm),
                                ) {
                                    items(state.related, key = MediaItem::id) { related ->
                                        PosterCard(related, { onOpenRelated(related) })
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } else {
            StateMessage("Title unavailable", "Return to Discover and choose the title again.")
            IconButton(onClick = onBack, modifier = Modifier.statusBarsPadding().padding(RoachesSpacing.xs)) {
                Icon(Icons.Rounded.ArrowBack, contentDescription = "Back")
            }
        }

        if (state.sourcePickerVisible) {
            ModalBottomSheet(
                onDismissRequest = onDismissSources,
                containerColor = RoachesColors.Surface,
                contentColor = RoachesColors.Ink,
                dragHandle = {
                    Box(
                        Modifier
                            .padding(vertical = RoachesSpacing.sm)
                            .width(32.dp)
                            .height(3.dp)
                            .background(RoachesColors.InkFaint, RoachesShapes.Tight),
                    )
                },
            ) {
                SourcePicker(state, onRetrySources, onPlay, onDownload, onDownloadSeason)
            }
        }
    }
}

@Composable
private fun DetailsHero(
    item: MediaItem,
    state: RoachesUiState,
    saved: Boolean,
    liked: Boolean,
    onBack: () -> Unit,
    onWatch: () -> Unit,
    onDownload: () -> Unit,
    onToggleSaved: () -> Unit,
    onToggleLiked: () -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val isWide = maxWidth > 700.dp
        Box(Modifier.fillMaxWidth().height(if (isWide) 560.dp else 510.dp)) {
        AsyncImage(
            model = item.backdropUrl ?: item.posterUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            alignment = Alignment.TopCenter,
            modifier = Modifier.fillMaxSize(),
        )
        ArtworkScrim(Modifier.fillMaxSize(), strong = true)
        Row(
            Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(RoachesSpacing.xs),
            horizontalArrangement = Arrangement.Start,
        ) {
            IconButton(onClick = onBack, modifier = Modifier.background(RoachesColors.Scrim, RoachesShapes.Tight)) {
                Icon(Icons.Rounded.ArrowBack, contentDescription = "Back")
            }
        }
        Column(
            Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth(if (isWide) 0.7f else 1f)
                .padding(horizontal = RoachesSpacing.md, vertical = RoachesSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(RoachesSpacing.sm),
        ) {
            Text(
                item.title,
                style = if (isWide) MaterialTheme.typography.displayLarge else MaterialTheme.typography.headlineLarge,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                listOfNotNull(
                    item.year.takeIf(String::isNotBlank),
                    item.kind.name,
                    state.details?.duration,
                    state.details?.audioLanguage,
                    item.rating?.let { "IMDb $it" },
                ).joinToString(" · "),
                style = MaterialTheme.typography.labelLarge,
                color = RoachesColors.InkMuted,
            )
            PrimaryWatchButton(onClick = onWatch)
            Row(horizontalArrangement = Arrangement.spacedBy(RoachesSpacing.xs)) {
                DetailAction(
                    icon = if (liked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                    label = if (liked) "Liked" else "Like",
                    active = liked,
                    onClick = onToggleLiked,
                )
                DetailAction(
                    icon = if (saved) Icons.Rounded.Bookmark else Icons.Rounded.BookmarkBorder,
                    label = if (saved) "Saved" else "Save",
                    active = saved,
                    onClick = onToggleSaved,
                )
                DetailAction(
                    icon = Icons.Rounded.Download,
                    label = "Download",
                    onClick = onDownload,
                )
            }
        }
        }
    }
}

@Composable
private fun DetailAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    active: Boolean = false,
    onClick: () -> Unit,
) {
    Column(
        Modifier
            .defaultMinSize(minWidth = 72.dp, minHeight = 56.dp)
            .clip(RoachesShapes.Tight)
            .clickable(onClick = onClick)
            .padding(horizontal = RoachesSpacing.xs, vertical = RoachesSpacing.xs),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = if (active) RoachesColors.Crawl else RoachesColors.Ink,
        )
        Text(label, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun EpisodePicker(
    state: RoachesUiState,
    onSeason: (Int) -> Unit,
    onEpisode: (Int) -> Unit,
    onWatch: () -> Unit,
    onDownloadSeason: () -> Unit,
) {
    val seasons = state.details?.seasons.orEmpty()
    val episodes = seasons.firstOrNull { it.number == state.selectedSeason }?.episodes.orEmpty()
    Column(
        Modifier.padding(top = RoachesSpacing.xl),
        verticalArrangement = Arrangement.spacedBy(RoachesSpacing.sm),
    ) {
        SectionTitle("Episodes", Modifier.padding(horizontal = RoachesSpacing.md))
        LazyRow(
            contentPadding = PaddingValues(horizontal = RoachesSpacing.md),
            horizontalArrangement = Arrangement.spacedBy(RoachesSpacing.xs),
        ) {
            items(seasons, key = { it.number }) { season ->
                SelectionButton(
                    label = "Season ${season.number}",
                    selected = season.number == state.selectedSeason,
                    onClick = { onSeason(season.number) },
                )
            }
        }
        LazyRow(
            contentPadding = PaddingValues(horizontal = RoachesSpacing.md),
            horizontalArrangement = Arrangement.spacedBy(RoachesSpacing.xs),
        ) {
            items(episodes, key = { it.number }) { episode ->
                SelectionButton(
                    label = episode.number.toString(),
                    selected = episode.number == state.selectedEpisode,
                    onClick = { onEpisode(episode.number) },
                )
            }
        }
        Button(
            onClick = onWatch,
            modifier = Modifier.padding(horizontal = RoachesSpacing.md).height(48.dp),
            shape = RoachesShapes.Tight,
            colors = ButtonDefaults.buttonColors(
                containerColor = RoachesColors.SurfaceQuiet,
                contentColor = RoachesColors.Ink,
            ),
        ) {
            Icon(Icons.Rounded.PlayArrow, contentDescription = null)
            Text("Play S${state.selectedSeason} E${state.selectedEpisode}")
        }
        TextButton(
            onClick = onDownloadSeason,
            modifier = Modifier.padding(horizontal = RoachesSpacing.md).height(48.dp),
            shape = RoachesShapes.Tight,
            colors = ButtonDefaults.textButtonColors(contentColor = RoachesColors.InkMuted),
        ) {
            Icon(Icons.Rounded.Download, contentDescription = null)
            Spacer(Modifier.width(RoachesSpacing.xs))
            Text("Download season ${state.selectedSeason}")
        }
    }
}

@Composable
private fun SelectionButton(label: String, selected: Boolean, onClick: () -> Unit) {
    TextButton(
        onClick = onClick,
        shape = RoachesShapes.Tight,
        colors = ButtonDefaults.textButtonColors(
            containerColor = if (selected) RoachesColors.Ink else RoachesColors.SurfaceQuiet,
            contentColor = if (selected) RoachesColors.Canvas else RoachesColors.InkMuted,
        ),
    ) { Text(label, style = MaterialTheme.typography.labelLarge) }
}

@Composable
private fun SourcePicker(
    state: RoachesUiState,
    onRetry: () -> Unit,
    onPlay: (StreamSource) -> Unit,
    onDownload: (StreamSource) -> Unit,
    onDownloadSeason: (StreamSource) -> Unit,
) {
    val downloading = state.sourceIntent != SourceIntent.Playback
    val seasonDownload = state.sourceIntent == SourceIntent.SeasonDownload
    val media = state.details?.item ?: state.detailsSeed
    val reportedAudio = state.sources.mapNotNull(StreamSource::audio).distinct()
    val audioLabel = when {
        reportedAudio.size == 1 -> reportedAudio.single()
        reportedAudio.size > 1 -> "Multiple provider labels"
        else -> state.details?.audioLanguage ?: "Not reported"
    }
    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(bottom = RoachesSpacing.xl),
    ) {
        Text(
            when {
                seasonDownload -> "Choose season download"
                downloading -> "Choose download"
                else -> "Choose playback"
            },
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(horizontal = RoachesSpacing.md, vertical = RoachesSpacing.sm),
        )
        Text(
            if (seasonDownload) {
                "Audio: $audioLabel. The closest matching quality will be used for each episode."
            } else {
                "Audio: $audioLabel. Change the preference in Settings."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = RoachesColors.InkMuted,
            modifier = Modifier.padding(horizontal = RoachesSpacing.md),
        )
        when {
            state.sourcesLoading -> LoadingState("Finding the best sources")
            state.sourcesError != null -> StateMessage(
                title = if (downloading) "Download unavailable" else "Playback unavailable",
                message = state.sourcesError,
                action = "Try again",
                onAction = onRetry,
            )
            else -> state.sources.groupBy(StreamSource::qualityLabel).forEach { (quality, sources) ->
                Text(
                    "$quality · ${sources.size} ${if (sources.size == 1) "source" else "sources"}",
                    style = MaterialTheme.typography.labelLarge,
                    color = RoachesColors.InkMuted,
                    modifier = Modifier.padding(
                        start = RoachesSpacing.md,
                        end = RoachesSpacing.md,
                        top = RoachesSpacing.md,
                    ),
                )
                sources.forEach { source ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable {
                                when (state.sourceIntent) {
                                    SourceIntent.Playback -> onPlay(source)
                                    SourceIntent.Download -> onDownload(source)
                                    SourceIntent.SeasonDownload -> onDownloadSeason(source)
                                }
                            }
                            .padding(
                                start = RoachesSpacing.md,
                                top = RoachesSpacing.sm,
                                bottom = RoachesSpacing.sm,
                            ),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        AsyncImage(
                            model = media?.posterUrl ?: media?.backdropUrl,
                            contentDescription = media?.title?.let { "$it thumbnail" },
                            contentScale = ContentScale.Crop,
                            placeholder = ColorPainter(RoachesColors.SurfaceQuiet),
                            error = ColorPainter(RoachesColors.SurfaceQuiet),
                            modifier = Modifier
                                .size(width = 52.dp, height = 72.dp)
                                .clip(RoachesShapes.Tight)
                                .background(RoachesColors.SurfaceQuiet),
                        )
                        Column(
                            Modifier.weight(1f).padding(horizontal = RoachesSpacing.sm),
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            Text(
                                source.audio?.takeIf(String::isNotBlank) ?: "Not reported",
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Text(
                                listOfNotNull(
                                    source.codec?.takeIf(String::isNotBlank)?.uppercase(),
                                    formatBytes(source.sizeBytes),
                                    formatSourceDuration(source.durationSeconds),
                                    source.uploader?.takeIf(String::isNotBlank),
                                    source.filename?.takeIf(String::isNotBlank)?.take(52),
                                ).take(3).ifEmpty { listOf("Provider source") }.joinToString(" · "),
                                style = MaterialTheme.typography.labelMedium,
                                color = RoachesColors.InkMuted,
                            )
                        }
                        if (downloading) {
                            Icon(
                                Icons.Rounded.Download,
                                contentDescription = "Download ${source.qualityLabel}",
                                modifier = Modifier.padding(RoachesSpacing.md),
                            )
                        } else {
                            IconButton(onClick = { onDownload(source) }) {
                                Icon(Icons.Rounded.Download, contentDescription = "Download ${source.qualityLabel}")
                            }
                        }
                    }
                    HorizontalDivider(color = RoachesColors.SurfaceQuiet)
                }
            }
        }
    }
}

private fun formatSourceDuration(seconds: Long?): String? {
    val total = seconds?.takeIf { it > 0L } ?: return null
    val hours = total / 3_600
    val minutes = total % 3_600 / 60
    return if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
}
