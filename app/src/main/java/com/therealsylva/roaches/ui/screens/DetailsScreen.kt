package com.therealsylva.roaches.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Download
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.therealsylva.roaches.data.model.MediaItem
import com.therealsylva.roaches.data.model.StreamSource
import com.therealsylva.roaches.ui.RoachesUiState
import com.therealsylva.roaches.ui.components.ArtworkScrim
import com.therealsylva.roaches.ui.components.LoadingState
import com.therealsylva.roaches.ui.components.PrimaryWatchButton
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
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onWatch: () -> Unit,
    onToggleSaved: () -> Unit,
    onSeason: (Int) -> Unit,
    onEpisode: (Int) -> Unit,
    onDismissSources: () -> Unit,
    onPlay: (StreamSource) -> Unit,
    onDownload: (StreamSource) -> Unit,
) {
    val item = state.details?.item ?: state.detailsSeed
    Box(Modifier.fillMaxSize().background(RoachesColors.Canvas)) {
        if (item != null) {
            LazyColumn(contentPadding = PaddingValues(bottom = RoachesSpacing.xxl)) {
                item(key = "details-hero") {
                    DetailsHero(item, state, saved, onBack, onWatch, onToggleSaved)
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
                                )
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
                SourcePicker(state, onWatch, onPlay, onDownload)
            }
        }
    }
}

@Composable
private fun DetailsHero(
    item: MediaItem,
    state: RoachesUiState,
    saved: Boolean,
    onBack: () -> Unit,
    onWatch: () -> Unit,
    onToggleSaved: () -> Unit,
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
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            IconButton(onClick = onBack, modifier = Modifier.background(RoachesColors.Scrim, RoachesShapes.Tight)) {
                Icon(Icons.Rounded.ArrowBack, contentDescription = "Back")
            }
            IconButton(
                onClick = onToggleSaved,
                modifier = Modifier.background(RoachesColors.Scrim, RoachesShapes.Tight),
            ) {
                Icon(
                    if (saved) Icons.Rounded.Check else Icons.Rounded.Add,
                    contentDescription = if (saved) "Remove from library" else "Add to library",
                )
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
                    item.rating?.let { "IMDb $it" },
                ).joinToString(" · "),
                style = MaterialTheme.typography.labelLarge,
                color = RoachesColors.InkMuted,
            )
            PrimaryWatchButton(onClick = onWatch)
        }
        }
    }
}

@Composable
private fun EpisodePicker(
    state: RoachesUiState,
    onSeason: (Int) -> Unit,
    onEpisode: (Int) -> Unit,
    onWatch: () -> Unit,
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
) {
    Column(Modifier.fillMaxWidth().padding(bottom = RoachesSpacing.xl)) {
        Text(
            "Choose playback",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(horizontal = RoachesSpacing.md, vertical = RoachesSpacing.sm),
        )
        Text(
            "Quality and language are shown exactly as the provider reports them.",
            style = MaterialTheme.typography.bodyMedium,
            color = RoachesColors.InkMuted,
            modifier = Modifier.padding(horizontal = RoachesSpacing.md),
        )
        when {
            state.sourcesLoading -> LoadingState("Finding the best sources")
            state.sourcesError != null -> StateMessage(
                title = "Playback unavailable",
                message = state.sourcesError,
                action = "Try again",
                onAction = onRetry,
            )
            else -> state.sources.forEach { source ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { onPlay(source) }
                        .padding(start = RoachesSpacing.md, top = RoachesSpacing.sm, bottom = RoachesSpacing.sm),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier
                            .size(46.dp)
                            .clip(RoachesShapes.Tight)
                            .background(RoachesColors.SurfaceQuiet),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(source.qualityLabel, style = MaterialTheme.typography.labelLarge)
                    }
                    Column(
                        Modifier.weight(1f).padding(horizontal = RoachesSpacing.sm),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Text(source.technicalLabel, style = MaterialTheme.typography.titleMedium)
                        Text(
                            formatBytes(source.sizeBytes) ?: "Stream",
                            style = MaterialTheme.typography.labelMedium,
                            color = RoachesColors.InkMuted,
                        )
                    }
                    IconButton(onClick = { onDownload(source) }) {
                        Icon(Icons.Rounded.Download, contentDescription = "Download ${source.qualityLabel}")
                    }
                }
                HorizontalDivider(color = RoachesColors.SurfaceQuiet)
            }
        }
    }
}
