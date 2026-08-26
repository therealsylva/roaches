package com.therealsylva.roaches.ui

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.therealsylva.roaches.data.local.EggsGate
import com.therealsylva.roaches.data.local.LocalStore
import com.therealsylva.roaches.data.download.SeasonDownloadCoordinator
import com.therealsylva.roaches.data.download.selectSeasonDownloadSource
import com.therealsylva.roaches.data.model.AppSettings
import com.therealsylva.roaches.data.model.BrowseCategory
import com.therealsylva.roaches.data.model.ContentRegion
import com.therealsylva.roaches.data.model.CobaltPrepareResult
import com.therealsylva.roaches.data.model.CobaltPreparedFile
import com.therealsylva.roaches.data.model.CobaltSaveRequest
import com.therealsylva.roaches.data.model.DownloadEntry
import com.therealsylva.roaches.data.model.DownloadMediaType
import com.therealsylva.roaches.data.model.DownloadPreference
import com.therealsylva.roaches.data.model.DownloadState
import com.therealsylva.roaches.data.model.LocalMediaEntry
import com.therealsylva.roaches.data.model.LinkAudioBitrate
import com.therealsylva.roaches.data.model.LinkDownloadMode
import com.therealsylva.roaches.data.model.LinkVideoQuality
import com.therealsylva.roaches.data.model.MediaDetails
import com.therealsylva.roaches.data.model.MediaItem
import com.therealsylva.roaches.data.model.PlaybackQuality
import com.therealsylva.roaches.data.model.PreferredAudio
import com.therealsylva.roaches.data.model.ReleaseUpdate
import com.therealsylva.roaches.data.model.SeasonDownloadProgress
import com.therealsylva.roaches.data.model.Shelf
import com.therealsylva.roaches.data.model.StreamSource
import com.therealsylva.roaches.data.model.SourceIntent
import com.therealsylva.roaches.data.model.WatchEntry
import com.therealsylva.roaches.data.model.extractHttpUrl
import com.therealsylva.roaches.data.remote.AppUpdateInstaller
import com.therealsylva.roaches.data.remote.CobaltApi
import com.therealsylva.roaches.data.remote.CobaltChallengeRequired
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
import java.io.IOException

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
    val seasonDownloads: List<SeasonDownloadProgress> = emptyList(),
    val localMedia: List<LocalMediaEntry> = emptyList(),
    val linkSaveVisible: Boolean = false,
    val linkSaveUrl: String = "",
    val linkDownloadMode: LinkDownloadMode = LinkDownloadMode.Video,
    val linkVideoQuality: LinkVideoQuality = LinkVideoQuality.FullHd,
    val linkAudioBitrate: LinkAudioBitrate = LinkAudioBitrate.Standard,
    val linkSaveLoading: Boolean = false,
    val linkSaveError: String? = null,
    val linkPicker: CobaltPrepareResult.Picker? = null,
    val cobaltChallengeSiteKey: String? = null,
    val cobaltChallengeRequest: CobaltSaveRequest? = null,
    val cobaltChallengeNonce: Int = 0,
    val linkRetryTitle: String? = null,
    val settings: AppSettings = AppSettings(),
    val updateLoading: Boolean = false,
    val updateMessage: String? = null,
    val availableUpdate: ReleaseUpdate? = null,
    val notice: String? = null,
)

class RoachesViewModel(application: Application) : AndroidViewModel(application) {
    private sealed interface CobaltOperation {
        val request: CobaltSaveRequest

        data class New(override val request: CobaltSaveRequest) : CobaltOperation
        data class Retry(val entry: DownloadEntry, override val request: CobaltSaveRequest) : CobaltOperation
    }

    private val store = LocalStore(application)
    private var repository = RoachesRepository(store)
    private val updateChecker = UpdateChecker()
    private val updateInstaller = AppUpdateInstaller(application)
    private val cobaltApi = CobaltApi()
    private val mutableState = MutableStateFlow(RoachesUiState())
    val state: StateFlow<RoachesUiState> = mutableState.asStateFlow()

    private var discoverJob: Job? = null
    private var searchJob: Job? = null
    private var cobaltJob: Job? = null
    private var cobaltOperation: CobaltOperation? = null
    private var lastProgressWrite = 0L

