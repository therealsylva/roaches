package com.therealsylva.roaches.data.repository

import com.therealsylva.roaches.data.local.LocalStore
import com.therealsylva.roaches.data.model.BrowseCategory
import com.therealsylva.roaches.data.model.ContentRegion
import com.therealsylva.roaches.data.model.Episode
import com.therealsylva.roaches.data.model.MediaDetails
import com.therealsylva.roaches.data.model.MediaItem
import com.therealsylva.roaches.data.model.MediaKind
import com.therealsylva.roaches.data.model.PlaybackQuality
import com.therealsylva.roaches.data.model.PreferredAudio
import com.therealsylva.roaches.data.model.Season
import com.therealsylva.roaches.data.model.Shelf
import com.therealsylva.roaches.data.model.StreamSource
import com.therealsylva.roaches.data.model.SubtitleTrack
import com.therealsylva.roaches.data.remote.MovieBoxApi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

class RoachesRepository(private val store: LocalStore) {
    private val settings = store.settings()
    private val api = MovieBoxApi(store.clientIdentity())

    fun cachedDiscover(): List<Shelf> = store.cachedHome(settings.contentRegion)

    suspend fun discover(): List<Shelf> = resolveHome(
        cached = cachedDiscover(),
        fetchCatalogue = { rail ->
            parseCatalogueResults(
                api.catalogue(
                    genre = rail.genre,
                    country = settings.contentRegion.providerCountry(),
                    sort = rail.sort,
                ),
                settings.contentRegion,
            )
        },
        fetchLegacy = {
            parseLegacyHome(api.home(), settings.contentRegion)
        },
        persist = { shelves -> store.cacheHome(settings.contentRegion, shelves) },
    )

    suspend fun search(query: String, page: Int = 1): List<MediaItem> {
        val payload = api.search(query, page)
        return parseSearchResults(payload, query)
    }

    suspend fun category(category: BrowseCategory, page: Int = 1): List<MediaItem> {
        val payload = api.catalogue(
            genre = category.providerGenre,
            country = category.providerCountry ?: settings.contentRegion.providerCountry(),
            page = page,
        )
        return parseCatalogueResults(payload, settings.contentRegion)
    }

    suspend fun details(seed: MediaItem): MediaDetails {
        val initial = api.details(seed.id)
        val preferred = initial.preferredSubject(settings.preferredAudio, seed.id)
        val json = if (preferred.id == seed.id) {
            initial
        } else {
            runCatching { api.details(preferred.id) }.getOrDefault(initial)
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
            audioLanguage = preferred.language ?: json.string("lanName", "language", "audio"),
            seasons = parseSeasons(json.opt("seasons")),
        )
    }

