package com.therealsylva.roaches.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.therealsylva.roaches.data.model.MediaItem
import com.therealsylva.roaches.ui.components.LoadingState
import com.therealsylva.roaches.ui.components.PosterCard
import com.therealsylva.roaches.ui.components.SectionTitle
import com.therealsylva.roaches.ui.components.StateMessage
import com.therealsylva.roaches.ui.theme.RoachesColors
import com.therealsylva.roaches.ui.theme.RoachesShapes
import com.therealsylva.roaches.ui.theme.RoachesSpacing

@Composable
fun SearchScreen(
    query: String,
    results: List<MediaItem>,
    loading: Boolean,
    error: String?,
    suggestions: List<MediaItem>,
    onQuery: (String) -> Unit,
    onOpen: (MediaItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.statusBarsPadding()) {
        Text(
            "Search",
            style = MaterialTheme.typography.headlineLarge,
            modifier = Modifier.padding(
                start = RoachesSpacing.md,
                end = RoachesSpacing.md,
                top = RoachesSpacing.lg,
                bottom = RoachesSpacing.md,
            ),
        )
        SearchField(query, onQuery)

        when {
            loading && results.isEmpty() -> LoadingState("Searching", Modifier.padding(top = RoachesSpacing.xl))
            error != null -> StateMessage(
                title = "Search paused",
                message = error,
                action = "Try again",
                onAction = { onQuery(query) },
            )
            query.isNotBlank() && results.isEmpty() -> StateMessage(
                title = "No matches",
                message = "Try a shorter title, another spelling or the original release name.",
            )
            else -> {
                val visible = if (query.isBlank()) suggestions else results
                Column(Modifier.fillMaxSize()) {
                    SectionTitle(
                        if (query.isBlank()) "Popular searches" else "${results.size} results",
                        Modifier.padding(
                            start = RoachesSpacing.md,
                            end = RoachesSpacing.md,
                            top = RoachesSpacing.lg,
                            bottom = RoachesSpacing.sm,
                        ),
                    )
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(132.dp),
                        contentPadding = PaddingValues(
                            start = RoachesSpacing.md,
                            end = RoachesSpacing.md,
                            bottom = RoachesSpacing.xl,
                        ),
                        horizontalArrangement = Arrangement.spacedBy(RoachesSpacing.sm),
                        verticalArrangement = Arrangement.spacedBy(RoachesSpacing.lg),
                    ) {
                        items(visible, key = { it.id }) { item ->
                            PosterCard(item, { onOpen(item) }, Modifier.fillMaxWidth())
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchField(query: String, onQuery: (String) -> Unit) {
    val focusManager = LocalFocusManager.current
    BasicTextField(
        value = query,
        onValueChange = onQuery,
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyLarge.copy(color = RoachesColors.Ink),
        cursorBrush = SolidColor(RoachesColors.Crawl),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = RoachesSpacing.md)
            .height(54.dp),
        decorationBox = { inner ->
            Row(
                Modifier
                    .fillMaxSize()
                    .background(RoachesColors.SurfaceQuiet, RoachesShapes.Tight)
                    .padding(start = RoachesSpacing.md),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(RoachesSpacing.sm),
            ) {
                Icon(Icons.Rounded.Search, contentDescription = null, tint = RoachesColors.InkMuted)
                Box(Modifier.weight(1f)) {
                    if (query.isBlank()) {
                        Text(
                            "Title, actor or series",
                            color = RoachesColors.InkFaint,
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                    inner()
                }
                if (query.isNotBlank()) {
                    IconButton(onClick = { onQuery("") }) {
                        Icon(Icons.Rounded.Close, contentDescription = "Clear search")
                    }
                }
            }
        },
    )
}
