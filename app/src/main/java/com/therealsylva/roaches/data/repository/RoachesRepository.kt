package com.therealsylva.roaches.data.repository

import com.therealsylva.roaches.data.local.LocalStore
import com.therealsylva.roaches.data.model.Episode
import com.therealsylva.roaches.data.model.MediaDetails
import com.therealsylva.roaches.data.model.MediaItem
import com.therealsylva.roaches.data.model.MediaKind
import com.therealsylva.roaches.data.model.Season
import com.therealsylva.roaches.data.model.Shelf
import com.therealsylva.roaches.data.model.StreamSource
import com.therealsylva.roaches.data.model.SubtitleTrack
import com.therealsylva.roaches.data.remote.MovieBoxApi
import org.json.JSONArray
import org.json.JSONObject

class RoachesRepository(store: LocalStore) {
    private val api = MovieBoxApi(store.clientIdentity())

    suspend fun discover(): List<Shelf> {
        val payload = api.home()
        val groups = (payload as? JSONObject)?.optJSONArray("items") ?: JSONArray()
        return buildList {
            repeat(groups.length()) { index ->
                val group = groups.optJSONObject(index) ?: return@repeat
                val title = group.string("title", "name").orEmpty()
                val subjects = group.optJSONArray("subjects") ?: return@repeat
                val items = subjects.mediaItems().distinctBy(MediaItem::id)
                if (items.isNotEmpty()) {
                    add(Shelf("home-$index", normalizeShelfTitle(title, index), items))
                }
            }
        }.ifEmpty {
            val fallback = when (payload) {
                is JSONArray -> payload.mediaItems()
                is JSONObject -> payload.optJSONArray("subjects")?.mediaItems().orEmpty()
                else -> emptyList()
            }
            if (fallback.isEmpty()) emptyList() else listOf(Shelf("discover", "Discover", fallback))
        }
    }

    suspend fun search(query: String, page: Int = 1): List<MediaItem> {
        val payload = api.search(query, page)
        val root = payload as? JSONObject ?: return emptyList()
        val resultGroups = root.optJSONArray("results") ?: JSONArray()
        val subjects = buildList {
            repeat(resultGroups.length()) { index ->
                val group = resultGroups.optJSONObject(index) ?: return@repeat
                val values = group.optJSONArray("subjects") ?: return@repeat
                repeat(values.length()) { itemIndex -> values.optJSONObject(itemIndex)?.let(::add) }
            }
        }
        return JSONArray(subjects).mediaItems()
            .filter { item -> item.title.contains(query, ignoreCase = true) }
            .distinctBy { "${it.title.lowercase()}-${it.year}-${it.kind}" }
            .sortedWith(
                compareByDescending<MediaItem> { it.title.equals(query, ignoreCase = true) }
                    .thenByDescending { it.title.startsWith(query, ignoreCase = true) }
                    .thenByDescending(MediaItem::year),
            )
    }

    suspend fun details(seed: MediaItem): MediaDetails {
        val json = api.details(seed.id)
        val parsed = json.toMediaItem() ?: seed
        val item = parsed.copy(
            posterUrl = parsed.posterUrl ?: seed.posterUrl,
            backdropUrl = parsed.backdropUrl ?: seed.backdropUrl ?: seed.posterUrl,
            year = parsed.year.ifBlank { seed.year },
        )
        return MediaDetails(
            item = item,
            synopsis = json.string("description", "intro", "synopsis", "overview")
                ?: seed.description.orEmpty(),
            duration = json.string("duration", "runtime"),
            genres = json.stringList("genre", "genres"),
            director = json.string("director", "directors"),
            cast = json.string("stars", "actors", "cast"),
            country = json.string("countryName", "country"),
            seasons = parseSeasons(json.opt("seasons")),
        )
    }

    suspend fun sources(subjectId: String, season: Int, episode: Int): List<StreamSource> {
        val payload = api.resources(subjectId, season, episode)
        val resources = when (payload) {
            is JSONArray -> payload
            is JSONObject -> payload.optJSONArray("list") ?: JSONArray()
            else -> JSONArray()
        }
        return buildList {
            repeat(resources.length()) { index ->
                val source = resources.optJSONObject(index) ?: return@repeat
                val url = source.string("resourceLink", "url", "link") ?: return@repeat
                add(
                    StreamSource(
                        resourceId = source.string("resourceId", "id").orEmpty(),
                        url = url,
                        resolution = source.int("resolution", "quality"),
                        codec = source.string("codec", "encode", "format"),
                        audio = source.string("audio", "language", "lanName"),
                        sizeBytes = source.longOrNull("size", "fileSize", "sizeBytes"),
                        filename = source.string("filename", "fileName", "name"),
                    ),
                )
            }
        }.distinctBy { it.resourceId.ifBlank { it.url.substringBefore('?') } }
            .sortedWith(compareByDescending<StreamSource>(StreamSource::resolution).thenBy { it.sizeBytes })
    }

