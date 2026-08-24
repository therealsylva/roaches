package com.therealsylva.roaches.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BrokenImage
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.therealsylva.roaches.R
import com.therealsylva.roaches.data.model.MediaItem
import com.therealsylva.roaches.ui.theme.RoachesColors
import com.therealsylva.roaches.ui.theme.RoachesShapes
import com.therealsylva.roaches.ui.theme.RoachesSpacing

@Composable
fun RoachesWordmark(modifier: Modifier = Modifier) {
    Image(
        painter = painterResource(R.drawable.roaches_mark),
        contentDescription = "Roaches",
        contentScale = ContentScale.Crop,
        modifier = modifier.size(44.dp).clip(RoachesShapes.Tight),
    )
}

@Composable
fun PosterCard(
    item: MediaItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    progress: Float? = null,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.975f else 1f, label = "poster-press")
    Column(
        modifier = modifier
            .width(136.dp)
            .scale(scale)
            .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onClick),
        verticalArrangement = Arrangement.spacedBy(RoachesSpacing.xs),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .clip(RoachesShapes.Tight)
                .background(RoachesColors.SurfaceQuiet)
                .semantics { contentDescription = "${item.title} poster" },
        ) {
            AsyncImage(
                model = item.posterUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                placeholder = ColorPainter(RoachesColors.SurfaceQuiet),
                error = ColorPainter(RoachesColors.SurfaceQuiet),
                modifier = Modifier.fillMaxSize(),
            )
            progress?.takeIf { it > 0f }?.let {
                Box(
                    Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth(it.coerceIn(0.03f, 1f))
                        .height(3.dp)
                        .background(RoachesColors.Crawl),
                )
            }
        }
        Text(
            text = item.title,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.labelLarge,
            color = RoachesColors.Ink,
        )
        val meta = listOfNotNull(item.year.takeIf(String::isNotBlank), item.kind.name).joinToString(" · ")
        Text(
            text = meta,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.labelMedium,
            color = RoachesColors.InkMuted,
        )
    }
}

@Composable
fun ArtworkScrim(modifier: Modifier = Modifier, strong: Boolean = false) {
    Box(
        modifier.background(
            Brush.verticalGradient(
                colorStops = arrayOf(
                    0f to Color.Transparent,
                    0.42f to Color.Transparent,
                    0.72f to Color.Black.copy(alpha = if (strong) 0.78f else 0.62f),
                    1f to RoachesColors.Canvas,
                ),
            ),
        ),
    )
}

@Composable
fun PrimaryWatchButton(text: String = "Watch", onClick: () -> Unit, modifier: Modifier = Modifier) {
    Button(
        onClick = onClick,
        modifier = modifier.height(52.dp),
        shape = RoachesShapes.Tight,
        colors = ButtonDefaults.buttonColors(
            containerColor = RoachesColors.Ink,
            contentColor = RoachesColors.Canvas,
        ),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = RoachesSpacing.lg),
    ) {
        Icon(Icons.Rounded.PlayArrow, contentDescription = null)
        Spacer(Modifier.width(RoachesSpacing.xs))
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
fun StateMessage(
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    action: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier.padding(horizontal = RoachesSpacing.lg, vertical = RoachesSpacing.xxl),
        horizontalAlignment = Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(RoachesSpacing.sm),
    ) {
        Icon(
            imageVector = Icons.Rounded.BrokenImage,
            contentDescription = null,
            tint = RoachesColors.InkFaint,
            modifier = Modifier.size(28.dp),
        )
        Text(title, style = MaterialTheme.typography.headlineMedium)
        Text(message, style = MaterialTheme.typography.bodyMedium, color = RoachesColors.InkMuted)
        if (action != null && onAction != null) {
            Spacer(Modifier.height(RoachesSpacing.xs))
            Button(
                onClick = onAction,
                shape = RoachesShapes.Tight,
                colors = ButtonDefaults.buttonColors(
                    containerColor = RoachesColors.Ink,
                    contentColor = RoachesColors.Canvas,
                ),
            ) { Text(action) }
        }
    }
}

@Composable
fun LoadingState(label: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.padding(RoachesSpacing.lg),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(RoachesSpacing.sm),
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(18.dp),
            color = RoachesColors.Crawl,
            strokeWidth = 2.dp,
        )
        Text(label, style = MaterialTheme.typography.bodyMedium, color = RoachesColors.InkMuted)
    }
}

@Composable
fun SectionTitle(title: String, modifier: Modifier = Modifier) {
    Text(
        text = title,
        style = MaterialTheme.typography.headlineMedium,
        modifier = modifier,
        color = RoachesColors.Ink,
    )
}

fun formatBytes(bytes: Long?): String? {
    if (bytes == null || bytes <= 0L) return null
    val gigabyte = 1024.0 * 1024.0 * 1024.0
    val megabyte = 1024.0 * 1024.0
    return if (bytes >= gigabyte) "%.1f GB".format(bytes / gigabyte) else "%.0f MB".format(bytes / megabyte)
}