    init {
        refreshLocal()
        mutableState.update { it.copy(shelves = repository.cachedDiscover()) }
        if (store.nextSeasonDownloadTask() != null) {
            SeasonDownloadCoordinator.schedule(application)
        }
        loadDiscover()
    }

    fun setDestination(destination: MainDestination) {
        mutableState.update { it.copy(destination = destination, screen = AppScreen.Browse) }
        if (destination == MainDestination.Downloads) refreshDownloads()
    }

    fun loadDiscover() {
        discoverJob?.cancel()
        val expectedRepository = repository
        val expectedRegion = mutableState.value.settings.contentRegion
        val cached = expectedRepository.cachedDiscover()
        mutableState.update {
            it.copy(
                shelves = it.shelves.ifEmpty { cached },
                discoverLoading = true,
                discoverError = null,
            )
        }
        discoverJob = viewModelScope.launch {
            try {
                val shelves = expectedRepository.discover()
                if (mutableState.value.settings.contentRegion != expectedRegion) return@launch
                mutableState.update {
                    it.copy(
                        shelves = shelves,
                        discoverLoading = false,
                        discoverError = if (shelves.isEmpty()) "No Home sections were returned." else null,
                    )
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (failure: Throwable) {
                if (mutableState.value.settings.contentRegion != expectedRegion) return@launch
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
                (repository.category(category, 1) + repository.category(category, 2))
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
        discoverJob?.cancel()
        store.setContentRegion(region)
        repository = RoachesRepository(store)
        searchJob?.cancel()
        val cached = repository.cachedDiscover()
        mutableState.update {
            it.copy(
                settings = store.settings(),
                shelves = cached,
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
        if (enabled == mutableState.value.settings.wifiOnlyDownloads) return
        val queuedBeforeChange = if (enabled) {
            emptyList()
        } else {
            store.downloads().filter { it.state == DownloadState.Queued }
        }
        store.setWifiOnlyDownloads(enabled)
        mutableState.update {
            it.copy(
                settings = store.settings(),
                notice = if (enabled) "Downloads will wait for Wi-Fi" else "Downloads may use mobile data",
            )
        }

        val seasonRetryCount = queuedBeforeChange
            .mapNotNull(DownloadEntry::batchId)
            .distinct()
            .sumOf(store::retrySeasonDownload)
        if (seasonRetryCount > 0) refreshDownloads()
        if (store.nextSeasonDownloadTask() != null) {
            SeasonDownloadCoordinator.schedule(getApplication(), replace = true)
        }

        val singles = queuedBeforeChange.filter { it.batchId == null && !it.isLinkSave }
        if (singles.isEmpty()) {
            if (seasonRetryCount > 0) {
                mutableState.update { it.copy(notice = "Restarting $seasonRetryCount queued episodes") }
            }
            return
        }
        viewModelScope.launch {
            var restarted = 0
            singles.forEach { entry ->
                runCatching { restartDownloadEntry(entry) }
                    .onSuccess { restarted += 1 }
            }
            refreshDownloads()
            mutableState.update {
                it.copy(
                    notice = when {
                        restarted + seasonRetryCount > 0 -> {
                            "Restarting ${restarted + seasonRetryCount} queued downloads"
                        }
                        else -> "Queued downloads could not be restarted"
                    },
                )
            }
        }
    }

    fun setDarkTheme(enabled: Boolean) {
        store.setDarkTheme(enabled)
        mutableState.update { it.copy(settings = store.settings()) }
    }

    fun enableEggs(key: String) {
        val access = EggsGate.accessFor(key)
        store.setEggsAccess(access.enabled, access.matureContentUnlocked)
        applyEggsAccess("Eggs enabled")
    }

    fun disableEggs() {
        store.setEggsAccess(enabled = false, unlocked = false)
        applyEggsAccess("Eggs disabled")
    }

    private fun applyEggsAccess(notice: String) {
        searchJob?.cancel()
        repository = RoachesRepository(store)
        mutableState.update {
            it.copy(
                settings = store.settings(),
                searchQuery = "",
                searchResults = emptyList(),
                searchLoading = false,
                searchError = null,
                notice = notice,
            )
        }
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

    fun openLinkSave(sharedText: String? = null) {
        cobaltJob?.cancel()
        cobaltOperation = null
        val sharedUrl = sharedText?.let(::extractHttpUrl)
        mutableState.update {
            it.copy(
                destination = MainDestination.Downloads,
                screen = AppScreen.Browse,
                linkSaveVisible = true,
                linkSaveUrl = sharedUrl ?: sharedText?.trim().orEmpty(),
                linkSaveLoading = false,
                linkSaveError = null,
                linkPicker = null,
                cobaltChallengeSiteKey = null,
                cobaltChallengeRequest = null,
                linkRetryTitle = null,
            )
        }
    }

    fun updateLinkSaveUrl(value: String) {
        resetLinkPreparation()
        mutableState.update { it.copy(linkSaveUrl = value.take(2_048)) }
    }

    fun setLinkDownloadMode(mode: LinkDownloadMode) {
        if (mode == mutableState.value.linkDownloadMode) return
        resetLinkPreparation()
        mutableState.update { it.copy(linkDownloadMode = mode) }
    }

    fun setLinkVideoQuality(quality: LinkVideoQuality) {
        if (quality == mutableState.value.linkVideoQuality) return
        resetLinkPreparation()
        mutableState.update { it.copy(linkVideoQuality = quality) }
    }

    fun setLinkAudioBitrate(bitrate: LinkAudioBitrate) {
        if (bitrate == mutableState.value.linkAudioBitrate) return
        resetLinkPreparation()
        mutableState.update { it.copy(linkAudioBitrate = bitrate) }
    }

    fun submitLinkSave() {
        val current = mutableState.value
        val sourceUrl = extractHttpUrl(current.linkSaveUrl)
        if (sourceUrl == null) {
            mutableState.update { it.copy(linkSaveError = "Paste a complete http or https link.") }
            return
        }
        val request = CobaltSaveRequest(
            sourceUrl = sourceUrl,
            mode = current.linkDownloadMode,
            videoQuality = current.linkVideoQuality,
            audioBitrate = current.linkAudioBitrate,
        )
        mutableState.update { it.copy(linkSaveUrl = sourceUrl) }
        startCobaltOperation(CobaltOperation.New(request))
    }

    fun dismissLinkSave() {
        cobaltJob?.cancel()
        cobaltJob = null
        cobaltOperation = null
        mutableState.update {
            it.copy(
                linkSaveVisible = false,
                linkSaveLoading = false,
                linkSaveError = null,
                linkPicker = null,
                cobaltChallengeSiteKey = null,
                cobaltChallengeRequest = null,
                linkRetryTitle = null,
            )
        }
    }

    fun completeCobaltChallenge(browserResult: String) {
        val operation = cobaltOperation ?: return
        if (mutableState.value.linkSaveLoading) return
        cobaltJob?.cancel()
        mutableState.update { it.copy(linkSaveLoading = true, linkSaveError = null) }
        cobaltJob = viewModelScope.launch {
            try {
                val result = cobaltApi.completeBrowserChallenge(operation.request, browserResult)
                if (cobaltOperation != operation) return@launch
                mutableState.update {
                    it.copy(
                        cobaltChallengeSiteKey = null,
                        cobaltChallengeRequest = null,
                    )
                }
                when (operation) {
                    is CobaltOperation.New -> handleNewCobaltResult(operation, result)
                    is CobaltOperation.Retry -> handleCobaltRetryResult(operation, result)
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (failure: Throwable) {
                if (cobaltOperation != operation) return@launch
                cobaltApi.clearSession()
                mutableState.update {
                    it.copy(
                        linkSaveLoading = false,
                        linkSaveError = failure.userMessage("The connection check failed. Try again."),
                        cobaltChallengeNonce = it.cobaltChallengeNonce + 1,
                    )
                }
            }
        }
    }

    fun cobaltChallengeFailed(message: String) {
        mutableState.update {
            it.copy(
                linkSaveLoading = false,
                linkSaveError = message.take(160).ifBlank { "The connection check could not load." },
            )
        }
    }

    fun retryCobaltChallenge() {
        mutableState.update {
            it.copy(
                linkSaveError = null,
                cobaltChallengeNonce = it.cobaltChallengeNonce + 1,
            )
        }
    }

    fun saveCobaltPickerFile(file: CobaltPreparedFile) {
        val operation = cobaltOperation as? CobaltOperation.New ?: return
        runCatching { store.enqueueCobaltDownload(operation.request, file) }
            .onSuccess { finishNewLinkSave(1) }
            .onFailure { failure ->
                mutableState.update { it.copy(linkSaveError = failure.userMessage("Download could not start.")) }
            }
    }

    fun saveAllCobaltPickerFiles() {
        val operation = cobaltOperation as? CobaltOperation.New ?: return
        val files = mutableState.value.linkPicker?.items.orEmpty()
        if (files.isEmpty()) return
        var saved = 0
        var failed = 0
        files.forEach { file ->
            runCatching { store.enqueueCobaltDownload(operation.request, file) }
                .onSuccess { saved += 1 }
                .onFailure { failed += 1 }
        }
        if (saved > 0) {
            finishNewLinkSave(saved, failed)
        } else {
            mutableState.update { it.copy(linkSaveError = "These items could not be added to Downloads.") }
        }
    }

    private fun resetLinkPreparation() {
        cobaltJob?.cancel()
        cobaltJob = null
        cobaltOperation = null
        mutableState.update {
            it.copy(
                linkSaveLoading = false,
                linkSaveError = null,
                linkPicker = null,
                cobaltChallengeSiteKey = null,
                cobaltChallengeRequest = null,
                linkRetryTitle = null,
            )
        }
    }

    private fun startCobaltOperation(operation: CobaltOperation) {
        cobaltJob?.cancel()
        cobaltOperation = operation
        mutableState.update {
            it.copy(
                linkSaveLoading = true,
                linkSaveError = null,
                linkPicker = null,
                cobaltChallengeSiteKey = null,
                cobaltChallengeRequest = null,
                linkRetryTitle = (operation as? CobaltOperation.Retry)?.entry?.media?.title,
            )
        }
        cobaltJob = viewModelScope.launch { runCobaltOperation(operation) }
    }

    private suspend fun runCobaltOperation(operation: CobaltOperation) {
        try {
            val result = cobaltApi.prepare(operation.request)
            if (cobaltOperation != operation) return
            when (operation) {
                is CobaltOperation.New -> handleNewCobaltResult(operation, result)
                is CobaltOperation.Retry -> handleCobaltRetryResult(operation, result)
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (challenge: CobaltChallengeRequired) {
            if (cobaltOperation != operation) return
            mutableState.update {
                it.copy(
                    linkSaveVisible = true,
                    linkSaveLoading = false,
                    linkSaveError = null,
                    cobaltChallengeSiteKey = challenge.siteKey,
                    cobaltChallengeRequest = operation.request,
                    cobaltChallengeNonce = it.cobaltChallengeNonce + 1,
                )
            }
        } catch (failure: Throwable) {
            if (cobaltOperation != operation) return
            if (operation is CobaltOperation.Retry) {
                cobaltOperation = null
                mutableState.update {
                    it.copy(
                        linkSaveVisible = false,
                        linkSaveLoading = false,
                        linkSaveError = null,
                        cobaltChallengeSiteKey = null,
                        cobaltChallengeRequest = null,
                        linkRetryTitle = null,
                        notice = failure.userMessage("Download could not be retried."),
                    )
                }
            } else {
                mutableState.update {
                    it.copy(
                        linkSaveLoading = false,
                        linkSaveError = failure.userMessage("Cobalt could not prepare this link."),
                    )
                }
            }
        }
    }

    private fun handleNewCobaltResult(
        operation: CobaltOperation.New,
        result: CobaltPrepareResult,
    ) {
        when (result) {
            is CobaltPrepareResult.File -> runCatching {
                store.enqueueCobaltDownload(operation.request, result.file)
            }.onSuccess {
                finishNewLinkSave(1)
            }.onFailure { failure ->
                mutableState.update {
                    it.copy(
                        linkSaveLoading = false,
                        linkSaveError = failure.userMessage("Download could not start."),
                    )
                }
            }
            is CobaltPrepareResult.Picker -> mutableState.update {
                it.copy(
                    linkSaveLoading = false,
                    linkPicker = result,
                    cobaltChallengeSiteKey = null,
                    cobaltChallengeRequest = null,
                )
            }
        }
    }

    private fun handleCobaltRetryResult(
        operation: CobaltOperation.Retry,
        result: CobaltPrepareResult,
    ) {
        val selection = operation.entry.cobaltRetry?.selection
        val file = when (result) {
            is CobaltPrepareResult.File -> result.file
            is CobaltPrepareResult.Picker -> when {
                selection?.pickerAudio == true -> result.audio
                selection?.index != null -> result.items.firstOrNull {
                    it.selection?.index == selection.index
                } ?: result.items.firstOrNull { it.mediaType == selection.mediaType }
                else -> null
            }
        } ?: throw IOException("This link now needs a new media selection")
        store.replaceCobaltDownload(operation.entry, file)
        cobaltOperation = null
        refreshDownloads()
        mutableState.update {
            it.copy(
                linkSaveVisible = false,
                linkSaveLoading = false,
                linkSaveError = null,
                cobaltChallengeSiteKey = null,
                cobaltChallengeRequest = null,
                linkRetryTitle = null,
                notice = "Download restarted with a fresh link",
            )
        }
    }

    private fun finishNewLinkSave(saved: Int, failed: Int = 0) {
        cobaltOperation = null
        refreshDownloads()
        mutableState.update {
            it.copy(
                linkSaveVisible = false,
                linkSaveUrl = "",
                linkSaveLoading = false,
                linkSaveError = null,
                linkPicker = null,
                cobaltChallengeSiteKey = null,
                cobaltChallengeRequest = null,
                linkRetryTitle = null,
                notice = when {
                    failed > 0 -> "Added $saved items · $failed could not start"
                    saved == 1 -> "Added saved link to Downloads"
                    else -> "Added $saved items to Downloads"
                },
            )
        }
    }

    fun download(source: StreamSource) {
        val current = mutableState.value
        val media = current.details?.item ?: current.detailsSeed ?: return
        val episode = current.details?.seasons
            ?.firstOrNull { it.number == current.selectedSeason }
            ?.episodes
            ?.firstOrNull { it.number == current.selectedEpisode }
        runCatching { store.enqueueDownload(media, source, episode) }
            .onSuccess {
                refreshDownloads()
                mutableState.update { state -> state.copy(sourcePickerVisible = false, notice = "Added to Downloads") }
            }
            .onFailure { failure ->
                mutableState.update { state -> state.copy(notice = failure.userMessage("Download could not start.")) }
            }
    }

    fun downloadSeason(source: StreamSource) {
        val current = mutableState.value
        val details = current.details ?: return
        val season = details.seasons.firstOrNull { it.number == current.selectedSeason } ?: return
        val result = store.queueSeason(
            media = details.item,
            season = season.number,
            episodes = season.episodes,
            preference = DownloadPreference(source.resolution, source.audio),
        )
        mutableState.update { state ->
            state.copy(
                sourcePickerVisible = false,
                notice = when {
                    result.queuedCount == 0 -> "Season ${season.number} is already in Downloads"
                    result.skippedCount > 0 -> {
                        "Queued ${result.queuedCount} episodes · ${result.skippedCount} already added"
                    }
                    else -> "Season ${season.number} queued"
                },
            )
        }
        refreshDownloads()
        if (result.queuedCount > 0) SeasonDownloadCoordinator.schedule(getApplication())
    }

    fun removeDownload(entry: DownloadEntry) {
        store.removeDownload(entry)
        refreshDownloads()
        if (entry.batchId != null) SeasonDownloadCoordinator.schedule(getApplication())
    }

    fun retryDownload(entry: DownloadEntry) {
        if (entry.batchId != null) {
            retrySeasonDownload(entry.batchId)
            return
        }
        if (entry.isLinkSave) {
            val request = entry.cobaltRetry?.request
            if (request == null) {
                mutableState.update { it.copy(notice = "The original link is no longer available on this device") }
                return
            }
            mutableState.update { it.copy(notice = "Preparing a fresh download link") }
            startCobaltOperation(CobaltOperation.Retry(entry, request))
            return
        }
        viewModelScope.launch {
            mutableState.update { it.copy(notice = "Preparing download retry") }
            runCatching { restartDownloadEntry(entry) }.onSuccess {
                refreshDownloads()
                mutableState.update { it.copy(notice = "Download restarted") }
            }.onFailure { failure ->
                mutableState.update { it.copy(notice = failure.userMessage("Download could not be retried.")) }
            }
        }
    }

    private suspend fun restartDownloadEntry(entry: DownloadEntry): DownloadEntry {
        val sources = repository.sources(
            subjectId = entry.media.id,
            season = entry.season,
            episode = entry.episode,
            languageHint = entry.source.audio,
        )
        val source = selectSeasonDownloadSource(
            sources,
            DownloadPreference(entry.source.resolution, entry.source.audio),
        ) ?: throw IOException("No replacement source is available")
        return store.replaceDownload(entry, source)
    }

    fun cancelSeasonDownload(batchId: String) {
        store.cancelSeasonDownload(batchId)
        refreshDownloads()
        mutableState.update { it.copy(notice = "Season download removed") }
    }

    fun retrySeasonDownload(batchId: String) {
        val retryCount = store.retrySeasonDownload(batchId)
        refreshDownloads()
        if (retryCount > 0) {
            SeasonDownloadCoordinator.schedule(getApplication())
            mutableState.update { it.copy(notice = "Retrying $retryCount episodes") }
        }
    }

    fun playDownload(entry: DownloadEntry) {
        if (entry.state != DownloadState.Complete || entry.localUri.isNullOrBlank()) {
            mutableState.update { it.copy(notice = "This download is not ready yet") }
            return
        }
        if (entry.mediaType != DownloadMediaType.Video) {
            val intent = Intent(Intent.ACTION_VIEW)
                .setDataAndType(Uri.parse(entry.localUri), entry.mimeType)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
            runCatching { getApplication<Application>().startActivity(intent) }
                .onFailure {
                    mutableState.update { state ->
                        state.copy(notice = "No installed app can open this ${entry.mediaType.label.lowercase()}")
                    }
                }
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
        val downloads = store.downloads()
        val tasks = store.seasonDownloadTasks()
        mutableState.update {
            it.copy(
                downloads = downloads,
                seasonDownloads = store.seasonDownloadProgress(downloads, tasks),
            )
        }
        val seasonActive = downloads.any { entry ->
            entry.batchId != null && entry.state in setOf(DownloadState.Queued, DownloadState.Downloading)
        }
        if (!seasonActive && store.nextSeasonDownloadTask() != null) {
            SeasonDownloadCoordinator.schedule(getApplication())
        }
    }

    fun recordProgress(positionMs: Long, durationMs: Long, force: Boolean = false) {
        val media = mutableState.value.playerMedia ?: return
        if (media.id.startsWith("local:") || media.id.startsWith("link:")) return
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
        mutableState.update { it.copy(updateLoading = true, updateMessage = null, availableUpdate = null) }
        viewModelScope.launch {
            runCatching { updateChecker.check() }
                .onSuccess { release ->
                    val installable = release.available && release.apkUrl != null && release.sha256 != null
                    mutableState.update {
                        it.copy(
                            updateLoading = false,
                            updateMessage = when {
                                !release.available -> "Roaches is up to date."
                                release.apkUrl == null -> "Roaches ${release.versionName} has no installable APK."
                                release.sha256 == null -> "Roaches ${release.versionName} could not be verified."
                                else -> "Roaches ${release.versionName} is ready."
                            },
                            availableUpdate = release.takeIf { installable },
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

    fun installUpdate() {
        if (mutableState.value.updateLoading) return
        val release = mutableState.value.availableUpdate ?: return
        mutableState.update { it.copy(updateLoading = true, updateMessage = "Starting update") }
        viewModelScope.launch {
            runCatching {
                updateInstaller.downloadAndRequestInstall(release) { progress ->
                    val message = progress.percent?.let { "${progress.message} · $it%" } ?: progress.message
                    mutableState.update { state -> state.copy(updateMessage = message) }
                }
            }.onSuccess {
                mutableState.update {
                    it.copy(
                        updateLoading = false,
                        updateMessage = "Android is ready to install Roaches ${release.versionName}.",
                    )
                }
            }.onFailure { failure ->
                mutableState.update {
                    it.copy(
                        updateLoading = false,
                        updateMessage = failure.userMessage("The update could not be installed."),
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
        val downloads = store.downloads()
        val seasonTasks = store.seasonDownloadTasks()
        mutableState.update {
            it.copy(
                watchlist = store.watchlist(),
                liked = store.liked(),
                history = store.history(),
                downloads = downloads,
                seasonDownloads = store.seasonDownloadProgress(downloads, seasonTasks),
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
        viewModelScope.launch {
            val remote = runCatching { repository.recommendations(details) }.getOrDefault(emptyList())
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