    suspend fun captions(subjectId: String, source: StreamSource): List<SubtitleTrack> {
        if (source.resourceId.isBlank()) return emptyList()
        val payload = api.captions(subjectId, source.resourceId)
        val captions = (payload as? JSONObject)?.optJSONArray("extCaptions") ?: JSONArray()
        return buildList {
            repeat(captions.length()) { index ->
                val caption = captions.optJSONObject(index) ?: return@repeat
                val url = caption.string("url", "link") ?: return@repeat
                add(SubtitleTrack(caption.string("lanName", "language", "name") ?: "Subtitle", url))
            }
        }.distinctBy(SubtitleTrack::url)
    }
}

private fun JSONArray.mediaItems(): List<MediaItem> = buildList {
    repeat(length()) { index -> optJSONObject(index)?.toMediaItem()?.let(::add) }
}

private fun JSONObject.toMediaItem(): MediaItem? {
    val id = string("subjectId", "id") ?: return null
    val rawTitle = string("title", "name") ?: return null
    val kindValue = int("subjectType", "stype")
    val poster = image("poster", "cover", "pic", "image", "thumbnail")
    val backdrop = image("backdrop", "banner", "still", "cover")
    return MediaItem(
        id = id,
        title = cleanTitle(rawTitle),
        kind = if (kindValue == 2) MediaKind.Series else MediaKind.Movie,
        year = string("releaseDate", "year", "releaseInfo")?.substringBefore('-').orEmpty(),
        posterUrl = poster,
        backdropUrl = backdrop,
        rating = string("imdbRatingValue", "imdbRate", "rating"),
        description = string("description", "intro", "synopsis", "overview"),
        seasonCount = int("season", "seasonCount"),
    )
}

private fun parseSeasons(payload: Any?): List<Season> {
    val array = when (payload) {
        is JSONArray -> payload
        is JSONObject -> payload.optJSONArray("seasons")
            ?: payload.optJSONArray("list")
            ?: payload.optJSONArray("seasonList")
            ?: JSONArray()
        else -> JSONArray()
    }
    return buildList {
        repeat(array.length()) { index ->
            val value = array.optJSONObject(index) ?: return@repeat
            val number = value.int("se", "season", "seasonNumber").takeIf { it > 0 } ?: index + 1
            val explicit = value.string("allEp", "episodes")
                ?.split(',', ' ', '|')
                ?.mapNotNull(String::toIntOrNull)
                ?.filter { it > 0 }
                .orEmpty()
            val max = value.int("maxEp", "episodeCount", "maxEpisode").coerceAtLeast(1)
            val episodeNumbers = explicit.ifEmpty { (1..max).toList() }
            add(Season(number, episodeNumbers.distinct().map { Episode(number, it) }))
        }
    }.distinctBy(Season::number).sortedBy(Season::number)
}

private fun JSONObject.image(vararg keys: String): String? = keys.firstNotNullOfOrNull { key ->
    when (val value = opt(key)) {
        is String -> value.takeIf(String::isNotBlank)
        is JSONObject -> value.string("url", "src", "image")
        else -> null
    }
}

private fun JSONObject.string(vararg keys: String): String? = keys.firstNotNullOfOrNull { key ->
    opt(key)?.takeUnless { it == JSONObject.NULL }?.toString()?.trim()?.takeIf(String::isNotBlank)
}

private fun JSONObject.int(vararg keys: String): Int = keys.firstNotNullOfOrNull { key ->
    when (val value = opt(key)) {
        is Number -> value.toInt()
        is String -> value.filter(Char::isDigit).toIntOrNull()
        else -> null
    }
} ?: 0

private fun JSONObject.longOrNull(vararg keys: String): Long? = keys.firstNotNullOfOrNull { key ->
    when (val value = opt(key)) {
        is Number -> value.toLong()
        is String -> value.filter(Char::isDigit).toLongOrNull()
        else -> null
    }
}?.takeIf { it > 0L }

private fun JSONObject.stringList(vararg keys: String): List<String> = keys.firstNotNullOfOrNull { key ->
    when (val value = opt(key)) {
        is JSONArray -> buildList {
            repeat(value.length()) { index ->
                value.optString(index).trim().takeIf(String::isNotBlank)?.let(::add)
            }
        }
        is String -> value.split(',', '|').map(String::trim).filter(String::isNotBlank)
        else -> null
    }?.takeIf { it.isNotEmpty() }
}.orEmpty()

private fun normalizeShelfTitle(raw: String, index: Int): String {
    val cleaned = raw.replace(Regex("\\s+"), " ").trim()
    return cleaned.takeIf(String::isNotBlank) ?: if (index == 0) "Featured" else "More to watch"
}

private fun cleanTitle(raw: String): String {
    var title = raw.trim()
    while (title.startsWith('[') && title.contains(']')) {
        title = title.substringAfter(']').trim()
    }
    title = title.substringBefore("[").trim()
    val suffix = title.substringAfterLast(" - ", "")
    if (suffix.contains(Regex("(?i)hindi|tamil|telugu|english|dub|audio|multi"))) {
        title = title.substringBeforeLast(" - ").trim()
    }
    return title.trimEnd('-', ':', '_', '.', ' ').ifBlank { raw.trim() }
}
