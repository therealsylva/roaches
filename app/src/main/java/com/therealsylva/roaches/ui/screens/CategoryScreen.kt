package com.therealsylva.roaches.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.therealsylva.roaches.data.model.BrowseCategory
import com.therealsylva.roaches.data.model.MediaItem
import com.therealsylva.roaches.ui.components.LoadingState
import com.therealsylva.roaches.ui.components.PosterCard
import com.therealsylva.roaches.ui.components.StateMessage
import com.therealsylva.roaches.ui.theme.RoachesSpacing

@Composable
fun CategoryScreen(
    category: BrowseCategory?,
    results: List<MediaItem>,
    loading: Boolean,
    error: String?,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onOpen: (MediaItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxSize().statusBarsPadding()) {
        androidx.compose.foundation.layout.Row(
            Modifier.fillMaxWidth().padding(horizontal = RoachesSpacing.xxs, vertical = RoachesSpacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Rounded.ArrowBack, contentDescription = "Back")
            }
            Text(category?.label ?: "Category", style = MaterialTheme.typography.headlineMedium)
        }
        when {
            loading -> LoadingState("Loading ${category?.label?.lowercase() ?: "titles"}")
            error != null && results.isEmpty() -> StateMessage(
                title = "Category unavailable",
                message = error,
                action = "Try again",
                onAction = onRetry,
            )
            else -> LazyVerticalGrid(
                columns = GridCells.Adaptive(136.dp),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = RoachesSpacing.md,
                    end = RoachesSpacing.md,
                    top = RoachesSpacing.sm,
                    bottom = RoachesSpacing.xl,
                ),
                horizontalArrangement = Arrangement.spacedBy(RoachesSpacing.sm),
                verticalArrangement = Arrangement.spacedBy(RoachesSpacing.lg),
            ) {
                items(results, key = MediaItem::id) { item ->
                    PosterCard(item = item, onClick = { onOpen(item) })
                }
            }
        }
    }
}
