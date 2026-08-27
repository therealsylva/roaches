package com.therealsylva.roaches.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.therealsylva.roaches.BuildConfig
import com.therealsylva.roaches.data.model.AppSettings
import com.therealsylva.roaches.data.model.ContentRegion
import com.therealsylva.roaches.data.model.PlaybackQuality
import com.therealsylva.roaches.data.model.PreferredAudio
import com.therealsylva.roaches.data.remote.UpdateChecker
import com.therealsylva.roaches.ui.components.RoachesWordmark
import com.therealsylva.roaches.ui.theme.RoachesColors
import com.therealsylva.roaches.ui.theme.RoachesSpacing

@Composable
fun SettingsScreen(
    settings: AppSettings,
    historyCount: Int,
    updateLoading: Boolean,
    updateMessage: String?,
    updateAvailable: Boolean,
    onRegion: (ContentRegion) -> Unit,
    onQuality: (PlaybackQuality) -> Unit,
    onAudio: (PreferredAudio) -> Unit,
    onWifiOnly: (Boolean) -> Unit,
    onDarkTheme: (Boolean) -> Unit,
    onEggsKey: (String) -> Unit,
    onEggsOff: () -> Unit,
    onClearHistory: () -> Unit,
    onCheckUpdates: () -> Unit,
    onInstallUpdate: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val uriHandler = LocalUriHandler.current
    var eggsPromptVisible by rememberSaveable { mutableStateOf(false) }
    var eggsKey by rememberSaveable { mutableStateOf("") }

    if (eggsPromptVisible) {
        AlertDialog(
            onDismissRequest = {
                eggsPromptVisible = false
                eggsKey = ""
            },
            title = { Text("Enable eggs") },
            text = {
                OutlinedTextField(
                    value = eggsKey,
                    onValueChange = { value -> eggsKey = value.take(32) },
                    label = { Text("Key") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    visualTransformation = PasswordVisualTransformation(),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onEggsKey(eggsKey)
                        eggsPromptVisible = false
                        eggsKey = ""
                    },
                    enabled = eggsKey.isNotBlank(),
                ) {
                    Text("Enable")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        eggsPromptVisible = false
                        eggsKey = ""
                    },
                ) {
                    Text("Cancel")
                }
            },
        )
    }

    Column(modifier.fillMaxSize().statusBarsPadding()) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = RoachesSpacing.md, vertical = RoachesSpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(RoachesSpacing.sm),
        ) {
            Text("Settings", style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.weight(1f))
            RoachesWordmark()
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
                    title = "Home feed region",
                    description = "Chooses what Roaches puts first on Home. Search stays global.",
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
                    title = "Audio language",
                    description = "Roaches prefers this version of a title when the provider offers it.",
                ) {
                    PreferredAudio.entries.forEach { audio ->
                        ChoiceRow(
                            title = audio.label,
                            selected = settings.preferredAudio == audio,
                            onClick = { onAudio(audio) },
                        )
                    }
                }
            }

            item {
                SettingsSection(
                    title = "Appearance",
                    description = "Use the light palette when dark screens are uncomfortable.",
                ) {
                    ToggleRow(
                        title = "Dark theme",
                        checked = settings.darkTheme,
                        onChecked = onDarkTheme,
                    )
                }
            }

            item {
                SettingsSection(
                    title = "Extras",
                    description = "Optional catalogue controls.",
                ) {
                    ToggleRow(
                        title = "Enable eggs",
                        checked = settings.eggsEnabled,
                        onChecked = { enabled ->
                            if (enabled) {
                                eggsKey = ""
                                eggsPromptVisible = true
                            } else {
                                onEggsOff()
                            }
                        },
                    )
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
                    title = "Updates",
                    description = "Checks published Roaches releases on GitHub.",
                ) {
                    TextButton(
                        onClick = onCheckUpdates,
                        enabled = !updateLoading,
                        contentPadding = PaddingValues(horizontal = 0.dp, vertical = RoachesSpacing.xs),
                    ) {
                        Text(if (updateLoading && !updateAvailable) "Checking…" else "Check for updates")
                    }
                    updateMessage?.let { message ->
                        Text(message, style = MaterialTheme.typography.bodyMedium, color = RoachesColors.InkMuted)
                    }
                    if (updateAvailable) {
                        TextButton(
                            onClick = onInstallUpdate,
                            enabled = !updateLoading,
                            contentPadding = PaddingValues(horizontal = 0.dp, vertical = RoachesSpacing.xs),
                        ) {
                            Text(if (updateLoading) "Preparing update…" else "Download and install")
                        }
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
                    TextButton(
                        onClick = { uriHandler.openUri(UpdateChecker.REPOSITORY_URL) },
                        contentPadding = PaddingValues(horizontal = 0.dp, vertical = RoachesSpacing.xs),
                    ) {
                        Text("GitHub · therealsylva")
                    }
                    TextButton(
                        onClick = { uriHandler.openUri("https://x.com/sylva_es") },
                        contentPadding = PaddingValues(horizontal = 0.dp, vertical = RoachesSpacing.xs),
                    ) {
                        Text("X · @sylva_es")
                    }
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
