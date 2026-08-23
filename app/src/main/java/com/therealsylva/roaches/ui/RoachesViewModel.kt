package com.therealsylva.roaches.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.therealsylva.roaches.data.local.LocalStore
import com.therealsylva.roaches.data.model.AppSettings
import com.therealsylva.roaches.data.model.ContentRegion
import com.therealsylva.roaches.data.model.DownloadEntry
import com.therealsylva.roaches.data.model.MediaDetails
import com.therealsylva.roaches.data.model.MediaItem
import com.therealsylva.roaches.data.model.PlaybackQuality
import com.therealsylva.roaches.data.model.Shelf
import com.therealsylva.roaches.data.model.StreamSource
import com.therealsylva.roaches.data.model.WatchEntry
import com.therealsylva.roaches.data.repository.RoachesRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class MainDestination { Discover, Search, Library, Downloads }
enum class AppScreen { Browse, Details, Player, Settings }

data class RoachesUiState(
    val destination: MainDestination = MainDestination.Discover,
    val screen: AppScreen = AppScreen.Browse,
    val shelves: List<Shelf> = emptyList(),
    val discoverLoading: Boolean = true,
    val discoverError: String? = null,
    val searchQuery: String = "",
    val searchResults: List<MediaItem> = emptyList(),
    val searchLoading: Boolean = false,
    val searchError: String? = null,
    val details: MediaDetails? = null,
    val detailsSeed: MediaItem? = null,
    val detailsLoading: Boolean = false,
    val detailsError: String? = null,
    val selectedSeason: Int = 0,
    val selectedEpisode: Int = 0,
    val sourcePickerVisible: Boolean = false,
    val sources: List<StreamSource> = emptyList(),
    val sourcesLoading: Boolean = false,
    val sourcesError: String? = null,
    val playerMedia: MediaItem? = null,
    val playerSource: StreamSource? = null,
    val captionsLoading: Boolean = false,
    val watchlist: List<MediaItem> = emptyList(),
    val history: List<WatchEntry> = emptyList(),
    val downloads: List<DownloadEntry> = emptyList(),
    val settings: AppSettings = AppSettings(),
    val notice: String? = null,
)

class RoachesViewModel(application: Application) : AndroidViewModel(application) {
    private val store = LocalStore(application)
    private var repository = RoachesRepository(store)
    private val mutableState = MutableStateFlow(RoachesUiState())
    val state: StateFlow<RoachesUiState> = mutableState.asStateFlow()

    private var searchJob: Job? = null
    private var lastProgressWrite = 0L

    init {
        refreshLocal()
        loadDiscover()
    }

    fun setDestination(destination: MainDestination) {
        mutableState.update { it.copy(destination = destination, screen = AppScreen.Browse) }
        if (destination == MainDestination.Downloads) refreshDownloads()
    }

    fun loadDiscover() {
        if (mutableState.value.discoverLoading && mutableState.value.shelves.isNotEmpty()) return
        mutableState.update { it.copy(discoverLoading = true, discoverError = null) }
        viewModelScope.launch {
            runCatching { repository.discover() }
                .onSuccess { shelves ->
                    mutableState.update {
                        it.copy(
                            shelves = shelves,
                            discoverLoading = false,
                            discoverError = if (shelves.isEmpty()) "No catalogue sections were returned." else null,
                        )
                    }
                }
                .onFailure { failure ->
                    mutableState.update {
                        it.copy(
                            discoverLoading = false,
                            discoverError = failure.userMessage("The catalogue could not be loaded."),
                        )
                    }
                }
        }
    }

    fun updateSearch(query: String) {
        mutableState.update {
            it.copy(
                searchQuery = query,
                searchResults = if (query.isBlank()) emptyList() else it.searchResults,
                searchLoading = query.isNotBlank(),
                searchError = null,
            )
        }
        searchJob?.cancel()
        if (query.isBlank()) return
        searchJob = viewModelScope.launch {
            delay(320)
            performSearch(query.trim())
        }
    }

