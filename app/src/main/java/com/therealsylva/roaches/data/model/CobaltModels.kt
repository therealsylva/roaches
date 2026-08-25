package com.therealsylva.roaches.data.model

import java.net.URI
import java.util.Locale

enum class LinkDownloadMode(val label: String, val apiValue: String) {
    Video("Video", "auto"),
    Audio("Audio", "audio"),
}

enum class LinkVideoQuality(val label: String, val apiValue: String, val height: Int) {
    Best("Best", "max", 0),
    UltraHd("2160p", "2160", 2160),
    FullHd("1080p", "1080", 1080),
    Hd("720p", "720", 720),
    Standard("480p", "480", 480),
}

enum class LinkAudioBitrate(val label: String, val apiValue: String) {
    High("320 kbps", "320"),
    Balanced("256 kbps", "256"),
    Standard("128 kbps", "128"),
    Compact("96 kbps", "96"),
}

enum class DownloadMediaType(val label: String, val fallbackMimeType: String) {
    Video("Video", "video/*"),
    Audio("Audio", "audio/*"),
    Image("Photo", "image/*"),
    Gif("GIF", "image/gif"),
}

data class CobaltSaveRequest(
    val sourceUrl: String,
    val mode: LinkDownloadMode = LinkDownloadMode.Video,
    val videoQuality: LinkVideoQuality = LinkVideoQuality.FullHd,
    val audioBitrate: LinkAudioBitrate = LinkAudioBitrate.Standard,
)

data class CobaltPickerSelection(
    val index: Int? = null,
    val mediaType: DownloadMediaType,
    val pickerAudio: Boolean = false,
)

data class CobaltRetry(
    val request: CobaltSaveRequest,
    val selection: CobaltPickerSelection? = null,
)

data class CobaltPreparedFile(
    val url: String,
    val filename: String,
    val mediaType: DownloadMediaType,
    val mimeType: String,
    val thumbnailUrl: String? = null,
    val selection: CobaltPickerSelection? = null,
)

sealed interface CobaltPrepareResult {
    data class File(val file: CobaltPreparedFile) : CobaltPrepareResult

    data class Picker(
        val items: List<CobaltPreparedFile>,
        val audio: CobaltPreparedFile? = null,
    ) : CobaltPrepareResult
}

data class CobaltInstanceInfo(
    val version: String,
    val turnstileSiteKey: String?,
    val services: List<String>,
)

internal fun extractHttpUrl(value: String): String? {
    val trimmed = value.trim()
    if (trimmed.isBlank()) return null
    val match = HTTP_URL.find(trimmed)?.value
    val candidate = (match ?: trimmed)
        .trim()
        .trimEnd('.', ',', ';', ')', ']', '}', '>')
    return runCatching { URI(candidate) }
        .getOrNull()
        ?.takeIf { uri ->
            uri.scheme?.lowercase(Locale.US) in setOf("http", "https") &&
                !uri.host.isNullOrBlank()
        }
        ?.toString()
}

internal fun downloadMediaType(filename: String?, fallback: DownloadMediaType): DownloadMediaType {
    return when (filename.mediaExtension()) {
        in VIDEO_EXTENSIONS -> DownloadMediaType.Video
        in AUDIO_EXTENSIONS -> DownloadMediaType.Audio
        "gif" -> DownloadMediaType.Gif
        in IMAGE_EXTENSIONS -> DownloadMediaType.Image
        else -> fallback
    }
}

internal fun downloadMimeType(filename: String?, mediaType: DownloadMediaType): String = when (
    filename.mediaExtension()
) {
    "mp4", "m4v" -> "video/mp4"
    "mkv" -> "video/x-matroska"
    "webm" -> if (mediaType == DownloadMediaType.Audio) "audio/webm" else "video/webm"
    "mov" -> "video/quicktime"
    "ts" -> "video/mp2t"
    "mp3" -> "audio/mpeg"
    "m4a" -> "audio/mp4"
    "ogg" -> "audio/ogg"
    "opus" -> "audio/opus"
    "wav" -> "audio/wav"
    "jpg", "jpeg" -> "image/jpeg"
    "png" -> "image/png"
    "webp" -> "image/webp"
    "gif" -> "image/gif"
    else -> mediaType.fallbackMimeType
}

internal fun fallbackCobaltFilename(
    sourceUrl: String,
    mediaType: DownloadMediaType,
    index: Int? = null,
): String {
    val host = runCatching { URI(sourceUrl).host }.getOrNull()
        ?.removePrefix("www.")
        ?.substringBefore('.')
        ?.replace(Regex("[^A-Za-z0-9_-]"), "")
        ?.take(32)
        ?.ifBlank { null }
        ?: "saved-link"
    val suffix = index?.let { "-${it + 1}" }.orEmpty()
    val extension = when (mediaType) {
        DownloadMediaType.Video -> "mp4"
        DownloadMediaType.Audio -> "mp3"
        DownloadMediaType.Image -> "jpg"
        DownloadMediaType.Gif -> "gif"
    }
    return "$host$suffix.$extension"
}

internal fun cobaltDisplayTitle(filename: String?, sourceUrl: String): String {
    val filenameTitle = filename
        ?.substringBeforeLast('.')
        ?.replace('_', ' ')
        ?.replace(Regex("\\s+"), " ")
        ?.trim()
        ?.take(100)
        ?.takeIf(String::isNotBlank)
    if (filenameTitle != null) return filenameTitle
    return runCatching { URI(sourceUrl).host }
        .getOrNull()
        ?.removePrefix("www.")
        ?.takeIf(String::isNotBlank)
        ?: "Saved link"
}

private fun String?.mediaExtension(): String? = this
    ?.substringBefore('?')
    ?.substringBefore('#')
    ?.substringAfterLast('/')
    ?.substringAfterLast('.', "")
    ?.lowercase(Locale.US)
    ?.takeIf(String::isNotBlank)

private val HTTP_URL = Regex("https?://[^\\s<>\\\"']+", RegexOption.IGNORE_CASE)
private val VIDEO_EXTENSIONS = setOf("mp4", "mkv", "webm", "m4v", "mov", "ts")
private val AUDIO_EXTENSIONS = setOf("mp3", "m4a", "ogg", "opus", "wav")
private val IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "webp")
