package com.therealsylva.roaches.data.local

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import com.therealsylva.roaches.data.model.AppSettings
import com.therealsylva.roaches.data.model.ContentRegion
import com.therealsylva.roaches.data.model.DownloadEntry
import com.therealsylva.roaches.data.model.DownloadState
import com.therealsylva.roaches.data.model.LocalMediaEntry
import com.therealsylva.roaches.data.model.MediaItem
import com.therealsylva.roaches.data.model.MediaKind
import com.therealsylva.roaches.data.model.PlaybackQuality
import com.therealsylva.roaches.data.model.PreferredAudio
import com.therealsylva.roaches.data.model.StreamSource
import com.therealsylva.roaches.data.model.WatchEntry
import com.therealsylva.roaches.data.remote.ClientIdentity
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

class LocalStore(context: Context) {
    companion object {
        private const val PROVIDER_IP_PREFIX = "103.241"
    }

    private val appContext = context.applicationContext
    private val preferences = appContext.getSharedPreferences("roaches_local", Context.MODE_PRIVATE)

    internal fun clientIdentity(): ClientIdentity {
        val installId = valueOrCreate("install_id") { UUID.randomUUID().toString() }
        val sessionId = valueOrCreate("session_id") { UUID.randomUUID().toString() }
        val hash = installId.hashCode().toUInt().toLong()
        val forwardedIp = "$PROVIDER_IP_PREFIX.${(hash shr 8) % 253 + 1}.${hash % 253 + 1}"
        return ClientIdentity(
            installId = installId,
            sessionId = sessionId,
            forwardedIp = forwardedIp,
        )
    }

    fun settings(): AppSettings = AppSettings(
        contentRegion = preferences.enumValue("content_region", ContentRegion.GlobalEnglish),
        playbackQuality = preferences.enumValue("playback_quality", PlaybackQuality.Auto),
        preferredAudio = preferences.enumValue("preferred_audio", PreferredAudio.English),
        wifiOnlyDownloads = preferences.getBoolean("wifi_only_downloads", true),
        darkTheme = preferences.getBoolean("dark_theme", true),
    )

    fun setContentRegion(region: ContentRegion) {
        preferences.edit().putString("content_region", region.name).apply()
    }

    fun setPlaybackQuality(quality: PlaybackQuality) {
        preferences.edit().putString("playback_quality", quality.name).apply()
    }

    fun setPreferredAudio(audio: PreferredAudio) {
        preferences.edit().putString("preferred_audio", audio.name).apply()
    }

    fun setWifiOnlyDownloads(enabled: Boolean) {
        preferences.edit().putBoolean("wifi_only_downloads", enabled).apply()
    }

    fun setDarkTheme(enabled: Boolean) {
        preferences.edit().putBoolean("dark_theme", enabled).apply()
    }

    fun watchlist(): List<MediaItem> = readArray("watchlist").objects().mapNotNull(::mediaFromJson)

    fun isSaved(id: String): Boolean = watchlist().any { it.id == id }

    fun toggleSaved(item: MediaItem): Boolean {
        val current = watchlist().toMutableList()
        val existing = current.indexOfFirst { it.id == item.id }
        val nowSaved = existing < 0
        if (nowSaved) current.add(0, item) else current.removeAt(existing)
        writeArray("watchlist", current.map(::mediaToJson))
        return nowSaved
    }

    fun liked(): List<MediaItem> = readArray("liked").objects().mapNotNull(::mediaFromJson)

    fun toggleLiked(item: MediaItem): Boolean {
        val current = liked().toMutableList()
        val existing = current.indexOfFirst { it.id == item.id }
        val nowLiked = existing < 0
        if (nowLiked) current.add(0, item) else current.removeAt(existing)
        writeArray("liked", current.map(::mediaToJson))
        return nowLiked
    }

    fun localMedia(): List<LocalMediaEntry> = readArray("local_media").objects()
        .mapNotNull(::localMediaFromJson)
        .sortedByDescending(LocalMediaEntry::addedAt)

    fun addLocalMedia(uri: String, title: String): LocalMediaEntry {
        localMedia().firstOrNull { it.uri == uri }?.let { return it }
        val entry = LocalMediaEntry(
            id = UUID.randomUUID().toString(),
            title = title.trim().ifBlank { "Local video" },
            uri = uri,
            addedAt = System.currentTimeMillis(),
        )
        writeArray("local_media", listOf(localMediaToJson(entry)) + localMedia().map(::localMediaToJson))
        return entry
    }

