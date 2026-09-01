package com.therealsylva.roaches.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Sports
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.therealsylva.roaches.data.model.SportType
import com.therealsylva.roaches.data.model.SportsMatch
import com.therealsylva.roaches.data.model.SportsMatchStatus
import com.therealsylva.roaches.ui.RoachesUiState
import com.therealsylva.roaches.ui.components.LoadingState
import com.therealsylva.roaches.ui.components.StateMessage
import com.therealsylva.roaches.ui.theme.RoachesColors
import com.therealsylva.roaches.ui.theme.RoachesShapes
import com.therealsylva.roaches.ui.theme.RoachesSpacing
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun SportsScreen(
    state: RoachesUiState,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onSport: (SportType) -> Unit,
    onOpen: (SportsMatch) -> Unit,
) {
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Row(
            Modifier.fillMaxWidth().height(64.dp).padding(horizontal = RoachesSpacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) { Icon(Icons.Rounded.ArrowBack, contentDescription = "Back") }
            Icon(
                Icons.Rounded.Sports,
                contentDescription = null,
                tint = RoachesColors.Crawl,
                modifier = Modifier.size(22.dp),
            )
            Text(
                "Live sports",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(start = RoachesSpacing.xs),
            )
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onRefresh, enabled = !state.sportsLoading) {
                Icon(Icons.Rounded.Refresh, contentDescription = "Refresh matches")
            }
        }

        Row(
            Modifier.fillMaxWidth().padding(horizontal = RoachesSpacing.md),
            horizontalArrangement = Arrangement.spacedBy(RoachesSpacing.xs),
        ) {
            SportType.entries.forEach { sport ->
                val selected = sport == state.sportType
                TextButton(
                    onClick = { onSport(sport) },
                    shape = RoachesShapes.Tight,
                    colors = ButtonDefaults.textButtonColors(
                        containerColor = if (selected) RoachesColors.Ink else RoachesColors.SurfaceQuiet,
                        contentColor = if (selected) RoachesColors.Canvas else RoachesColors.Ink,
                    ),
                ) { Text(sport.label, style = MaterialTheme.typography.labelLarge) }
            }
        }

        when {
            state.sportsLoading && state.sportsLeagues.isEmpty() -> LoadingState("Loading today's matches")
            state.sportsLeagues.isEmpty() -> StateMessage(
                title = "No matches found",
                message = state.sportsError ?: "There are no fixtures listed for today.",
                action = "Try again",
                onAction = onRefresh,
            )
            else -> LazyColumn(
                contentPadding = PaddingValues(
                    start = RoachesSpacing.md,
                    end = RoachesSpacing.md,
                    top = RoachesSpacing.lg,
                    bottom = RoachesSpacing.xl,
                ),
            ) {
                state.sportsLeagues.forEachIndexed { leagueIndex, league ->
                    item(key = "league:$leagueIndex:${league.name}") {
                        Text(
                            league.name,
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(top = RoachesSpacing.sm, bottom = RoachesSpacing.xs),
                        )
                    }
                    items(league.matches, key = { "$leagueIndex:${it.id}" }) { match ->
                        MatchRow(
                            match = match,
                            opening = state.sportsOpeningId == match.id,
                            enabled = state.sportsOpeningId == null,
                            onOpen = { onOpen(match) },
                        )
                        HorizontalDivider(color = RoachesColors.SurfaceQuiet)
                    }
                }
            }
        }
    }
}

@Composable
private fun MatchRow(match: SportsMatch, opening: Boolean, enabled: Boolean, onOpen: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(enabled = enabled, onClick = onOpen).padding(vertical = RoachesSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(RoachesSpacing.sm)) {
            TeamLine(match.home.name, match.home.avatarUrl, match.home.score)
            TeamLine(match.away.name, match.away.avatarUrl, match.away.score)
        }
        Box(Modifier.padding(start = RoachesSpacing.md), contentAlignment = Alignment.Center) {
            if (opening) {
                CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
            } else {
                val label = when (match.status) {
                    SportsMatchStatus.Live -> "LIVE"
                    SportsMatchStatus.Ended -> if (match.isPlayable) "REPLAY" else "FT"
                    SportsMatchStatus.Scheduled -> formatKickoff(match.startTimeMs)
                }
                Text(
                    label,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (match.status == SportsMatchStatus.Live) RoachesColors.Crawl else RoachesColors.InkMuted,
                    fontWeight = if (match.status == SportsMatchStatus.Live) FontWeight.Bold else FontWeight.Medium,
                )
            }
        }
    }
}

@Composable
private fun TeamLine(name: String, avatarUrl: String?, score: String?) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        AsyncImage(
            model = avatarUrl,
            contentDescription = null,
            modifier = Modifier.size(28.dp).clip(RoachesShapes.Tight),
        )
        Text(
            name,
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = RoachesSpacing.sm).weight(1f),
        )
        score?.let {
            Text(it, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = RoachesSpacing.sm))
        }
    }
}

private fun formatKickoff(timestampMs: Long): String {
    if (timestampMs <= 0L) return "UPCOMING"
    return DateTimeFormatter.ofPattern("HH:mm")
        .withZone(ZoneId.systemDefault())
        .format(Instant.ofEpochMilli(timestampMs))
}