    fun submitSearch() {
        val query = mutableState.value.searchQuery.trim()
        if (query.isBlank()) return
        searchJob?.cancel()
        mutableState.update { it.copy(searchLoading = true, searchError = null) }
        searchJob = viewModelScope.launch { performSearch(query) }
    }

    private suspend fun performSearch(expected: String) {
        try {
            val results = repository.search(expected)
            if (mutableState.value.searchQuery.trim() == expected) {
                mutableState.update { it.copy(searchResults = results, searchLoading = false) }
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (failure: Throwable) {
            if (mutableState.value.searchQuery.trim() == expected) {
                mutableState.update {
                    it.copy(
                        searchLoading = false,
                        searchError = failure.userMessage("Search is temporarily unavailable."),
                    )
                }
            }
        }
    }

    fun openSettings() {
        mutableState.update { it.copy(screen = AppScreen.Settings) }
    }

    fun setContentRegion(region: ContentRegion) {
        if (region == mutableState.value.settings.contentRegion) return
        store.setContentRegion(region)
        repository = RoachesRepository(store)
        searchJob?.cancel()
        mutableState.update {
            it.copy(
                settings = store.settings(),
                shelves = emptyList(),
                discoverLoading = false,
                discoverError = null,
                searchQuery = "",
                searchResults = emptyList(),
                searchLoading = false,
                searchError = null,
            )
        }
        loadDiscover()
    }

    fun setPlaybackQuality(quality: PlaybackQuality) {
        if (quality == mutableState.value.settings.playbackQuality) return
        store.setPlaybackQuality(quality)
        repository = RoachesRepository(store)
        mutableState.update { it.copy(settings = store.settings(), notice = "Playback quality updated") }
    }

    fun setWifiOnlyDownloads(enabled: Boolean) {
        store.setWifiOnlyDownloads(enabled)
        mutableState.update { it.copy(settings = store.settings()) }
    }

    fun clearHistory() {
        store.clearHistory()
        refreshLocal()
        mutableState.update { it.copy(notice = "Watch history cleared") }
    }

    fun openDetails(item: MediaItem) {
        mutableState.update {
            it.copy(
                screen = AppScreen.Details,
                details = null,
                detailsSeed = item,
                detailsLoading = true,
                detailsError = null,
                sources = emptyList(),
                sourcesError = null,
                sourcePickerVisible = false,
            )
        }
        viewModelScope.launch {
            runCatching { repository.details(item) }
                .onSuccess { details ->
                    val firstSeason = details.seasons.firstOrNull()
                    mutableState.update {
                        it.copy(
                            details = details,
                            detailsLoading = false,
                            selectedSeason = firstSeason?.number ?: 0,
                            selectedEpisode = firstSeason?.episodes?.firstOrNull()?.number ?: 0,
                        )
                    }
                }
                .onFailure { failure ->
                    mutableState.update {
                        it.copy(
                            detailsLoading = false,
                            detailsError = failure.userMessage("Details could not be loaded."),
                        )
                    }
                }
        }
    }

    fun retryDetails() {
        mutableState.value.detailsSeed?.let(::openDetails)
    }

    fun selectSeason(number: Int) {
        val firstEpisode = mutableState.value.details?.seasons
            ?.firstOrNull { it.number == number }
            ?.episodes
            ?.firstOrNull()
            ?.number ?: 1
        mutableState.update { it.copy(selectedSeason = number, selectedEpisode = firstEpisode) }
    }

    fun selectEpisode(number: Int) {
        mutableState.update { it.copy(selectedEpisode = number) }
    }

    fun requestSources() {
        val current = mutableState.value
        val media = current.details?.item ?: current.detailsSeed ?: return
        mutableState.update {
            it.copy(
                sourcePickerVisible = true,
                sourcesLoading = true,
                sourcesError = null,
                sources = emptyList(),
            )
        }
        viewModelScope.launch {
            runCatching {
                repository.sources(media.id, current.selectedSeason, current.selectedEpisode)
            }.onSuccess { sources ->
                mutableState.update {
                    it.copy(
                        sources = sources,
                        sourcesLoading = false,
                        sourcesError = if (sources.isEmpty()) "No playable source is available for this title." else null,
                    )
                }
            }.onFailure { failure ->
                mutableState.update {
                    it.copy(
                        sourcesLoading = false,
                        sourcesError = failure.userMessage("Sources could not be loaded."),
                    )
                }
            }
        }
    }

    fun dismissSourcePicker() {
        mutableState.update { it.copy(sourcePickerVisible = false) }
    }

    fun play(source: StreamSource) {
        val media = mutableState.value.details?.item ?: mutableState.value.detailsSeed ?: return
        mutableState.update {
            it.copy(
                sourcePickerVisible = false,
                playerMedia = media,
                playerSource = source,
                captionsLoading = true,
                screen = AppScreen.Player,
            )
        }
        viewModelScope.launch {
            val captions = runCatching { repository.captions(media.id, source) }.getOrDefault(emptyList())
            mutableState.update {
                if (it.playerSource?.url == source.url) {
                    it.copy(playerSource = source.copy(subtitles = captions), captionsLoading = false)
                } else {
                    it
                }
            }
        }
    }

    fun toggleSaved() {
        val media = mutableState.value.details?.item ?: mutableState.value.detailsSeed ?: return
        val saved = store.toggleSaved(media)
        refreshLocal()
        mutableState.update { it.copy(notice = if (saved) "Added to library" else "Removed from library") }
    }

    fun isSaved(item: MediaItem?): Boolean = item != null && mutableState.value.watchlist.any { it.id == item.id }

    fun download(source: StreamSource) {
        val media = mutableState.value.details?.item ?: mutableState.value.detailsSeed ?: return
        runCatching { store.enqueueDownload(media, source) }
            .onSuccess {
                refreshDownloads()
                mutableState.update { state -> state.copy(sourcePickerVisible = false, notice = "Download started") }
            }
            .onFailure { failure ->
                mutableState.update { state -> state.copy(notice = failure.userMessage("Download could not start.")) }
            }
    }

    fun removeDownload(entry: DownloadEntry) {
        store.removeDownload(entry)
        refreshDownloads()
    }

    fun refreshDownloads() {
        mutableState.update { it.copy(downloads = store.downloads()) }
    }

    fun recordProgress(positionMs: Long, durationMs: Long, force: Boolean = false) {
        val media = mutableState.value.playerMedia ?: return
        val now = System.currentTimeMillis()
        if (!force && now - lastProgressWrite < 5_000) return
        lastProgressWrite = now
        store.updateProgress(media, positionMs, durationMs)
        if (force) refreshLocal()
    }

    fun dismissNotice() {
        mutableState.update { it.copy(notice = null) }
    }

    fun goBack(): Boolean {
        return when (mutableState.value.screen) {
            AppScreen.Player -> {
                mutableState.update { it.copy(screen = AppScreen.Details, playerSource = null) }
                true
            }
            AppScreen.Details -> {
                mutableState.update { it.copy(screen = AppScreen.Browse, details = null, detailsSeed = null) }
                true
            }
            AppScreen.Settings -> {
                mutableState.update { it.copy(screen = AppScreen.Browse) }
                true
            }
            AppScreen.Browse -> false
        }
    }

    private fun refreshLocal() {
        mutableState.update {
            it.copy(
                watchlist = store.watchlist(),
                history = store.history(),
                downloads = store.downloads(),
                settings = store.settings(),
            )
        }
    }
}

private fun Throwable.userMessage(fallback: String): String {
    val value = message?.trim().orEmpty()
    return value.takeIf { it.length in 4..180 && !it.contains("http", ignoreCase = true) } ?: fallback
}
