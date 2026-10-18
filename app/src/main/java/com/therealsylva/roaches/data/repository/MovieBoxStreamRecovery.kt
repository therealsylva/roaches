package com.therealsylva.roaches.data.repository

import com.therealsylva.roaches.data.model.StreamSource
import com.therealsylva.roaches.data.remote.DashResolver
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicReference

/** Keep the API envelope handling identical for listings and recovered resources. */
internal fun unwrapMovieBoxData(payload: Any?): Any? {
    var data = payload
    repeat(4) {
        val nested = (data as? JSONObject)?.opt("data")
        if (nested == null || nested == JSONObject.NULL) return data
        data = nested
    }
    return data
}

/** No stream URL, cookie or provider token is persisted or logged here. */
internal suspend fun resolveMovieBoxSources(
    subjectId: String,
    season: Int,
    episode: Int,
    languageHint: String?,
    userAgent: String,
    fetchPlayInfo: suspend () -> Any,
    fetchExactResources: suspend () -> Any,
    fetchLegacyResources: suspend () -> List<JSONObject>,
    recoverResource: suspend (resourceId: String, failedUrl: String) -> Any,
    verifyRecovered: suspend (StreamSource) -> Boolean,
    maxRecoveries: Int = 6,
    recoveryTimeoutMs: Long = 20_000,
): List<StreamSource> {
    require((season == 0 && episode == 0) || (season > 0 && episode > 0))
    val firstFailure = AtomicReference<Exception?>(null)
    suspend fun <T> attempt(block: suspend () -> T): T? = try {
        block()
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (failure: Exception) {
        firstFailure.compareAndSet(null, failure)
        null
    }

    val primary = attempt { parsePlayInfoSources(fetchPlayInfo(), languageHint) }.orEmpty()
    if (primary.isNotEmpty()) return primary

    var remaining = maxRecoveries.coerceIn(0, 12)
    val attempted = mutableSetOf<String>()

    suspend fun resolveRows(rows: List<JSONObject>): List<StreamSource> {
        val matching = rows.filter { it.matchesRecoveryTarget(subjectId, season, episode) }
        val direct = parseStreamSources(matching, languageHint)
            .filter { DashResolver.isMediaUrl(it.url) }
            .map { it.copy(headers = mapOf("User-Agent" to userAgent)) }
        val notices = matching.filter { row ->
            val id = row.recoveryId()
            val url = row.recoveryUrl()
            remaining > 0 && id != null && url != null &&
                DashResolver.isNoticeUrl(url) && attempted.add(id).also { added ->
                    if (added) remaining -= 1
                }
        }
        val recovered = mutableListOf<StreamSource>()
        // At most two outstanding recoveries, with a budget shared by v2 and
        // legacy fallback. A failed quality must not hide a working alternative.
        for (batch in notices.chunked(2)) {
            val results = coroutineScope {
                batch.map { original ->
                    async {
                        attempt {
                            withTimeoutOrNull(recoveryTimeoutMs) {
                                val payload = recoverResource(original.recoveryId()!!, original.recoveryUrl()!!)
                                val replacements = parseResourcePage(payload).items.filter { candidate ->
                                    candidate.recoveryId() == original.recoveryId() &&
                                        candidate.matchesRecoveryTarget(subjectId, season, episode)
                                }
                                for (candidate in replacements.take(4)) {
                                    val replacement = recoveredStreamSource(original, candidate, languageHint, userAgent)
                                        ?: continue
                                    if (attempt { verifyRecovered(replacement) } == true) {
                                        return@withTimeoutOrNull replacement
                                    }
                                }
                                null
                            }
                        }
                    }
                }.awaitAll()
            }
            recovered += results.filterNotNull()
        }
        return (direct + recovered).distinctBy { it.resourceId to it.url }
    }

    val exactRows = attempt { parseResourcePage(fetchExactResources()).items }.orEmpty()
    val exact = resolveRows(exactRows)
    if (exact.isNotEmpty()) return exact

    // HTTP 200 with empty, malformed or wrong-episode rows also falls back.
    val legacy = resolveRows(attempt { fetchLegacyResources() }.orEmpty())
    if (legacy.isNotEmpty()) return legacy
    firstFailure.get()?.let { throw it }
    return emptyList()
}

private fun recoveredStreamSource(
    original: JSONObject,
    candidate: JSONObject,
    languageHint: String?,
    userAgent: String,
): StreamSource? {
    val source = parseStreamSources(listOf(candidate), null).singleOrNull() ?: return null
    if (!DashResolver.isMediaUrl(source.url)) return null
    val metadata = parseStreamSources(listOf(original), languageHint).singleOrNull() ?: return null
    return source.copy(
        resolution = source.resolution.takeIf { it > 0 } ?: metadata.resolution,
        codec = source.codec ?: metadata.codec,
        audio = source.audio ?: metadata.audio,
        sizeBytes = source.sizeBytes ?: metadata.sizeBytes,
        filename = source.filename ?: metadata.filename,
        durationSeconds = source.durationSeconds ?: metadata.durationSeconds,
        uploader = source.uploader ?: metadata.uploader,
        // Never forward the API bearer token or DASH cookie/referer to MP4 CDNs.
        headers = mapOf("User-Agent" to userAgent),
    )
}

private fun JSONObject.recoveryId(): String? = recoveryString("resourceId", "id")
private fun JSONObject.recoveryUrl(): String? = recoveryString("resourceLink", "url", "link")

private fun JSONObject.recoveryString(vararg names: String): String? = names.firstNotNullOfOrNull { name ->
    opt(name)?.takeUnless { it == JSONObject.NULL }?.toString()?.takeIf { it.isNotBlank() }
}

private fun JSONObject.matchesRecoveryTarget(subjectId: String, season: Int, episode: Int): Boolean {
    val declaredSubject = recoveryString("subjectId")
    if (declaredSubject != null && declaredSubject != subjectId) return false
    val declaredSeason = recoveryString("se", "season", "seasonNumber")
    val declaredEpisode = recoveryString("ep", "episode", "episodeNumber")
    return if (season > 0) {
        declaredSeason?.toIntOrNull() == season && declaredEpisode?.toIntOrNull() == episode
    } else {
        (declaredSeason == null || declaredSeason.toIntOrNull() == 0) &&
            (declaredEpisode == null || declaredEpisode.toIntOrNull() == 0)
    }
}
