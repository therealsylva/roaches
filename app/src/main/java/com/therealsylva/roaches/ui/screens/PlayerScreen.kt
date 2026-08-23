package com.therealsylva.roaches.ui.screens

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Forward10
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Replay10
import androidx.compose.material.icons.rounded.Subtitles
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.therealsylva.roaches.data.model.MediaItem as RoachesMedia
import com.therealsylva.roaches.data.model.StreamSource
import com.therealsylva.roaches.data.model.SubtitleTrack
import com.therealsylva.roaches.ui.components.LoadingState
import com.therealsylva.roaches.ui.theme.RoachesColors
import com.therealsylva.roaches.ui.theme.RoachesShapes
import com.therealsylva.roaches.ui.theme.RoachesSpacing
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun PlayerScreen(
    media: RoachesMedia?,
    source: StreamSource?,
    captionsLoading: Boolean,
    resumePositionMs: Long,
    onBack: () -> Unit,
    onProgress: (Long, Long, Boolean) -> Unit,
    onPlayerMode: (Boolean) -> Unit,
    isInPictureInPicture: () -> Boolean,
) {
    if (media == null || source == null) {
        Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
            LoadingState("Preparing playback")
        }
        return
    }

    val context = LocalContext.current
    LocalConfiguration.current
    val player = remember {
        ExoPlayer.Builder(context).build().apply {
            playWhenReady = true
        }
    }
    val mediaSession = remember { MediaSession.Builder(context, player).build() }
    var selectedSubtitle by remember { mutableStateOf<SubtitleTrack?>(null) }
    var showSubtitlePicker by remember { mutableStateOf(false) }
    var controlsVisible by remember { mutableStateOf(true) }
    var playing by remember { mutableStateOf(false) }
    var buffering by remember { mutableStateOf(true) }
    var positionMs by remember { mutableLongStateOf(resumePositionMs) }
    var durationMs by remember { mutableLongStateOf(0L) }
    var sliderPosition by remember { mutableFloatStateOf(0f) }
    var dragging by remember { mutableStateOf(false) }
    var playerError by remember { mutableStateOf<String?>(null) }
    val inPip = isInPictureInPicture()

    fun loadMedia(subtitle: SubtitleTrack?, preservePosition: Boolean) {
        val currentPosition = if (preservePosition) player.currentPosition else resumePositionMs
        val configuration = subtitle?.let {
            MediaItem.SubtitleConfiguration.Builder(Uri.parse(it.url))
                .setLabel(it.label)
                .setLanguage(languageCode(it.label))
                .setMimeType(subtitleMime(it.url))
                .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
                .build()
        }
        val item = MediaItem.Builder()
            .setUri(source.url)
            .setMediaId(media.id)
            .setMediaMetadata(
                androidx.media3.common.MediaMetadata.Builder()
                    .setTitle(media.title)
                    .setArtworkUri(media.posterUrl?.let(Uri::parse))
                    .build(),
            )
            .setSubtitleConfigurations(listOfNotNull(configuration))
            .build()
        player.setMediaItem(item, currentPosition.coerceAtLeast(0L))
        player.prepare()
        player.playWhenReady = true
    }

    LaunchedEffect(source.url) { loadMedia(null, preservePosition = false) }

    DisposableEffect(player) {
        onPlayerMode(true)
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                playing = isPlaying
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                buffering = playbackState == Player.STATE_BUFFERING || playbackState == Player.STATE_IDLE
                durationMs = player.duration.takeIf { it > 0L } ?: durationMs
            }

            override fun onPlayerError(error: PlaybackException) {
                playerError = "This source could not be played. Choose another quality."
                buffering = false
            }
        }
        player.addListener(listener)
        onDispose {
            onProgress(player.currentPosition.coerceAtLeast(0L), player.duration.coerceAtLeast(0L), true)
            player.removeListener(listener)
            mediaSession.release()
            player.release()
            onPlayerMode(false)
        }
    }

    LaunchedEffect(player) {
        while (true) {
            positionMs = player.currentPosition.coerceAtLeast(0L)
            durationMs = player.duration.takeIf { it > 0L } ?: durationMs
            if (!dragging && durationMs > 0L) {
                sliderPosition = (positionMs.toFloat() / durationMs).coerceIn(0f, 1f)
            }
            onProgress(positionMs, durationMs, false)
            delay(500)
        }
    }

    LaunchedEffect(controlsVisible, playing, dragging) {
        if (controlsVisible && playing && !dragging) {
            delay(3_800)
            controlsVisible = false
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(Unit) {
                detectTapGestures(onTap = { controlsVisible = !controlsVisible })
            },
    ) {
        AndroidView(
            factory = { viewContext ->
                PlayerView(viewContext).apply {
                    useController = false
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    keepScreenOn = true
                    this.player = player
                }
            },
            update = { it.player = player },
            modifier = Modifier.fillMaxSize(),
        )

        if (buffering && playerError == null) {
            CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center).size(36.dp),
                color = RoachesColors.Ink,
                strokeWidth = 2.dp,
            )
        }

        if (controlsVisible && !inPip) {
            PlayerControls(
                mediaTitle = media.title,
                quality = source.qualityLabel,
                playing = playing,
                positionMs = positionMs,
                durationMs = durationMs,
                sliderPosition = sliderPosition,
                captionsLoading = captionsLoading,
                subtitleLabel = selectedSubtitle?.label,
                error = playerError,
                onBack = onBack,
                onToggle = { if (player.isPlaying) player.pause() else player.play() },
                onReplay = { player.seekTo((player.currentPosition - 10_000).coerceAtLeast(0L)) },
                onForward = {
                    val maximum = player.duration.takeIf { it > 0L } ?: Long.MAX_VALUE
                    player.seekTo((player.currentPosition + 10_000).coerceAtMost(maximum))
                },
                onSlider = {
                    dragging = true
                    sliderPosition = it
                },
                onSliderFinished = {
                    player.seekTo((durationMs * sliderPosition).toLong())
                    dragging = false
                },
                onSubtitles = { showSubtitlePicker = true },
            )
        }
    }

    if (showSubtitlePicker) {
        ModalBottomSheet(
            onDismissRequest = { showSubtitlePicker = false },
            containerColor = RoachesColors.Surface,
            contentColor = RoachesColors.Ink,
        ) {
            Column(Modifier.fillMaxWidth().padding(bottom = RoachesSpacing.xl)) {
                Text(
                    "Subtitles",
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.padding(RoachesSpacing.md),
                )
                SubtitleOption("Off", selectedSubtitle == null) {
                    selectedSubtitle = null
                    loadMedia(null, preservePosition = true)
                    showSubtitlePicker = false
                }
                source.subtitles.forEach { track ->
                    SubtitleOption(track.label, selectedSubtitle?.url == track.url) {
                        selectedSubtitle = track
                        loadMedia(track, preservePosition = true)
                        showSubtitlePicker = false
                    }
                }
                if (captionsLoading) LoadingState("Loading subtitle tracks")
                if (!captionsLoading && source.subtitles.isEmpty()) {
                    Text(
                        "No external subtitles were reported for this source.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = RoachesColors.InkMuted,
                        modifier = Modifier.padding(RoachesSpacing.md),
                    )
                }
            }
        }
    }
}

