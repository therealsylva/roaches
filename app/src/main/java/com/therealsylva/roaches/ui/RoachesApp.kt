package com.therealsylva.roaches.ui

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.core.view.WindowCompat
import com.therealsylva.roaches.ui.screens.DetailsScreen
import com.therealsylva.roaches.ui.screens.CategoryScreen
import com.therealsylva.roaches.ui.screens.DiscoverScreen
import com.therealsylva.roaches.ui.screens.DownloadsScreen
import com.therealsylva.roaches.ui.screens.LibraryScreen
import com.therealsylva.roaches.ui.screens.PlayerScreen
import com.therealsylva.roaches.ui.screens.SearchScreen
import com.therealsylva.roaches.ui.screens.SettingsScreen
import com.therealsylva.roaches.ui.theme.RoachesColors
import com.therealsylva.roaches.ui.theme.RoachesTheme
import com.therealsylva.roaches.data.model.SourceIntent
import kotlinx.coroutines.delay

@Composable
fun RoachesApp(
    viewModel: RoachesViewModel = viewModel(),
    onPlayerMode: (Boolean) -> Unit,
    isInPictureInPicture: () -> Boolean,
    sharedText: String? = null,
    onSharedTextConsumed: () -> Unit = {},
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val view = LocalView.current
    SideEffect {
        (view.context as? Activity)?.window?.let { window ->
            val darkBars = state.settings.darkTheme || state.screen == AppScreen.Player
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkBars
            WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = !darkBars
        }
    }

    RoachesTheme(darkTheme = state.settings.darkTheme) {
        BackHandler(enabled = state.screen != AppScreen.Browse) {
            viewModel.goBack()
        }

        LaunchedEffect(state.notice) {
            if (state.notice != null) {
                delay(2_600)
                viewModel.dismissNotice()
            }
        }

        LaunchedEffect(sharedText) {
            sharedText?.let {
                viewModel.openLinkSave(it)
                onSharedTextConsumed()
            }
        }

        CompositionLocalProvider(LocalContentColor provides RoachesColors.Ink) {
            Box(Modifier.fillMaxSize().background(RoachesColors.Canvas)) {
                when (state.screen) {
                    AppScreen.Player -> RoachesTheme(darkTheme = true) {
                        PlayerScreen(
                            media = state.playerMedia,
                            source = state.playerSource,
                            captionsLoading = state.captionsLoading,
                            resumePositionMs = state.history
                                .firstOrNull { it.media.id == state.playerMedia?.id }
                                ?.positionMs
                                ?: 0L,
                            onBack = { viewModel.goBack() },
                            onProgress = viewModel::recordProgress,
                            onPlayerMode = onPlayerMode,
                            isInPictureInPicture = isInPictureInPicture,
                        )
                    }
                    AppScreen.Category -> CategoryScreen(
                        category = state.category,
                        results = state.categoryResults,
                        loading = state.categoryLoading,
                        error = state.categoryError,
                        onBack = { viewModel.goBack() },
                        onRetry = viewModel::retryCategory,
                        onOpen = viewModel::openDetails,
                    )
                    AppScreen.Details -> DetailsScreen(
                        state = state,
                        saved = viewModel.isSaved(state.details?.item ?: state.detailsSeed),
                        liked = viewModel.isLiked(state.details?.item ?: state.detailsSeed),
                        onBack = { viewModel.goBack() },
                        onRetry = viewModel::retryDetails,
                        onWatch = { viewModel.requestSources(SourceIntent.Playback) },
                        onDownloadRequest = { viewModel.requestSources(SourceIntent.Download) },
                        onDownloadSeasonRequest = { viewModel.requestSources(SourceIntent.SeasonDownload) },
                        onToggleSaved = viewModel::toggleSaved,
                        onToggleLiked = viewModel::toggleLiked,
                        onSeason = viewModel::selectSeason,
                        onEpisode = viewModel::selectEpisode,
                        onDismissSources = viewModel::dismissSourcePicker,
                        onRetrySources = viewModel::retrySources,
                        onPlay = viewModel::play,
                        onDownload = viewModel::download,
                        onDownloadSeason = viewModel::downloadSeason,
                        onOpenRelated = viewModel::openDetails,
                    )
                    AppScreen.Settings -> SettingsScreen(
                        settings = state.settings,
                        historyCount = state.history.size,
                        updateLoading = state.updateLoading,
                        updateMessage = state.updateMessage,
                        updateAvailable = state.availableUpdate != null,
                        onBack = { viewModel.goBack() },
                        onRegion = viewModel::setContentRegion,
                        onQuality = viewModel::setPlaybackQuality,
                        onAudio = viewModel::setPreferredAudio,
                        onWifiOnly = viewModel::setWifiOnlyDownloads,
                        onDarkTheme = viewModel::setDarkTheme,
                        onEggsKey = viewModel::enableEggs,
                        onEggsOff = viewModel::disableEggs,
                        onClearHistory = viewModel::clearHistory,
                        onCheckUpdates = viewModel::checkForUpdates,
                        onInstallUpdate = viewModel::installUpdate,
                    )
                    AppScreen.Browse -> BrowseShell(state, viewModel)
                }

                state.notice?.let { notice ->
                    Snackbar(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .navigationBarsPadding()
                            .padding(bottom = if (state.screen == AppScreen.Browse) 72.dp else 18.dp),
                        containerColor = RoachesColors.Ink,
                        contentColor = RoachesColors.Canvas,
                    ) { Text(notice, style = MaterialTheme.typography.labelLarge) }
                }
            }
        }
    }
}

