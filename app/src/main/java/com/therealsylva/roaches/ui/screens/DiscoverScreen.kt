package com.therealsylva.roaches.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowForward
import androidx.compose.material.icons.rounded.SportsSoccer
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.therealsylva.roaches.data.model.BrowseCategory
import com.therealsylva.roaches.data.model.MediaItem
import com.therealsylva.roaches.ui.RoachesUiState
import com.therealsylva.roaches.ui.components.ArtworkScrim
import com.therealsylva.roaches.ui.components.LoadingState
import com.therealsylva.roaches.ui.components.PosterCard
import com.therealsylva.roaches.ui.components.RoachesWordmark
import com.therealsylva.roaches.ui.components.SectionTitle
import com.therealsylva.roaches.ui.components.StateMessage
import com.therealsylva.roaches.ui.theme.RoachesColors
import com.therealsylva.roaches.ui.theme.RoachesShapes
import com.therealsylva.roaches.ui.theme.RoachesSpacing
import kotlinx.coroutines.delay

@Composable
fun DiscoverScreen(
    state: RoachesUiState,
    onRetry: () -> Unit,
    onCategory: (BrowseCategory) -> Unit,
    onOpen: (MediaItem) -> Unit,
    onSports: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val featuredItems = remember(state.shelves) {
        state.shelves.flatMap { it.items.take(4) }.distinctBy(MediaItem::id).take(10)
    }
    var featuredIndex by remember { mutableIntStateOf(0) }
    LaunchedEffect(featuredItems) {
        featuredIndex = 0
        while (featuredItems.size > 1) {
            delay(7_500)
            featuredIndex = (featuredIndex + 1) % featuredItems.size
        }
    }
    LazyColumn(modifier = modifier, contentPadding = PaddingValues(bottom = RoachesSpacing.xl)) {
        item(key = "hero") {
            if (featuredItems.isNotEmpty()) {
                AnimatedContent(
                    targetState = featuredItems[featuredIndex.coerceIn(featuredItems.indices)],
                    transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(180)) },
                    label = "featured-title",
                ) { featured ->
                    DiscoverHero(featured, onOpen, onSports)
                }
            } else {
                Column(Modifier.statusBarsPadding().padding(top = RoachesSpacing.lg)) {
                    DiscoverHeader(onSports, Modifier.padding(horizontal = RoachesSpacing.md))
                    if (state.discoverLoading) {
                        LoadingState("Opening Home", Modifier.padding(top = 140.dp))
                    } else {
                        StateMessage(
                            title = "Nothing to show yet",
                            message = state.discoverError ?: "Home returned no titles.",
                            action = "Try again",
                            onAction = onRetry,
                        )
                    }
                }
            }
        }

        item(key = "genre-heading") {
            SectionTitle("Browse by genre", Modifier.padding(horizontal = RoachesSpacing.md, vertical = RoachesSpacing.lg))
        }
        item(key = "genre-rail") {
            LazyRow(
                contentPadding = PaddingValues(horizontal = RoachesSpacing.md),
                horizontalArrangement = Arrangement.spacedBy(RoachesSpacing.xs),
            ) {
                item(key = "sports") {
                    TextButton(
                        onClick = onSports,
                        shape = RoachesShapes.Tight,
                        colors = ButtonDefaults.textButtonColors(
                            containerColor = RoachesColors.SurfaceQuiet,
                            contentColor = RoachesColors.Ink,
                        ),
                        modifier = Modifier.height(48.dp),
                    ) {
                        Text("Sports", style = MaterialTheme.typography.labelLarge)
                    }
                }
                items(BrowseCategory.entries, key = BrowseCategory::name) { category ->
                    TextButton(
                        onClick = { onCategory(category) },
                        shape = RoachesShapes.Tight,
                        colors = ButtonDefaults.textButtonColors(
                            containerColor = RoachesColors.SurfaceQuiet,
                            contentColor = RoachesColors.Ink,
                        ),
                        modifier = Modifier.height(48.dp),
                    ) {
                        Text(category.label, style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }

        if (state.history.isNotEmpty()) {
            item(key = "continue-heading") {
                SectionTitle("Continue watching", Modifier.padding(RoachesSpacing.md))
            }
            item(key = "continue-rail") {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = RoachesSpacing.md),
                    horizontalArrangement = Arrangement.spacedBy(RoachesSpacing.sm),
                ) {
                    items(state.history.take(12), key = { it.media.id }) { entry ->
                        PosterCard(entry.media, { onOpen(entry.media) }, progress = entry.progress)
                    }
                }
            }
            item { Spacer(Modifier.height(RoachesSpacing.lg)) }
        }

        state.shelves.forEachIndexed { index, shelf ->
            if (shelf.items.isNotEmpty()) {
                item(key = "${shelf.id}-heading") {
                    SectionTitle(
                        title = shelf.title,
                        modifier = Modifier.padding(
                            start = RoachesSpacing.md,
                            end = RoachesSpacing.md,
                            top = if (index == 0) RoachesSpacing.lg else RoachesSpacing.xl,
                            bottom = RoachesSpacing.sm,
                        ),
                    )
                }
                item(key = shelf.id) {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = RoachesSpacing.md),
                        horizontalArrangement = Arrangement.spacedBy(RoachesSpacing.sm),
                    ) {
                        items(shelf.items, key = { it.id }) { item -> PosterCard(item, { onOpen(item) }) }
                    }
                }
            }
        }
    }
}

