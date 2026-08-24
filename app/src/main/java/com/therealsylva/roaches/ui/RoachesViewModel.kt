package com.therealsylva.roaches.ui

import android.app.Application
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.therealsylva.roaches.data.local.LocalStore
import com.therealsylva.roaches.data.model.AppSettings
import com.therealsylva.roaches.data.model.BrowseCategory
import com.therealsylva.roaches.data.model.ContentRegion
import com.therealsylva.roaches.data.model.DownloadEntry
import com.therealsylva.roaches.data.model.DownloadState
import com.therealsylva.roaches.data.model.LocalMediaEntry
import com.therealsylva.roaches.data.model.MediaDetails
import com.therealsylva.roaches.data.model.MediaItem
import com.therealsylva.roaches.data.model.PlaybackQuality
import com.therealsylva.roaches.data.model.PreferredAudio
import com.therealsylva.roaches.data.model.Shelf
import com.therealsylva.roaches.data.model.StreamSource
import com.therealsylva.roaches.data.model.SourceIntent
import com.therealsylva.roaches.data.model.WatchEntry
import com.therealsylva.roaches.data.remote.UpdateChecker
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
enum class AppScreen { Browse, Category, Details, Player, Settings }

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
    val category: BrowseCategory? = null,
    val categoryResults: List<MediaItem> = emptyList(),
    val categoryLoading: Boolean = false,
    val categoryError: String? = null,
    val details: MediaDetails? = null,
    val detailsSeed: MediaItem? = null,
    val detailsLoading: Boolean = false,
    val detailsError: String? = null,
    val related: List<MediaItem> = emptyList(),
    val selectedSeason: Int = 0,
    val selectedEpisode: Int = 0,
    val sourcePickerVisible: Boolean = false,
    val sourceIntent: SourceIntent = SourceIntent.Playback,
    val sources: List<StreamSource> = emptyList(),
    val sourcesLoading: Boolean = false,
    val sourcesError: String? = null,
    val playerMedia: MediaItem? = null,
    val playerSource: StreamSource? = null,
    val playerReturnDestination: MainDestination? = null,
    val captionsLoading: Boolean = false,
    val watchlist: List<MediaItem> = emptyList(),
    val liked: List<MediaItem> = emptyList(),
    val history: List<WatchEntry> = emptyList(),
    val downloads: List<DownloadEntry> = emptyList(),
    val localMedia: List<LocalMediaEntry> = emptyList(),
    val settings: AppSettings = AppSettings(),
    val updateLoading: Boolean = false,
    val updateMessage: String? = null,
    val updateUrl: String? = null,
    val notice: String? = null,
)

