package com.therealsylva.roaches.data.repository

import com.therealsylva.roaches.data.local.LocalStore
import com.therealsylva.roaches.data.model.ContentRegion
import com.therealsylva.roaches.data.model.Episode
import com.therealsylva.roaches.data.model.MediaDetails
import com.therealsylva.roaches.data.model.MediaItem
import com.therealsylva.roaches.data.model.MediaKind
import com.therealsylva.roaches.data.model.PlaybackQuality
import com.therealsylva.roaches.data.model.Season
import com.therealsylva.roaches.data.model.Shelf
import com.therealsylva.roaches.data.model.StreamSource
import com.therealsylva.roaches.data.model.SubtitleTrack
import com.therealsylva.roaches.data.remote.MovieBoxApi
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

class RoachesRepository(store: LocalStore) {
    private val settings = store.settings()
    private val api = MovieBoxApi(store.clientIdentity())

    suspend fun discover(): List<Shelf> {
        val payload = api.home()
        val groups = (payload as? JSONObject)?.optJSONArray("items") ?: JSONArray()
        val shelves = buildList {
            repeat(groups.length()) { index ->
                val group = groups.optJSONObject(index) ?: return@repeat
                val title = group.string("title", "name").orEmpty()
                val subjects = group.optJSONArray("subjects") ?: return@repeat
                val items = subjects.catalogueItems(settings.contentRegion)
                if (items.isNotEmpty()) {
                    add(Shelf("home-$index", normalizeShelfTitle(title, index), items))
                }
            }
        }.sortedBy { it.title.regionalPenalty(settings.contentRegion) }
        return shelves.ifEmpty {
            val fallback = when (payload) {
                is JSONArray -> payload.catalogueItems(settings.contentRegion)
                is JSONObject -> payload.optJSONArray("subjects")
                    ?.catalogueItems(settings.contentRegion)
                    .orEmpty()
                else -> emptyList()
            }
            if (fallback.isEmpty()) emptyList() else listOf(Shelf("discover", "Discover", fallback))
        }
    }

    suspend fun search(query: String, page: Int = 1): List<MediaItem> {
        val payload = api.search(query, page)
        return parseSearchResults(payload, query)
    }

