package com.therealsylva.roaches.data.model

enum class MediaKind { Movie, Series }

enum class BrowseCategory(
    val label: String,
    val providerGenre: String,
    val providerCountry: String? = null,
) {
    Anime("Anime", "Animation", "Japan"),
    Action("Action", "Action"),
    Romance("Romance", "Romance"),
    Comedy("Comedy", "Comedy"),
    Horror("Horror", "Horror"),
    ScienceFiction("Sci-fi", "Sci-Fi"),
    Thriller("Thriller", "Thriller"),
    Drama("Drama", "Drama"),
    Fantasy("Fantasy", "Fantasy"),
    Animation("Animation", "Animation"),
    Crime("Crime", "Crime"),
    Documentary("Documentary", "Documentary"),
    Family("Family", "Family"),
}

enum class ContentRegion(val label: String) {
    GlobalEnglish("Global English"),
    UnitedKingdom("United Kingdom"),
    Nigeria("Nigeria"),
}

enum class PlaybackQuality(val height: Int, val label: String) {
    Auto(0, "Best available"),
    FullHd(1080, "Up to 1080p"),
    Hd(720, "Up to 720p"),
}

enum class PreferredAudio(val label: String, vararg val matches: String) {
    English("English", "english", "eng"),
    Original("Original audio", "original"),
    French("French", "french", "fra"),
    Spanish("Spanish", "spanish", "spa"),
    Arabic("Arabic", "arabic", "ara"),
    Hindi("Hindi", "hindi", "hin"),
    Any("Any available"),
}

data class AppSettings(
    val contentRegion: ContentRegion = ContentRegion.GlobalEnglish,
    val playbackQuality: PlaybackQuality = PlaybackQuality.Auto,
    val preferredAudio: PreferredAudio = PreferredAudio.English,
    val wifiOnlyDownloads: Boolean = false,
    val darkTheme: Boolean = true,
)

data class MediaItem(
    val id: String,
    val title: String,
    val kind: MediaKind,
    val year: String = "",
    val posterUrl: String? = null,
    val backdropUrl: String? = null,
    val rating: String? = null,
    val description: String? = null,
    val seasonCount: Int = 0,
)

data class Shelf(
    val id: String,
    val title: String,
    val items: List<MediaItem>,
)

data class Episode(
    val season: Int,
    val number: Int,
    val title: String = "Episode $number",
)

data class Season(
    val number: Int,
    val episodes: List<Episode>,
)

data class MediaDetails(
    val item: MediaItem,
    val synopsis: String = "",
    val duration: String? = null,
    val genres: List<String> = emptyList(),
    val director: String? = null,
    val cast: String? = null,
    val country: String? = null,
    val audioLanguage: String? = null,
    val seasons: List<Season> = emptyList(),
)

data class LocalMediaEntry(
    val id: String,
    val title: String,
    val uri: String,
    val addedAt: Long,
)

data class SubtitleTrack(
    val label: String,
    val url: String,
)

data class StreamSource(
    val resourceId: String,
    val url: String,
    val resolution: Int = 0,
    val codec: String? = null,
    val audio: String? = null,
    val sizeBytes: Long? = null,
    val filename: String? = null,
    val durationSeconds: Long? = null,
    val uploader: String? = null,
    val subtitles: List<SubtitleTrack> = emptyList(),
) {
    val qualityLabel: String
        get() = if (resolution > 0) "${resolution}p" else "Auto"

    val technicalLabel: String
        get() = listOfNotNull(
            qualityLabel,
            codec?.takeIf(String::isNotBlank),
            audio?.takeIf(String::isNotBlank),
        ).joinToString(" · ")
}

data class WatchEntry(
    val media: MediaItem,
    val positionMs: Long,
    val durationMs: Long,
    val updatedAt: Long,
) {
    val progress: Float
        get() = if (durationMs <= 0L) 0f else (positionMs.toFloat() / durationMs).coerceIn(0f, 1f)
}

enum class DownloadState { Queued, Downloading, Complete, Failed, Missing }

enum class SourceIntent { Playback, Download, SeasonDownload }

data class DownloadPreference(
    val resolution: Int = 0,
    val audio: String? = null,
)

data class SeasonDownloadTask(
    val id: String,
    val batchId: String,
    val media: MediaItem,
    val season: Int,
    val episode: Int,
    val episodeTitle: String,
    val preference: DownloadPreference,
    val batchSize: Int,
    val createdAt: Long,
    val attempts: Int = 0,
    val lastError: String? = null,
) {
    val targetKey: String
        get() = downloadTargetKey(media.id, season, episode)
}

data class SeasonDownloadProgress(
    val batchId: String,
    val media: MediaItem,
    val season: Int,
    val totalCount: Int,
    val readyCount: Int,
    val failedCount: Int,
    val queuedCount: Int,
    val activeEpisode: Int? = null,
    val activeProgress: Float = 0f,
    val statusMessage: String? = null,
    val retryAvailable: Boolean = false,
) {
    val progress: Float
        get() = if (totalCount <= 0) {
            0f
        } else {
            ((readyCount + activeProgress.coerceIn(0f, 1f)) / totalCount.toFloat()).coerceIn(0f, 1f)
        }
}

data class ReleaseUpdate(
    val versionName: String,
    val releaseUrl: String,
    val apkUrl: String?,
    val available: Boolean,
)

data class DownloadEntry(
    val downloadId: Long,
    val media: MediaItem,
    val source: StreamSource,
    val createdAt: Long,
    val season: Int = 0,
    val episode: Int = 0,
    val episodeTitle: String? = null,
    val batchId: String? = null,
    val batchSize: Int = 0,
    val state: DownloadState = DownloadState.Queued,
    val progress: Float = 0f,
    val localUri: String? = null,
    val statusMessage: String? = null,
) {
    val targetKey: String
        get() = downloadTargetKey(media.id, season, episode)
}

fun downloadTargetKey(subjectId: String, season: Int, episode: Int): String =
    "$subjectId:${season.coerceAtLeast(0)}:${episode.coerceAtLeast(0)}"
