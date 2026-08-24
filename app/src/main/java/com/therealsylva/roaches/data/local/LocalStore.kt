package com.therealsylva.roaches.data.local

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import com.therealsylva.roaches.data.model.AppSettings
import com.therealsylva.roaches.data.model.ContentRegion
import com.therealsylva.roaches.data.model.DownloadEntry
import com.therealsylva.roaches.data.model.DownloadPreference
import com.therealsylva.roaches.data.model.DownloadState
import com.therealsylva.roaches.data.model.Episode
import com.therealsylva.roaches.data.model.LocalMediaEntry
import com.therealsylva.roaches.data.model.MediaItem
import com.therealsylva.roaches.data.model.MediaKind
import com.therealsylva.roaches.data.model.PlaybackQuality
import com.therealsylva.roaches.data.model.PreferredAudio
import com.therealsylva.roaches.data.model.SeasonDownloadProgress
import com.therealsylva.roaches.data.model.SeasonDownloadTask
import com.therealsylva.roaches.data.model.Shelf
import com.therealsylva.roaches.data.model.StreamSource
import com.therealsylva.roaches.data.model.WatchEntry
import com.therealsylva.roaches.data.remote.ClientIdentity
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class SeasonQueueResult(
    val batchId: String?,
    val queuedCount: Int,
    val skippedCount: Int,
)

internal fun missingSeasonEpisodes(
    subjectId: String,
    season: Int,
    episodes: List<Episode>,
    existingKeys: Set<String>,
): List<Episode> = episodes.filter { episode ->
    com.therealsylva.roaches.data.model.downloadTargetKey(subjectId, season, episode.number) !in existingKeys
}

internal fun downloadFileName(
    title: String,
    season: Int,
    episode: Int,
    source: StreamSource,
): String {
    val safeTitle = title.replace(Regex("[^A-Za-z0-9._ -]"), "").trim().ifBlank { "Roaches" }
    val extension = source.filename
        ?.substringAfterLast('.', "mp4")
        ?.takeIf { it.length in 2..5 }
        ?: "mp4"
    val episodeSuffix = if (season > 0 && episode > 0) {
        "-S${season.toString().padStart(2, '0')}E${episode.toString().padStart(2, '0')}"
    } else {
        ""
    }
    return "$safeTitle$episodeSuffix-${source.qualityLabel}.$extension"
}