class RoachesViewModel(application: Application) : AndroidViewModel(application) {
    private val store = LocalStore(application)
    private var repository = RoachesRepository(store)
    private val updateChecker = UpdateChecker()
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
                            discoverError = if (shelves.isEmpty()) "No Home sections were returned." else null,
                        )
                    }
                }
                .onFailure { failure ->
                    mutableState.update {
                        it.copy(
                            discoverLoading = false,
                            discoverError = failure.userMessage("Home could not be loaded."),
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

    fun openCategory(category: BrowseCategory) {
        mutableState.update {
            it.copy(
                screen = AppScreen.Category,
                category = category,
                categoryResults = emptyList(),
                categoryLoading = true,
                categoryError = null,
            )
        }
        viewModelScope.launch {
            runCatching {
                (repository.search(category.query, 1) + repository.search(category.query, 2))
                    .distinctBy(MediaItem::id)
            }.onSuccess { results ->
                mutableState.update {
                    it.copy(
                        categoryResults = results,
                        categoryLoading = false,
                        categoryError = if (results.isEmpty()) "No ${category.label.lowercase()} titles were found." else null,
                    )
                }
            }.onFailure { failure ->
                mutableState.update {
                    it.copy(
                        categoryLoading = false,
                        categoryError = failure.userMessage("This category could not be loaded."),
                    )
                }
            }
        }
    }

    fun retryCategory() {
        mutableState.value.category?.let(::openCategory)
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

    fun setPreferredAudio(audio: PreferredAudio) {
        if (audio == mutableState.value.settings.preferredAudio) return
        store.setPreferredAudio(audio)
        repository = RoachesRepository(store)
        mutableState.update { it.copy(settings = store.settings(), notice = "Preferred audio updated") }
    }

    fun setWifiOnlyDownloads(enabled: Boolean) {
        store.setWifiOnlyDownloads(enabled)
        mutableState.update { it.copy(settings = store.settings()) }
    }

    fun setDarkTheme(enabled: Boolean) {
        store.setDarkTheme(enabled)
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
                related = emptyList(),
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
                    loadRelated(details)
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

    fun requestSources(intent: SourceIntent = SourceIntent.Playback) {
        val current = mutableState.value
        val media = current.details?.item ?: current.detailsSeed ?: return
        mutableState.update {
            it.copy(
                sourcePickerVisible = true,
                sourceIntent = intent,
                sourcesLoading = true,
                sourcesError = null,
                sources = emptyList(),
            )
        }
        viewModelScope.launch {
            runCatching {
                repository.sources(
                    media.id,
                    current.selectedSeason,
                    current.selectedEpisode,
                    current.details?.audioLanguage,
                )
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

    fun retrySources() {
        requestSources(mutableState.value.sourceIntent)
    }

    fun play(source: StreamSource) {
        val media = mutableState.value.details?.item ?: mutableState.value.detailsSeed ?: return
        mutableState.update {
            it.copy(
                sourcePickerVisible = false,
                playerMedia = media,
                playerSource = source,
                captionsLoading = true,
                playerReturnDestination = null,
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

    fun toggleLiked() {
        val media = mutableState.value.details?.item ?: mutableState.value.detailsSeed ?: return
        val liked = store.toggleLiked(media)
        refreshLocal()
        mutableState.update { it.copy(notice = if (liked) "Added to liked" else "Removed from liked") }
    }

    fun isLiked(item: MediaItem?): Boolean = item != null && mutableState.value.liked.any { it.id == item.id }

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

    fun playDownload(entry: DownloadEntry) {
        if (entry.state != DownloadState.Complete || entry.localUri.isNullOrBlank()) {
            mutableState.update { it.copy(notice = "This download is not ready yet") }
            return
        }
        mutableState.update {
            it.copy(
                playerMedia = entry.media,
                playerSource = entry.source.copy(url = entry.localUri),
                playerReturnDestination = MainDestination.Downloads,
                captionsLoading = false,
                screen = AppScreen.Player,
            )
        }
    }

    fun importLocalMedia(uriValue: String) {
        val uri = Uri.parse(uriValue)
        val resolver = getApplication<Application>().contentResolver
        val displayName = runCatching {
            resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
        }.getOrNull()?.substringBeforeLast('.')?.takeIf(String::isNotBlank) ?: "Local video"
        store.addLocalMedia(uriValue, displayName)
        refreshLocal()
        mutableState.update { it.copy(notice = "Local video added") }
    }

    fun removeLocalMedia(entry: LocalMediaEntry) {
        store.removeLocalMedia(entry)
        refreshLocal()
    }

    fun playLocalMedia(entry: LocalMediaEntry) {
        val media = MediaItem(
            id = "local:${entry.id}",
            title = entry.title,
            kind = com.therealsylva.roaches.data.model.MediaKind.Movie,
            description = "Local media",
        )
        mutableState.update {
            it.copy(
                playerMedia = media,
                playerSource = StreamSource(entry.id, entry.uri, filename = entry.title),
                playerReturnDestination = MainDestination.Library,
                captionsLoading = false,
                screen = AppScreen.Player,
            )
        }
    }

    fun refreshDownloads() {
        mutableState.update { it.copy(downloads = store.downloads()) }
    }

    fun recordProgress(positionMs: Long, durationMs: Long, force: Boolean = false) {
        val media = mutableState.value.playerMedia ?: return
        if (media.id.startsWith("local:")) return
        val now = System.currentTimeMillis()
        if (!force && now - lastProgressWrite < 5_000) return
        lastProgressWrite = now
        store.updateProgress(media, positionMs, durationMs)
        if (force) refreshLocal()
    }

    fun dismissNotice() {
        mutableState.update { it.copy(notice = null) }
    }

    fun checkForUpdates() {
        if (mutableState.value.updateLoading) return
        mutableState.update { it.copy(updateLoading = true, updateMessage = null, updateUrl = null) }
        viewModelScope.launch {
            runCatching { updateChecker.check() }
                .onSuccess { release ->
                    mutableState.update {
                        it.copy(
                            updateLoading = false,
                            updateMessage = if (release.available) {
                                "Roaches ${release.versionName} is ready."
                            } else {
                                "Roaches is up to date."
                            },
                            updateUrl = if (release.available) release.apkUrl ?: release.releaseUrl else null,
                        )
                    }
                }
                .onFailure { failure ->
                    mutableState.update {
                        it.copy(
                            updateLoading = false,
                            updateMessage = failure.userMessage("Updates could not be checked right now."),
                        )
                    }
                }
        }
    }

    fun goBack(): Boolean {
        return when (mutableState.value.screen) {
            AppScreen.Player -> {
                mutableState.update {
                    val returnDestination = it.playerReturnDestination
                    if (returnDestination == null) {
                        it.copy(screen = AppScreen.Details, playerSource = null)
                    } else {
                        it.copy(
                            screen = AppScreen.Browse,
                            destination = returnDestination,
                            playerSource = null,
                            playerReturnDestination = null,
                        )
                    }
                }
                true
            }
            AppScreen.Details -> {
                mutableState.update { it.copy(screen = AppScreen.Browse, details = null, detailsSeed = null) }
                true
            }
            AppScreen.Category -> {
                mutableState.update { it.copy(screen = AppScreen.Browse, category = null) }
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
                liked = store.liked(),
                history = store.history(),
                downloads = store.downloads(),
                localMedia = store.localMedia(),
                settings = store.settings(),
            )
        }
    }

    private fun loadRelated(details: MediaDetails) {
        val local = mutableState.value.shelves
            .flatMap(Shelf::items)
            .filter { it.id != details.item.id && it.kind == details.item.kind }
            .distinctBy(MediaItem::id)
            .take(12)
        mutableState.update { it.copy(related = local) }
        val query = details.genres.firstOrNull()?.takeIf(String::isNotBlank) ?: return
        viewModelScope.launch {
            val remote = runCatching { repository.search(query) }.getOrDefault(emptyList())
            mutableState.update { current ->
                if (current.details?.item?.id != details.item.id) current else current.copy(
                    related = (remote + local)
                        .filter { it.id != details.item.id }
                        .distinctBy(MediaItem::id)
                        .take(18),
                )
            }
        }
    }
}

private fun Throwable.userMessage(fallback: String): String {
    val value = message?.trim().orEmpty()
    return value.takeIf { it.length in 4..180 && !it.contains("http", ignoreCase = true) } ?: fallback
}