@Composable
private fun DiscoverHero(item: MediaItem, onOpen: (MediaItem) -> Unit, onSports: () -> Unit) {
    BoxWithConstraints(
        Modifier
            .fillMaxWidth()
            .height(if (item.description.isNullOrBlank()) 490.dp else 530.dp),
    ) {
        val isWide = maxWidth > 700.dp
        AsyncImage(
            model = item.backdropUrl ?: item.posterUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            alignment = Alignment.TopCenter,
            modifier = Modifier.fillMaxSize(),
        )
        ArtworkScrim(Modifier.fillMaxSize(), strong = true)
        DiscoverHeader(
            onSports = onSports,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(horizontal = RoachesSpacing.md, vertical = RoachesSpacing.sm),
        )
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(horizontal = RoachesSpacing.md, vertical = RoachesSpacing.lg)
                .fillMaxWidth(if (isWide) 0.6f else 0.9f),
            verticalArrangement = Arrangement.spacedBy(RoachesSpacing.sm),
        ) {
            Text(
                text = item.title,
                style = if (isWide) MaterialTheme.typography.displayLarge else MaterialTheme.typography.headlineLarge,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = listOfNotNull(
                    item.year.takeIf(String::isNotBlank),
                    item.kind.name,
                    item.rating?.let { "IMDb $it" },
                ).joinToString(" · "),
                style = MaterialTheme.typography.labelLarge,
                color = RoachesColors.InkMuted,
            )
            item.description?.takeIf(String::isNotBlank)?.let { synopsis ->
                Text(
                    synopsis,
                    style = MaterialTheme.typography.bodyMedium,
                    color = RoachesColors.InkMuted,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Row {
                Button(
                    onClick = { onOpen(item) },
                    shape = RoachesShapes.Tight,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = RoachesColors.Ink,
                        contentColor = RoachesColors.Canvas,
                    ),
                    contentPadding = PaddingValues(horizontal = RoachesSpacing.lg),
                    modifier = Modifier.height(52.dp),
                ) {
                    Text("View title", style = MaterialTheme.typography.labelLarge)
                    Icon(Icons.Rounded.ArrowForward, contentDescription = null)
                }
            }
        }
    }
}

@Composable
private fun DiscoverHeader(onSports: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RoachesWordmark()
        Spacer(Modifier.weight(1f))
        TextButton(
            onClick = onSports,
            colors = ButtonDefaults.textButtonColors(contentColor = RoachesColors.Ink),
        ) {
            Icon(Icons.Rounded.SportsSoccer, contentDescription = null, modifier = Modifier.size(20.dp))
            Text("Live sports", modifier = Modifier.padding(start = RoachesSpacing.xs))
        }
    }
}