    fun removeLocalMedia(entry: LocalMediaEntry) {
        writeArray("local_media", localMedia().filterNot { it.id == entry.id }.map(::localMediaToJson))
    }

    fun history(): List<WatchEntry> = readArray("history").objects()
        .mapNotNull(::watchFromJson)
        .sortedByDescending(WatchEntry::updatedAt)

    fun updateProgress(item: MediaItem, positionMs: Long, durationMs: Long) {
        if (durationMs <= 0L || positionMs < 0L) return
        val current = history().filterNot { it.media.id == item.id }.toMutableList()
        current.add(
            0,
            WatchEntry(
                media = item,
                positionMs = positionMs,
                durationMs = durationMs,
                updatedAt = System.currentTimeMillis(),
            ),
        )
        writeArray("history", current.take(40).map(::watchToJson))
    }

    fun clearHistory() {
        writeArray("history", emptyList<JSONObject>())
    }

    fun downloads(): List<DownloadEntry> = readArray("downloads").objects()
        .mapNotNull(::downloadFromJson)
        .sortedByDescending(DownloadEntry::createdAt)
        .map(::queryDownload)

    fun enqueueDownload(media: MediaItem, source: StreamSource): DownloadEntry {
        val manager = appContext.getSystemService(DownloadManager::class.java)
        val safeTitle = media.title.replace(Regex("[^A-Za-z0-9._ -]"), "").trim().ifBlank { "Roaches" }
        val extension = source.filename
            ?.substringAfterLast('.', "mp4")
            ?.takeIf { it.length in 2..5 }
            ?: "mp4"
        val request = DownloadManager.Request(Uri.parse(source.url))
            .setTitle(media.title)
            .setDescription("${source.qualityLabel} · Roaches")
            .setMimeType("video/*")
            .setAllowedOverMetered(!settings().wifiOnlyDownloads)
            .setAllowedOverRoaming(false)
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalFilesDir(
                appContext,
                Environment.DIRECTORY_MOVIES,
                "$safeTitle-${source.qualityLabel}.$extension",
            )
        val id = manager.enqueue(request)
        val entry = DownloadEntry(id, media, source, System.currentTimeMillis())
        val current = downloads().filterNot { it.downloadId == id }.toMutableList()
        current.add(0, entry)
        writeArray("downloads", current.map(::downloadToJson))
        return entry
    }

    fun removeDownload(entry: DownloadEntry) {
        appContext.getSystemService(DownloadManager::class.java).remove(entry.downloadId)
        writeArray(
            "downloads",
            downloads().filterNot { it.downloadId == entry.downloadId }.map(::downloadToJson),
        )
    }

    private fun queryDownload(entry: DownloadEntry): DownloadEntry {
        val manager = appContext.getSystemService(DownloadManager::class.java)
        return runCatching {
            manager.query(DownloadManager.Query().setFilterById(entry.downloadId)).use { cursor ->
                if (!cursor.moveToFirst()) return@use entry.copy(state = DownloadState.Missing)
                val status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                val downloaded = cursor.getLong(
                    cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR),
                )
                val total = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
                val uri = cursor.getString(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_LOCAL_URI))
                entry.copy(
                    state = when (status) {
                        DownloadManager.STATUS_PENDING -> DownloadState.Queued
                        DownloadManager.STATUS_RUNNING, DownloadManager.STATUS_PAUSED -> DownloadState.Downloading
                        DownloadManager.STATUS_SUCCESSFUL -> DownloadState.Complete
                        DownloadManager.STATUS_FAILED -> DownloadState.Failed
                        else -> DownloadState.Missing
                    },
                    progress = if (total > 0L) (downloaded.toFloat() / total).coerceIn(0f, 1f) else 0f,
                    localUri = uri,
                )
            }
        }.getOrDefault(entry.copy(state = DownloadState.Missing))
    }

    private fun valueOrCreate(key: String, create: () -> String): String {
        preferences.getString(key, null)?.let { return it }
        return create().also { preferences.edit().putString(key, it).apply() }
    }

    private fun readArray(key: String): JSONArray = runCatching {
        JSONArray(preferences.getString(key, "[]"))
    }.getOrElse { JSONArray() }

    private fun writeArray(key: String, objects: List<JSONObject>) {
        preferences.edit().putString(key, JSONArray(objects).toString()).apply()
    }
}