@Composable
private fun PlayerControls(
    mediaTitle: String,
    quality: String,
    playing: Boolean,
    positionMs: Long,
    durationMs: Long,
    sliderPosition: Float,
    captionsLoading: Boolean,
    subtitleLabel: String?,
    error: String?,
    onBack: () -> Unit,
    onToggle: () -> Unit,
    onReplay: () -> Unit,
    onForward: () -> Unit,
    onSlider: (Float) -> Unit,
    onSliderFinished: () -> Unit,
    onSubtitles: () -> Unit,
) {
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.52f))) {
        Row(
            Modifier.fillMaxWidth().padding(RoachesSpacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(RoachesSpacing.sm),
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Rounded.ArrowBack, contentDescription = "Close player")
            }
            Column(Modifier.weight(1f)) {
                Text(
                    mediaTitle,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                )
                Text(quality, style = MaterialTheme.typography.labelMedium, color = RoachesColors.InkMuted)
            }
            IconButton(onClick = onSubtitles, enabled = !captionsLoading) {
                Icon(
                    Icons.Rounded.Subtitles,
                    contentDescription = subtitleLabel?.let { "Subtitles: $it" } ?: "Choose subtitles",
                    tint = if (subtitleLabel != null) RoachesColors.Crawl else RoachesColors.Ink,
                )
            }
        }

        Row(
            Modifier.align(Alignment.Center),
            horizontalArrangement = Arrangement.spacedBy(RoachesSpacing.xl),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PlayerAction(Icons.Rounded.Replay10, "Replay 10 seconds", onReplay)
            IconButton(
                onClick = onToggle,
                modifier = Modifier.size(72.dp).background(RoachesColors.Ink, CircleShape),
            ) {
                Icon(
                    if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    contentDescription = if (playing) "Pause" else "Play",
                    tint = RoachesColors.Canvas,
                    modifier = Modifier.size(38.dp),
                )
            }
            PlayerAction(Icons.Rounded.Forward10, "Forward 10 seconds", onForward)
        }

        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(RoachesSpacing.md),
        ) {
            error?.let {
                Text(it, color = RoachesColors.Error, style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(RoachesSpacing.xs))
            }
            Slider(
                value = sliderPosition,
                onValueChange = onSlider,
                onValueChangeFinished = onSliderFinished,
                colors = SliderDefaults.colors(
                    thumbColor = RoachesColors.Ink,
                    activeTrackColor = RoachesColors.Crawl,
                    inactiveTrackColor = RoachesColors.InkFaint,
                ),
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(formatTime(positionMs), style = MaterialTheme.typography.labelMedium)
                Text(formatTime(durationMs), style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@Composable
private fun PlayerAction(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.size(56.dp)) {
        Icon(icon, contentDescription = label, modifier = Modifier.size(32.dp))
    }
}

@Composable
private fun SubtitleOption(label: String, selected: Boolean, onClick: () -> Unit) {
    TextButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().height(52.dp).padding(horizontal = RoachesSpacing.md),
        shape = RoachesShapes.Tight,
        colors = ButtonDefaults.textButtonColors(
            contentColor = if (selected) RoachesColors.Crawl else RoachesColors.Ink,
        ),
    ) {
        Text(label, modifier = Modifier.weight(1f))
        if (selected) Text("Selected", style = MaterialTheme.typography.labelMedium)
    }
}

private fun formatTime(valueMs: Long): String {
    val seconds = (valueMs.coerceAtLeast(0L) / 1_000).toInt()
    val hours = seconds / 3_600
    val minutes = (seconds % 3_600) / 60
    val remainder = seconds % 60
    return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, remainder) else "%d:%02d".format(minutes, remainder)
}

private fun subtitleMime(url: String): String = when (url.substringBefore('?').substringAfterLast('.').lowercase()) {
    "vtt" -> MimeTypes.TEXT_VTT
    "ass", "ssa" -> MimeTypes.TEXT_SSA
    else -> MimeTypes.APPLICATION_SUBRIP
}

private fun languageCode(label: String): String? = when (label.lowercase()) {
    "english", "eng", "en" -> "en"
    "french", "fra", "fr" -> "fr"
    "spanish", "spa", "es" -> "es"
    "hindi", "hin", "hi" -> "hi"
    "arabic", "ara", "ar" -> "ar"
    else -> null
}