    suspend fun details(seed: MediaItem): MediaDetails {
        val initial = api.details(seed.id)
        val preferredId = initial.preferredSubjectId(seed.id)
        val json = if (preferredId == seed.id) {
            initial
        } else {
            runCatching { api.details(preferredId) }.getOrDefault(initial)
        }
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
        val parsed = buildList {
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
        val quality = settings.playbackQuality
        return if (quality == PlaybackQuality.Auto) {
            parsed.sortedWith(compareByDescending<StreamSource>(StreamSource::resolution).thenBy { it.sizeBytes })
        } else {
            parsed.sortedWith(
                compareBy<StreamSource> { source ->
                    if (source.resolution in 1..quality.height) 0 else 1
                }.thenByDescending(StreamSource::resolution),
            )
        }
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

private data class RankedMedia(val item: MediaItem, val penalty: Int, val index: Int)

private fun JSONArray.catalogueItems(region: ContentRegion): List<MediaItem> = buildList {
    repeat(length()) { index ->
        val value = optJSONObject(index) ?: return@repeat
        value.toMediaItem()?.let { item -> add(RankedMedia(item, value.cataloguePenalty(region), index)) }
    }
}.sortedWith(compareBy<RankedMedia>(RankedMedia::penalty).thenBy(RankedMedia::index))
    .distinctBy { ranked ->
        with(ranked.item) { "${title.lowercase(Locale.US)}-$year-$kind" }
    }
    .map(RankedMedia::item)

internal fun parseSearchResults(payload: Any, query: String): List<MediaItem> {
    val subjects = buildList {
        collectMediaObjects(payload, this)
    }
    return subjects.mapIndexedNotNull { index, value ->
        value.toMediaItem()?.let { item -> RankedMedia(item, value.variantPenalty(), index) }
    }.sortedWith(compareBy<RankedMedia>(RankedMedia::penalty).thenBy(RankedMedia::index))
        .distinctBy { ranked ->
            with(ranked.item) { "${title.lowercase(Locale.US)}-$year-$kind" }
        }
        .map(RankedMedia::item)
        .sortedWith(
            compareByDescending<MediaItem> { it.title.equals(query, ignoreCase = true) }
                .thenByDescending { it.title.startsWith(query, ignoreCase = true) }
                .thenByDescending(MediaItem::year),
        )
}

private fun collectMediaObjects(value: Any?, output: MutableList<JSONObject>) {
    when (value) {
        is JSONArray -> repeat(value.length()) { index -> collectMediaObjects(value.opt(index), output) }
        is JSONObject -> {
            if (value.string("subjectId", "id") != null && value.string("title", "name") != null) {
                output.add(value)
            }
            value.keys().forEach { key -> collectMediaObjects(value.opt(key), output) }
        }
    }
}

private fun JSONObject.cataloguePenalty(region: ContentRegion): Int = listOfNotNull(
    string("title", "name"),
    string("language", "lanName", "audio", "originalLanguage"),
    string("countryName", "country", "area"),
).joinToString(" ").regionalPenalty(region)

private fun JSONObject.variantPenalty(): Int {
    val label = string("language", "lanName", "audio", "originalLanguage").orEmpty()
    val title = string("title", "name").orEmpty()
    return when {
        label.contains("original", ignoreCase = true) -> 0
        label.contains("english", ignoreCase = true) || title.contains("[english]", ignoreCase = true) -> 1
        INDIA_MARKER.containsMatchIn("$label $title") -> 2
        else -> 1
    }
}

private fun JSONObject.preferredSubjectId(fallback: String): String {
    val dubs = optJSONArray("dubs") ?: return fallback
    return buildList {
        repeat(dubs.length()) { index ->
            val dub = dubs.optJSONObject(index) ?: return@repeat
            val id = dub.string("subjectId", "id") ?: return@repeat
            val language = dub.string("lanName", "language", "name").orEmpty()
            val priority = when {
                language.contains("original", ignoreCase = true) -> 0
                language.contains("english", ignoreCase = true) -> 1
                else -> 2
            }
            add(Triple(priority, index, id))
        }
    }.minWithOrNull(compareBy<Triple<Int, Int, String>> { it.first }.thenBy { it.second })?.third ?: fallback
}

private fun String.regionalPenalty(region: ContentRegion): Int = when (region) {
    ContentRegion.GlobalEnglish -> if (INDIA_MARKER.containsMatchIn(this)) 1 else 0
    ContentRegion.UnitedKingdom -> when {
        UK_MARKER.containsMatchIn(this) -> 0
        INDIA_MARKER.containsMatchIn(this) -> 2
        else -> 1
    }
    ContentRegion.Nigeria -> when {
        NIGERIA_MARKER.containsMatchIn(this) -> 0
        INDIA_MARKER.containsMatchIn(this) -> 2
        else -> 1
    }
}

private val INDIA_MARKER = Regex(
    "(?i)\\b(india|bollywood|hindi|tamil|telugu|malayalam|kannada|punjabi|bengali|marathi)\\b",
)
private val UK_MARKER = Regex("(?i)\\b(united kingdom|british|britain|england|english)\\b")
private val NIGERIA_MARKER = Regex("(?i)\\b(nigeria|nigerian|nollywood)\\b")
private val DECORATIVE_SYMBOL = Regex("[\\p{So}\\p{Sk}\\uFE0F\\u200D]")

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
    val cleaned = raw.replace(DECORATIVE_SYMBOL, " ").replace(Regex("\\s+"), " ").trim()
    return cleaned.takeIf(String::isNotBlank) ?: if (index == 0) "Featured" else "More to watch"
}

private fun cleanTitle(raw: String): String {
    var title = raw.replace(DECORATIVE_SYMBOL, " ").replace(Regex("\\s+"), " ").trim()
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