class LocalStore(context: Context) {
    companion object {
        private const val PROVIDER_IP_PREFIX = "103.241"
        private const val SEASON_DOWNLOAD_QUEUE = "season_download_queue_v1"
        private const val MAX_SEASON_ATTEMPTS = 3
        private val DOWNLOAD_LOCK = Any()
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

    fun cachedHome(region: ContentRegion): List<Shelf> = decodeHomeCache(
        preferences.getString(homeCacheKey(region), null),
    )

    fun cacheHome(region: ContentRegion, shelves: List<Shelf>) {
        if (shelves.none { it.items.isNotEmpty() }) return
        preferences.edit()
            .putString(homeCacheKey(region), encodeHomeCache(shelves))
            .apply()
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

    fun downloads(): List<DownloadEntry> = storedDownloads()
        .sortedByDescending(DownloadEntry::createdAt)
        .map(::queryDownload)

    fun seasonDownloadTasks(): List<SeasonDownloadTask> = decodeSeasonDownloadTasks(
        preferences.getString(SEASON_DOWNLOAD_QUEUE, null),
    ).sortedBy(SeasonDownloadTask::createdAt)

    fun queueSeason(
        media: MediaItem,
        season: Int,
        episodes: List<Episode>,
        preference: DownloadPreference,
    ): SeasonQueueResult = synchronized(DOWNLOAD_LOCK) {
        val candidates = episodes
            .filter { it.season == season && it.number > 0 }
            .distinctBy(Episode::number)
        val existingKeys = buildSet {
            storedDownloads().mapTo(this, DownloadEntry::targetKey)
            seasonDownloadTasks().mapTo(this, SeasonDownloadTask::targetKey)
        }
        val missing = missingSeasonEpisodes(media.id, season, candidates, existingKeys)
        if (missing.isEmpty()) {
            return@synchronized SeasonQueueResult(null, 0, candidates.size)
        }

        val batchId = UUID.randomUUID().toString()
        val createdAt = System.currentTimeMillis()
        val tasks = missing.mapIndexed { index, episode ->
            SeasonDownloadTask(
                id = UUID.randomUUID().toString(),
                batchId = batchId,
                media = media,
                season = season,
                episode = episode.number,
                episodeTitle = episode.title,
                preference = preference,
                batchSize = missing.size,
                createdAt = createdAt + index,
            )
        }
        writeSeasonDownloadTasks(seasonDownloadTasks() + tasks)
        SeasonQueueResult(batchId, tasks.size, candidates.size - tasks.size)
    }

    fun nextSeasonDownloadTask(): SeasonDownloadTask? = seasonDownloadTasks()
        .firstOrNull { it.attempts < MAX_SEASON_ATTEMPTS }

    fun hasActiveSeasonDownload(): Boolean = downloads().any { entry ->
        entry.batchId != null && entry.state in setOf(DownloadState.Queued, DownloadState.Downloading)
    }

    fun ownsSeasonDownload(downloadId: Long): Boolean =
        storedDownloads().any { it.downloadId == downloadId && it.batchId != null }

    fun recordSeasonTaskFailure(taskId: String, message: String): Boolean = synchronized(DOWNLOAD_LOCK) {
        var terminal = true
        val updated = seasonDownloadTasks().map { task ->
            if (task.id != taskId) return@map task
            val attempts = task.attempts + 1
            terminal = attempts >= MAX_SEASON_ATTEMPTS
            task.copy(
                attempts = attempts,
                lastError = message.trim().take(160).ifBlank { "Source could not be prepared" },
            )
        }
        writeSeasonDownloadTasks(updated)
        terminal
    }

    fun startSeasonDownload(task: SeasonDownloadTask, source: StreamSource): DownloadEntry? =
        synchronized(DOWNLOAD_LOCK) {
            if (storedDownloads().any { it.targetKey == task.targetKey }) {
                writeSeasonDownloadTasks(seasonDownloadTasks().filterNot { it.id == task.id })
                return@synchronized null
            }
            enqueueDownloadLocked(
                media = task.media,
                source = source,
                episode = Episode(task.season, task.episode, task.episodeTitle),
                batchId = task.batchId,
                batchSize = task.batchSize,
                ignoredTaskId = task.id,
            )
        }

    fun enqueueDownload(
        media: MediaItem,
        source: StreamSource,
        episode: Episode? = null,
    ): DownloadEntry = synchronized(DOWNLOAD_LOCK) {
        enqueueDownloadLocked(media, source, episode)
    }

    private fun enqueueDownloadLocked(
        media: MediaItem,
        source: StreamSource,
        episode: Episode? = null,
        batchId: String? = null,
        batchSize: Int = 0,
        ignoredTaskId: String? = null,
    ): DownloadEntry {
        val seasonNumber = episode?.season ?: 0
        val episodeNumber = episode?.number ?: 0
        val targetKey = com.therealsylva.roaches.data.model.downloadTargetKey(
            media.id,
            seasonNumber,
            episodeNumber,
        )
        val duplicate = storedDownloads().any { it.targetKey == targetKey } ||
            seasonDownloadTasks().any { it.id != ignoredTaskId && it.targetKey == targetKey }
        check(!duplicate) { "This title is already in Downloads." }

        val manager = appContext.getSystemService(DownloadManager::class.java)
        val episodeSuffix = if (seasonNumber > 0 && episodeNumber > 0) {
            "-S${seasonNumber.toString().padStart(2, '0')}E${episodeNumber.toString().padStart(2, '0')}"
        } else {
            ""
        }
        val episodeLabel = if (episodeSuffix.isBlank()) "" else " · S$seasonNumber E$episodeNumber"
        val request = DownloadManager.Request(Uri.parse(source.url))
            .setTitle(media.title + episodeLabel)
            .setDescription("${source.qualityLabel} · Roaches")
            .setMimeType("video/*")
            .setAllowedOverMetered(!settings().wifiOnlyDownloads)
            .setAllowedOverRoaming(false)
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalFilesDir(
                appContext,
                Environment.DIRECTORY_MOVIES,
                downloadFileName(media.title, seasonNumber, episodeNumber, source),
            )
        val id = manager.enqueue(request)
        val entry = DownloadEntry(
            downloadId = id,
            media = media,
            source = source,
            createdAt = System.currentTimeMillis(),
            season = seasonNumber,
            episode = episodeNumber,
            episodeTitle = episode?.title,
            batchId = batchId,
            batchSize = batchSize,
        )
        val current = storedDownloads().filterNot { it.downloadId == id }.toMutableList()
        current.add(0, entry)
        writeDownloads(current)
        if (ignoredTaskId != null) {
            writeSeasonDownloadTasks(seasonDownloadTasks().filterNot { it.id == ignoredTaskId })
        }
        return entry
    }

    fun removeDownload(entry: DownloadEntry) = synchronized(DOWNLOAD_LOCK) {
        appContext.getSystemService(DownloadManager::class.java).remove(entry.downloadId)
        val remaining = storedDownloads().filterNot { it.downloadId == entry.downloadId }
        val batchId = entry.batchId
        if (batchId == null) {
            writeDownloads(remaining)
            return@synchronized
        }
        val tasks = seasonDownloadTasks()
        val remainingInBatch = remaining.count { it.batchId == batchId } + tasks.count { it.batchId == batchId }
        val resizedEntries = remaining.map { candidate ->
            if (candidate.batchId == batchId) candidate.copy(batchSize = remainingInBatch) else candidate
        }
        val resizedTasks = tasks.map { task ->
            if (task.batchId == batchId) task.copy(batchSize = remainingInBatch) else task
        }
        writeDownloads(resizedEntries)
        writeSeasonDownloadTasks(resizedTasks)
    }

    fun cancelSeasonDownload(batchId: String) = synchronized(DOWNLOAD_LOCK) {
        val entries = storedDownloads()
        val ids = entries.filter { it.batchId == batchId }.map(DownloadEntry::downloadId).toLongArray()
        if (ids.isNotEmpty()) appContext.getSystemService(DownloadManager::class.java).remove(*ids)
        writeDownloads(entries.filterNot { it.batchId == batchId })
        writeSeasonDownloadTasks(seasonDownloadTasks().filterNot { it.batchId == batchId })
    }

    fun retrySeasonDownload(batchId: String): Int = synchronized(DOWNLOAD_LOCK) {
        val queried = downloads()
        val failedEntries = queried.filter {
            it.batchId == batchId && it.state in setOf(DownloadState.Failed, DownloadState.Missing)
        }
        val entriesToKeep = storedDownloads().filterNot { stored ->
            failedEntries.any { it.downloadId == stored.downloadId }
        }
        val existingTasks = seasonDownloadTasks()
        val resetTasks = existingTasks.map { task ->
            if (task.batchId == batchId && task.attempts >= MAX_SEASON_ATTEMPTS) {
                task.copy(attempts = 0, lastError = null)
            } else {
                task
            }
        }.toMutableList()
        val knownKeys = resetTasks.mapTo(mutableSetOf(), SeasonDownloadTask::targetKey)
        failedEntries.forEach { entry ->
            if (!knownKeys.add(entry.targetKey) || entry.season <= 0 || entry.episode <= 0) return@forEach
            resetTasks += SeasonDownloadTask(
                id = UUID.randomUUID().toString(),
                batchId = batchId,
                media = entry.media,
                season = entry.season,
                episode = entry.episode,
                episodeTitle = entry.episodeTitle ?: "Episode ${entry.episode}",
                preference = DownloadPreference(entry.source.resolution, entry.source.audio),
                batchSize = entry.batchSize,
                createdAt = System.currentTimeMillis() + resetTasks.size,
            )
        }
        failedEntries.forEach { entry ->
            appContext.getSystemService(DownloadManager::class.java).remove(entry.downloadId)
        }
        writeDownloads(entriesToKeep)
        writeSeasonDownloadTasks(resetTasks)
        failedEntries.size + existingTasks.count {
            it.batchId == batchId && it.attempts >= MAX_SEASON_ATTEMPTS
        }
    }

    fun seasonDownloadProgress(
        downloads: List<DownloadEntry> = downloads(),
        tasks: List<SeasonDownloadTask> = seasonDownloadTasks(),
    ): List<SeasonDownloadProgress> {
        val batchIds = (downloads.mapNotNull(DownloadEntry::batchId) + tasks.map(SeasonDownloadTask::batchId))
            .distinct()
        return batchIds.mapNotNull { batchId ->
            val entries = downloads.filter { it.batchId == batchId }
            val pending = tasks.filter { it.batchId == batchId }
            val media = entries.firstOrNull()?.media ?: pending.firstOrNull()?.media ?: return@mapNotNull null
            val season = entries.firstOrNull()?.season ?: pending.firstOrNull()?.season ?: return@mapNotNull null
            val total = (entries.map(DownloadEntry::batchSize) + pending.map(SeasonDownloadTask::batchSize))
                .maxOrNull()
                ?.coerceAtLeast(entries.size + pending.size)
                ?: return@mapNotNull null
            val active = entries.firstOrNull {
                it.state in setOf(DownloadState.Queued, DownloadState.Downloading)
            }
            SeasonDownloadProgress(
                batchId = batchId,
                media = media,
                season = season,
                totalCount = total,
                readyCount = entries.count { it.state == DownloadState.Complete },
                failedCount = entries.count { it.state in setOf(DownloadState.Failed, DownloadState.Missing) } +
                    pending.count { it.attempts >= MAX_SEASON_ATTEMPTS },
                queuedCount = pending.count { it.attempts < MAX_SEASON_ATTEMPTS } +
                    entries.count { it.state == DownloadState.Queued },
                activeEpisode = active?.episode,
                activeProgress = active?.progress ?: 0f,
                statusMessage = active?.statusMessage
                    ?: pending.firstOrNull { it.attempts >= MAX_SEASON_ATTEMPTS }?.lastError,
            )
        }.sortedByDescending { progress ->
            (downloads.filter { it.batchId == progress.batchId }.maxOfOrNull(DownloadEntry::createdAt)
                ?: tasks.filter { it.batchId == progress.batchId }.maxOfOrNull(SeasonDownloadTask::createdAt)
                ?: 0L)
        }
    }

    private fun queryDownload(entry: DownloadEntry): DownloadEntry {
        val manager = appContext.getSystemService(DownloadManager::class.java)
        return runCatching {
            manager.query(DownloadManager.Query().setFilterById(entry.downloadId)).use { cursor ->
                if (!cursor.moveToFirst()) {
                    return@use entry.copy(state = DownloadState.Missing, statusMessage = "File unavailable")
                }
                val status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                val downloaded = cursor.getLong(
                    cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR),
                )
                val total = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
                val uri = cursor.getString(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_LOCAL_URI))
                val reason = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON))
                entry.copy(
                    state = when (status) {
                        DownloadManager.STATUS_PENDING -> DownloadState.Queued
                        DownloadManager.STATUS_RUNNING -> DownloadState.Downloading
                        DownloadManager.STATUS_PAUSED -> DownloadState.Queued
                        DownloadManager.STATUS_SUCCESSFUL -> DownloadState.Complete
                        DownloadManager.STATUS_FAILED -> DownloadState.Failed
                        else -> DownloadState.Missing
                    },
                    progress = if (total > 0L) (downloaded.toFloat() / total).coerceIn(0f, 1f) else 0f,
                    localUri = uri,
                    statusMessage = downloadStatusMessage(status, reason, settings().wifiOnlyDownloads),
                )
            }
        }.getOrDefault(entry.copy(state = DownloadState.Missing, statusMessage = "File unavailable"))
    }

    private fun storedDownloads(): List<DownloadEntry> = decodeDownloadEntries(
        preferences.getString("downloads", null),
    )

    private fun writeDownloads(downloads: List<DownloadEntry>) {
        preferences.edit().putString("downloads", encodeDownloadEntries(downloads)).apply()
    }

    private fun writeSeasonDownloadTasks(tasks: List<SeasonDownloadTask>) {
        preferences.edit().putString(SEASON_DOWNLOAD_QUEUE, encodeSeasonDownloadTasks(tasks)).apply()
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

    private fun homeCacheKey(region: ContentRegion) = "home_cache_v1_${region.name}"
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

internal fun encodeHomeCache(shelves: List<Shelf>): String = JSONObject()
    .put("version", 1)
    .put("savedAt", System.currentTimeMillis())
    .put(
        "shelves",
        JSONArray(
            shelves.filter { it.items.isNotEmpty() }.distinctBy(Shelf::id).take(6).map { shelf ->
                JSONObject()
                    .put("id", shelf.id)
                    .put("title", shelf.title)
                    .put("items", JSONArray(shelf.items.distinctBy(MediaItem::id).take(20).map(::mediaToJson)))
            },
        ),
    )
    .toString()

internal fun decodeHomeCache(raw: String?): List<Shelf> = runCatching {
    val root = JSONObject(raw.orEmpty())
    if (root.optInt("version") != 1) return@runCatching emptyList()
    val shelves = root.optJSONArray("shelves") ?: return@runCatching emptyList()
    buildList {
        repeat(shelves.length()) { index ->
            val value = shelves.optJSONObject(index) ?: return@repeat
            val id = value.optString("id").takeIf(String::isNotBlank) ?: return@repeat
            val title = value.optString("title").takeIf(String::isNotBlank) ?: return@repeat
            val items = (value.optJSONArray("items") ?: JSONArray()).objects().mapNotNull(::mediaFromJson)
            if (items.isNotEmpty()) add(Shelf(id, title, items))
        }
    }
}.getOrDefault(emptyList())

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
    .put("season", entry.season)
    .put("episode", entry.episode)
    .put("episodeTitle", entry.episodeTitle)
    .put("batch", entry.batchId)
    .put("batchSize", entry.batchSize)

private fun downloadFromJson(json: JSONObject): DownloadEntry? {
    val media = json.optJSONObject("media")?.let(::mediaFromJson) ?: return null
    val source = json.optJSONObject("source")?.let(::streamFromJson) ?: return null
    return DownloadEntry(
        downloadId = json.optLong("id"),
        media = media,
        source = source,
        createdAt = json.optLong("created"),
        season = json.optInt("season"),
        episode = json.optInt("episode"),
        episodeTitle = json.nullableString("episodeTitle"),
        batchId = json.nullableString("batch"),
        batchSize = json.optInt("batchSize"),
    )
}

internal fun encodeDownloadEntries(entries: List<DownloadEntry>): String =
    JSONArray(entries.map(::downloadToJson)).toString()

internal fun decodeDownloadEntries(raw: String?): List<DownloadEntry> = runCatching {
    JSONArray(raw ?: "[]").objects().mapNotNull(::downloadFromJson)
}.getOrDefault(emptyList())

private fun seasonDownloadTaskToJson(task: SeasonDownloadTask) = JSONObject()
    .put("id", task.id)
    .put("batch", task.batchId)
    .put("media", mediaToJson(task.media))
    .put("season", task.season)
    .put("episode", task.episode)
    .put("episodeTitle", task.episodeTitle)
    .put("resolution", task.preference.resolution)
    .put("audio", task.preference.audio)
    .put("batchSize", task.batchSize)
    .put("created", task.createdAt)
    .put("attempts", task.attempts)
    .put("lastError", task.lastError)

private fun seasonDownloadTaskFromJson(json: JSONObject): SeasonDownloadTask? {
    val id = json.optString("id").takeIf(String::isNotBlank) ?: return null
    val batchId = json.optString("batch").takeIf(String::isNotBlank) ?: return null
    val media = json.optJSONObject("media")?.let(::mediaFromJson) ?: return null
    val season = json.optInt("season").takeIf { it > 0 } ?: return null
    val episode = json.optInt("episode").takeIf { it > 0 } ?: return null
    return SeasonDownloadTask(
        id = id,
        batchId = batchId,
        media = media,
        season = season,
        episode = episode,
        episodeTitle = json.optString("episodeTitle", "Episode $episode"),
        preference = DownloadPreference(
            resolution = json.optInt("resolution"),
            audio = json.nullableString("audio"),
        ),
        batchSize = json.optInt("batchSize").coerceAtLeast(1),
        createdAt = json.optLong("created"),
        attempts = json.optInt("attempts").coerceAtLeast(0),
        lastError = json.nullableString("lastError"),
    )
}

internal fun encodeSeasonDownloadTasks(tasks: List<SeasonDownloadTask>): String =
    JSONArray(tasks.map(::seasonDownloadTaskToJson)).toString()

internal fun decodeSeasonDownloadTasks(raw: String?): List<SeasonDownloadTask> = runCatching {
    JSONArray(raw ?: "[]").objects().mapNotNull(::seasonDownloadTaskFromJson)
}.getOrDefault(emptyList())

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

internal fun downloadStatusMessage(status: Int, reason: Int, wifiOnly: Boolean): String? = when (status) {
    DownloadManager.STATUS_PENDING -> if (wifiOnly) "Queued · Wi-Fi only" else "Queued by Android"
    DownloadManager.STATUS_PAUSED -> when (reason) {
        DownloadManager.PAUSED_WAITING_TO_RETRY -> "Waiting to retry"
        DownloadManager.PAUSED_WAITING_FOR_NETWORK -> "Waiting for a network"
        DownloadManager.PAUSED_QUEUED_FOR_WIFI -> "Waiting for Wi-Fi"
        else -> "Download paused"
    }
    DownloadManager.STATUS_FAILED -> when (reason) {
        DownloadManager.ERROR_INSUFFICIENT_SPACE -> "Not enough storage"
        DownloadManager.ERROR_FILE_ALREADY_EXISTS -> "File already exists"
        DownloadManager.ERROR_CANNOT_RESUME -> "Download could not resume"
        DownloadManager.ERROR_DEVICE_NOT_FOUND -> "Storage is unavailable"
        DownloadManager.ERROR_UNHANDLED_HTTP_CODE,
        DownloadManager.ERROR_HTTP_DATA_ERROR,
        DownloadManager.ERROR_TOO_MANY_REDIRECTS,
        -> "Download link failed"
        else -> "Download failed"
    }
    else -> null
}

private fun JSONObject.nullableString(key: String): String? = opt(key)
    ?.takeUnless { it == JSONObject.NULL }
    ?.toString()
    ?.takeIf(String::isNotBlank)