    suspend fun sources(
        subjectId: String,
        season: Int,
        episode: Int,
        languageHint: String? = null,
    ): List<StreamSource> {
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
                        codec = source.string("codec", "codecName", "encode", "format"),
                        audio = source.string("audio", "language", "lanName")
                            ?: detectLanguage(source.string("filename", "fileName", "name", "title"))
                            ?: languageHint,
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

internal data class HomeRail(val id: String, val title: String, val genre: String, val sort: String)

private val HOME_RAILS = listOf(
    HomeRail("popular", "Popular now", "All", "Hottest"),
    HomeRail("new", "New releases", "All", "Latest"),
    HomeRail("action", "Action", "Action", "Hottest"),
    HomeRail("drama", "Drama", "Drama", "Hottest"),
    HomeRail("comedy", "Comedy", "Comedy", "Hottest"),
)

internal suspend fun resolveHome(
    cached: List<Shelf>,
    fetchCatalogue: suspend (HomeRail) -> List<MediaItem>,
    fetchLegacy: suspend () -> List<Shelf>,
    persist: (List<Shelf>) -> Unit = {},
): List<Shelf> {
    val fresh = mutableListOf<Shelf>()
    var structuredFailure: Throwable? = null

    for (batch in HOME_RAILS.chunked(2)) {
        val results = coroutineScope {
            batch.map { rail ->
                async {
                    try {
                        HomeRailResult(rail, fetchCatalogue(rail), null)
                    } catch (cancellation: CancellationException) {
                        throw cancellation
                    } catch (failure: Throwable) {
                        HomeRailResult(rail, emptyList(), failure)
                    }
                }
            }.awaitAll()
        }
        results.forEach { result ->
            if (result.items.isNotEmpty()) fresh += Shelf(result.rail.id, result.rail.title, result.items)
            if (structuredFailure == null) structuredFailure = result.failure
        }
        if (results.any { it.failure != null }) break
    }

    if (fresh.isNotEmpty()) {
        val resolved = mergeHome(fresh, cached)
        runCatching { persist(resolved) }
        return resolved
    }

    var legacyFailure: Throwable? = null
    val legacy = try {
        fetchLegacy()
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (failure: Throwable) {
        legacyFailure = failure
        emptyList()
    }
    if (legacy.isNotEmpty()) {
        val resolved = if (cached.isEmpty()) legacy else mergeHome(cached, legacy)
        runCatching { persist(resolved) }
        return resolved
    }
    if (cached.isNotEmpty()) return cached

    throw structuredFailure ?: legacyFailure
        ?: IllegalStateException("No Home sections were returned")
}

private fun mergeHome(primary: List<Shelf>, fallback: List<Shelf>): List<Shelf> =
    (primary + fallback.filter { fallbackShelf ->
        primary.none { it.id == fallbackShelf.id }
    }).filter { it.items.isNotEmpty() }
        .distinctBy(Shelf::id)
        .take(6)

private data class HomeRailResult(
    val rail: HomeRail,
    val items: List<MediaItem>,
    val failure: Throwable?,
)

internal fun parseLegacyHome(payload: Any, region: ContentRegion): List<Shelf> {
    val root = payload as? JSONObject
    val groups = root?.optJSONArray("items") ?: JSONArray()
    val shelves = buildList {
        repeat(groups.length()) { index ->
            val group = groups.optJSONObject(index) ?: return@repeat
            val rawTitle = group.string("title", "name").orEmpty()
            if (INDIA_MARKER.containsMatchIn(rawTitle)) return@repeat
            val items = (group.optJSONArray("subjects") ?: JSONArray()).catalogueItems(region)
            if (items.isNotEmpty()) {
                add(Shelf("legacy-$index", normalizeShelfTitle(rawTitle, index), items))
            }
        }
    }
    if (shelves.isNotEmpty()) return shelves.take(6)

    val fallback = when (payload) {
        is JSONArray -> payload.catalogueItems(region)
        is JSONObject -> payload.optJSONArray("subjects")?.catalogueItems(region).orEmpty()
        else -> emptyList()
    }
    return if (fallback.isEmpty()) emptyList() else listOf(Shelf("legacy", "Discover", fallback))
}

internal fun parseCatalogueResults(payload: Any, region: ContentRegion): List<MediaItem> {
    val items = when (payload) {
        is JSONArray -> payload
        is JSONObject -> payload.optJSONArray("items")
            ?: payload.optJSONArray("subjects")
            ?: JSONArray()
        else -> JSONArray()
    }
    return items.catalogueItems(region)
}

private fun JSONArray.catalogueItems(region: ContentRegion): List<MediaItem> = buildList {
    repeat(length()) { index ->
        val value = optJSONObject(index) ?: return@repeat
        if (!value.isCatalogueTitle() || !value.isAllowedInCatalogue()) return@repeat
        value.toMediaItem()?.let { item -> add(RankedMedia(item, value.cataloguePenalty(region), index)) }
    }
}.sortedWith(compareBy<RankedMedia>(RankedMedia::penalty).thenBy(RankedMedia::index))
    .distinctBy { ranked ->
        with(ranked.item) { "${title.lowercase(Locale.US)}-$year-$kind" }
    }
    .map(RankedMedia::item)

internal fun parseSearchResults(payload: Any, query: String): List<MediaItem> {
    val subjects = primarySearchObjects(payload)
    return subjects.mapIndexedNotNull { index, value ->
        if (!value.isCatalogueTitle()) return@mapIndexedNotNull null
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

private fun primarySearchObjects(payload: Any): List<JSONObject> {
    val subjects = when (payload) {
        is JSONArray -> payload
        is JSONObject -> payload.primarySubjectGroup("results")
            ?: payload.primarySubjectGroup("groups")
            ?: payload.optJSONArray("subjects")
            ?: payload.optJSONArray("items")
            ?: JSONArray()
        else -> JSONArray()
    }
    return buildList {
        repeat(subjects.length()) { index ->
            subjects.optJSONObject(index)?.let(::add)
        }
    }
}

private fun JSONObject.primarySubjectGroup(key: String): JSONArray? {
    val groups = optJSONArray(key) ?: return null
    repeat(groups.length()) { index ->
        val group = groups.optJSONObject(index) ?: return@repeat
        val subjects = group.optJSONArray("subjects") ?: return@repeat
        val topicType = group.string("topicType").orEmpty()
        val moreTabId = group.string("moreTabId").orEmpty()
        if (subjects.length() > 0 && moreTabId.isBlank() &&
            (topicType.isBlank() || topicType.equals("SUBJECT", ignoreCase = true))
        ) {
            return subjects
        }
    }
    return null
}

private fun JSONObject.isCatalogueTitle(): Boolean {
    if (string("subjectId", "id") == null || string("title", "name") == null) return false
    val type = int("subjectType", "stype")
    return type == 0 || type == 1 || type == 2
}

private fun JSONObject.isAllowedInCatalogue(): Boolean {
    val title = string("title", "name").orEmpty()
    val country = string("countryName", "country", "area").orEmpty()
    val genre = string("genre", "genres").orEmpty()
    val language = string("language", "lanName", "audio", "originalLanguage").orEmpty()
    val taggedIndianAudio = INDIAN_AUDIO_MARKER.containsMatchIn(title)
    val originalOrEnglishAvailable = ORIGINAL_OR_ENGLISH_MARKER.containsMatchIn(language)
    return !INDIA_MARKER.containsMatchIn(country) &&
        !INDIA_CONTENT_MARKER.containsMatchIn(title) &&
        (!taggedIndianAudio || originalOrEnglishAvailable) &&
        !ADULT_MARKER.containsMatchIn(genre)
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
        INDIA_MARKER.containsMatchIn(title) -> 3
        label.contains("original", ignoreCase = true) -> 0
        label.contains("english", ignoreCase = true) || title.contains("[english]", ignoreCase = true) -> 1
        INDIA_MARKER.containsMatchIn(label) -> 3
        else -> 1
    }
}

private data class SubjectVariant(val id: String, val language: String?)

private fun JSONObject.preferredSubject(preference: PreferredAudio, fallback: String): SubjectVariant {
    val dubs = optJSONArray("dubs") ?: return SubjectVariant(fallback, null)
    val variants = buildList {
        repeat(dubs.length()) { index ->
            val dub = dubs.optJSONObject(index) ?: return@repeat
            val id = dub.string("subjectId", "id") ?: return@repeat
            val language = dub.string("lanName", "language", "name").orEmpty()
            add(Triple(index, id, language))
        }
    }
    if (variants.isEmpty()) return SubjectVariant(fallback, null)
    val chosen = variants.minWithOrNull(
        compareBy<Triple<Int, String, String>> { variant ->
            val language = variant.third
            when {
                preference == PreferredAudio.Any -> 0
                preference.matches.any { language.contains(it, ignoreCase = true) } -> 0
                language.contains("original", ignoreCase = true) -> 1
                language.contains("english", ignoreCase = true) -> 2
                else -> 3
            }
        }.thenBy { it.first },
    ) ?: return SubjectVariant(fallback, null)
    return SubjectVariant(chosen.second, chosen.third.takeIf(String::isNotBlank))
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
private val INDIA_CONTENT_MARKER = Regex("(?i)\\b(india|bollywood)\\b")
private val INDIAN_AUDIO_MARKER = Regex(
    "(?i)\\b(hindi|tamil|telugu|malayalam|kannada|punjabi|bengali|marathi)\\b",
)
private val ORIGINAL_OR_ENGLISH_MARKER = Regex("(?i)\\b(original|english|eng)\\b")
private val ADULT_MARKER = Regex("(?i)\\badult\\b")
private val UK_MARKER = Regex("(?i)\\b(united kingdom|british|britain|england|english)\\b")
private val NIGERIA_MARKER = Regex("(?i)\\b(nigeria|nigerian|nollywood)\\b")
private val DECORATIVE_SYMBOL = Regex("[\\p{So}\\p{Sk}\\uFE0F\\u200D]")

private fun ContentRegion.providerCountry(): String = when (this) {
    ContentRegion.GlobalEnglish -> "United States"
    ContentRegion.UnitedKingdom -> "United Kingdom"
    ContentRegion.Nigeria -> "Nigeria"
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

private fun detectLanguage(value: String?): String? {
    val text = value?.lowercase(Locale.US).orEmpty()
    return when {
        "multi audio" in text || "multi-audio" in text -> "Multi audio"
        "dual audio" in text || "dual-audio" in text -> "Dual audio"
        Regex("\\b(english|eng)\\b").containsMatchIn(text) -> "English"
        Regex("\\b(french|fra)\\b").containsMatchIn(text) -> "French"
        Regex("\\b(spanish|spa)\\b").containsMatchIn(text) -> "Spanish"
        Regex("\\b(arabic|ara)\\b").containsMatchIn(text) -> "Arabic"
        Regex("\\b(hindi|hin)\\b").containsMatchIn(text) -> "Hindi"
        else -> null
    }
}