private inline fun <reified T : Enum<T>> android.content.SharedPreferences.enumValue(
    key: String,
    fallback: T,
): T = getString(key, null)?.let { stored ->
    enumValues<T>().firstOrNull { it.name == stored }
} ?: fallback

private fun JSONArray.objects(): List<JSONObject> = buildList {
    repeat(length()) { index -> optJSONObject(index)?.let(::add) }
}

private fun mediaToJson(item: MediaItem) = JSONObject()
    .put("id", item.id)
    .put("title", item.title)
    .put("kind", item.kind.name)
    .put("year", item.year)
    .put("poster", item.posterUrl)
    .put("backdrop", item.backdropUrl)
    .put("rating", item.rating)
    .put("description", item.description)
    .put("seasons", item.seasonCount)

private fun mediaFromJson(json: JSONObject): MediaItem? {
    val id = json.optString("id").takeIf(String::isNotBlank) ?: return null
    return MediaItem(
        id = id,
        title = json.optString("title", "Unknown title"),
        kind = runCatching { MediaKind.valueOf(json.optString("kind")) }.getOrDefault(MediaKind.Movie),
        year = json.optString("year"),
        posterUrl = json.nullableString("poster"),
        backdropUrl = json.nullableString("backdrop"),
        rating = json.nullableString("rating"),
        description = json.nullableString("description"),
        seasonCount = json.optInt("seasons"),
    )
}

private fun watchToJson(entry: WatchEntry) = JSONObject()
    .put("media", mediaToJson(entry.media))
    .put("position", entry.positionMs)
    .put("duration", entry.durationMs)
    .put("updated", entry.updatedAt)

private fun watchFromJson(json: JSONObject): WatchEntry? {
    val media = json.optJSONObject("media")?.let(::mediaFromJson) ?: return null
    return WatchEntry(
        media,
        json.optLong("position"),
        json.optLong("duration"),
        json.optLong("updated"),
    )
}

private fun streamToJson(source: StreamSource) = JSONObject()
    .put("id", source.resourceId)
    .put("url", source.url)
    .put("resolution", source.resolution)
    .put("codec", source.codec)
    .put("audio", source.audio)
    .put("size", source.sizeBytes)
    .put("filename", source.filename)

private fun streamFromJson(json: JSONObject): StreamSource? {
    val url = json.optString("url").takeIf(String::isNotBlank) ?: return null
    return StreamSource(
        resourceId = json.optString("id"),
        url = url,
        resolution = json.optInt("resolution"),
        codec = json.nullableString("codec"),
        audio = json.nullableString("audio"),
        sizeBytes = json.optLong("size").takeIf { it > 0L },
        filename = json.nullableString("filename"),
    )
}

private fun downloadToJson(entry: DownloadEntry) = JSONObject()
    .put("id", entry.downloadId)
    .put("media", mediaToJson(entry.media))
    .put("source", streamToJson(entry.source))
    .put("created", entry.createdAt)

private fun downloadFromJson(json: JSONObject): DownloadEntry? {
    val media = json.optJSONObject("media")?.let(::mediaFromJson) ?: return null
    val source = json.optJSONObject("source")?.let(::streamFromJson) ?: return null
    return DownloadEntry(json.optLong("id"), media, source, json.optLong("created"))
}

private fun localMediaToJson(entry: LocalMediaEntry) = JSONObject()
    .put("id", entry.id)
    .put("title", entry.title)
    .put("uri", entry.uri)
    .put("added", entry.addedAt)

private fun localMediaFromJson(json: JSONObject): LocalMediaEntry? {
    val id = json.optString("id").takeIf(String::isNotBlank) ?: return null
    val uri = json.optString("uri").takeIf(String::isNotBlank) ?: return null
    return LocalMediaEntry(
        id = id,
        title = json.optString("title", "Local video"),
        uri = uri,
        addedAt = json.optLong("added"),
    )
}

private fun JSONObject.nullableString(key: String): String? = opt(key)
    ?.takeUnless { it == JSONObject.NULL }
    ?.toString()
    ?.takeIf(String::isNotBlank)
