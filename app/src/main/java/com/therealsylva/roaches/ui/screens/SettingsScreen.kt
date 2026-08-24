package com.therealsylva.roaches.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.therealsylva.roaches.BuildConfig
import com.therealsylva.roaches.data.model.AppSettings
import com.therealsylva.roaches.data.model.ContentRegion
import com.therealsylva.roaches.data.model.PlaybackQuality
import com.therealsylva.roaches.ui.theme.RoachesColors
import com.therealsylva.roaches.ui.theme.RoachesSpacing

@Composable
fun SettingsScreen(
    settings: AppSettings,
    historyCount: Int,
    onBack: () -> Unit,
    onRegion: (ContentRegion) -> Unit,
    onQuality: (PlaybackQuality) -> Unit,
    onWifiOnly: (Boolean) -> Unit,
    onClearHistory: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxSize().statusBarsPadding()) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = RoachesSpacing.xxs, end = RoachesSpacing.md, top = RoachesSpacing.xs),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(RoachesSpacing.xs),
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Rounded.ArrowBack, contentDescription = "Back")
            }
            Text("Settings", style = MaterialTheme.typography.headlineMedium)
        }

        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = RoachesSpacing.md,
                end = RoachesSpacing.md,
                top = RoachesSpacing.lg,
                bottom = RoachesSpacing.xxl,
            ),
            verticalArrangement = Arrangement.spacedBy(RoachesSpacing.xl),
        ) {
            item {
                SettingsSection(
                    title = "Catalogue region",
                    description = "Sets the Discover feed. Search still covers the full catalogue.",
                ) {
                    ContentRegion.entries.forEach { region ->
                        ChoiceRow(
                            title = region.label,
                            selected = settings.contentRegion == region,
                            onClick = { onRegion(region) },
                        )
                    }
                }
            }

            item {
                SettingsSection(
                    title = "Playback quality",
                    description = "Roaches picks the best available source within this limit.",
                ) {
                    PlaybackQuality.entries.forEach { quality ->
                        ChoiceRow(
                            title = quality.label,
                            selected = settings.playbackQuality == quality,
                            onClick = { onQuality(quality) },
                        )
                    }
                }
            }

            item {
                SettingsSection(
                    title = "Downloads",
                    description = "Control when new offline files may use mobile data.",
                ) {
                    ToggleRow(
                        title = "Wi-Fi only",
                        checked = settings.wifiOnlyDownloads,
                        onChecked = onWifiOnly,
                    )
                }
            }

            item {
                SettingsSection(
                    title = "Privacy",
                    description = if (historyCount == 0) {
                        "No watch history is stored on this device."
                    } else {
                        "$historyCount watched ${if (historyCount == 1) "title" else "titles"} stored on this device."
                    },
                ) {
                    TextButton(
                        onClick = onClearHistory,
                        enabled = historyCount > 0,
                        contentPadding = PaddingValues(horizontal = 0.dp, vertical = RoachesSpacing.xs),
                    ) {
                        Text("Clear watch history")
                    }
                }
            }

            item {
                SettingsSection(
                    title = "About",
                    description = "Roaches ${BuildConfig.VERSION_NAME.removeSuffix("-dev")} · No ads or analytics.",
                ) {
                    Text(
                        "Preferences, library and viewing history stay on this device.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = RoachesColors.InkMuted,
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsSection(
    title: String,
    description: String,
    content: @Composable () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(RoachesSpacing.sm)) {
        Column(verticalArrangement = Arrangement.spacedBy(RoachesSpacing.xxs)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(description, style = MaterialTheme.typography.bodyMedium, color = RoachesColors.InkMuted)
        }
        HorizontalDivider(color = RoachesColors.SurfaceQuiet)
        Column { content() }
    }
}

@Composable
private fun ChoiceRow(title: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 56.dp)
            .clickable(role = Role.RadioButton, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(title, style = MaterialTheme.typography.bodyLarge)
        RadioButton(
            selected = selected,
            onClick = null,
            colors = RadioButtonDefaults.colors(
                selectedColor = RoachesColors.Crawl,
                unselectedColor = RoachesColors.InkFaint,
            ),
        )
    }
}

@Composable
private fun ToggleRow(title: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 56.dp)
            .clickable(role = Role.Switch) { onChecked(!checked) },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(title, style = MaterialTheme.typography.bodyLarge)
        Switch(
            checked = checked,
            onCheckedChange = null,
            colors = SwitchDefaults.colors(
                checkedThumbColor = RoachesColors.Canvas,
                checkedTrackColor = RoachesColors.Crawl,
                uncheckedThumbColor = RoachesColors.InkMuted,
                uncheckedTrackColor = RoachesColors.SurfaceQuiet,
                uncheckedBorderColor = RoachesColors.InkFaint,
            ),
        )
    }
}
