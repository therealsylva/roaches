package com.therealsylva.roaches.data.model

enum class MediaKind { Movie, Series }

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
    val seasons: List<Season> = emptyList(),
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

data class DownloadEntry(
    val downloadId: Long,
    val media: MediaItem,
    val source: StreamSource,
    val createdAt: Long,
    val state: DownloadState = DownloadState.Queued,
    val progress: Float = 0f,
    val localUri: String? = null,
)