@Composable
private fun BrowseShell(state: RoachesUiState, viewModel: RoachesViewModel) {
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = RoachesColors.Canvas,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            RoachesNavigation(
                selected = state.destination,
                onSelect = viewModel::setDestination,
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(bottom = padding.calculateBottomPadding())) {
            when (state.destination) {
                MainDestination.Discover -> DiscoverScreen(
                    state = state,
                    onRetry = viewModel::loadDiscover,
                    onSettings = viewModel::openSettings,
                    onCategory = viewModel::openCategory,
                    onOpen = viewModel::openDetails,
                    modifier = Modifier.fillMaxSize(),
                )
                MainDestination.Search -> SearchScreen(
                    query = state.searchQuery,
                    results = state.searchResults,
                    loading = state.searchLoading,
                    error = state.searchError,
                    suggestions = state.shelves.firstOrNull()?.items.orEmpty(),
                    onQuery = viewModel::updateSearch,
                    onSubmit = viewModel::submitSearch,
                    onOpen = viewModel::openDetails,
                    modifier = Modifier.fillMaxSize(),
                )
                MainDestination.Library -> LibraryScreen(
                    watchlist = state.watchlist,
                    liked = state.liked,
                    history = state.history,
                    localMedia = state.localMedia,
                    onImport = viewModel::importLocalMedia,
                    onPlayLocal = viewModel::playLocalMedia,
                    onRemoveLocal = viewModel::removeLocalMedia,
                    onOpen = viewModel::openDetails,
                    modifier = Modifier.fillMaxSize(),
                )
                MainDestination.Downloads -> DownloadsScreen(
                    downloads = state.downloads,
                    seasonDownloads = state.seasonDownloads,
                    linkSaveVisible = state.linkSaveVisible,
                    linkSaveUrl = state.linkSaveUrl,
                    linkDownloadMode = state.linkDownloadMode,
                    linkVideoQuality = state.linkVideoQuality,
                    linkAudioBitrate = state.linkAudioBitrate,
                    linkSaveLoading = state.linkSaveLoading,
                    linkSaveError = state.linkSaveError,
                    linkPicker = state.linkPicker,
                    cobaltChallengeSiteKey = state.cobaltChallengeSiteKey,
                    cobaltChallengeNonce = state.cobaltChallengeNonce,
                    linkRetryTitle = state.linkRetryTitle,
                    onRefresh = viewModel::refreshDownloads,
                    onOpenLinkSave = { viewModel.openLinkSave() },
                    onDismissLinkSave = viewModel::dismissLinkSave,
                    onLinkUrl = viewModel::updateLinkSaveUrl,
                    onLinkMode = viewModel::setLinkDownloadMode,
                    onLinkVideoQuality = viewModel::setLinkVideoQuality,
                    onLinkAudioBitrate = viewModel::setLinkAudioBitrate,
                    onSubmitLink = viewModel::submitLinkSave,
                    onChallengeToken = viewModel::completeCobaltChallenge,
                    onChallengeError = viewModel::cobaltChallengeFailed,
                    onRetryChallenge = viewModel::retryCobaltChallenge,
                    onSavePickerFile = viewModel::saveCobaltPickerFile,
                    onSaveAllPickerFiles = viewModel::saveAllCobaltPickerFiles,
                    onRemove = viewModel::removeDownload,
                    onPlay = viewModel::playDownload,
                    onRetry = viewModel::retryDownload,
                    onCancelSeason = viewModel::cancelSeasonDownload,
                    onRetrySeason = viewModel::retrySeasonDownload,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

private data class NavItem(
    val destination: MainDestination,
    val label: String,
    val icon: ImageVector,
)

@Composable
private fun RoachesNavigation(selected: MainDestination, onSelect: (MainDestination) -> Unit) {
    val items = listOf(
        NavItem(MainDestination.Discover, "Home", Icons.Rounded.Home),
        NavItem(MainDestination.Search, "Search", Icons.Rounded.Search),
        NavItem(MainDestination.Library, "Library", Icons.Rounded.VideoLibrary),
        NavItem(MainDestination.Downloads, "Downloads", Icons.Rounded.Download),
    )
    Row(
        Modifier
            .background(RoachesColors.Canvas.copy(alpha = 0.98f))
            .navigationBarsPadding()
            .height(64.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        items.forEach { item ->
            val active = item.destination == selected
            NavigationBarItem(
                selected = active,
                onClick = { onSelect(item.destination) },
                icon = {
                    Box(contentAlignment = Alignment.TopCenter) {
                        if (active) {
                            Box(
                                Modifier
                                    .align(Alignment.TopCenter)
                                    .padding(bottom = 28.dp)
                                    .height(2.dp)
                                    .background(RoachesColors.Crawl),
                            )
                        }
                        Icon(item.icon, contentDescription = item.label)
                    }
                },
                label = { Text(item.label) },
                modifier = Modifier.semantics {
                    this.selected = active
                    role = Role.Tab
                },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = RoachesColors.Ink,
                    selectedTextColor = RoachesColors.Ink,
                    unselectedIconColor = RoachesColors.InkFaint,
                    unselectedTextColor = RoachesColors.InkFaint,
                    indicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                ),
            )
        }
    }
}
