package com.therealsylva.roaches.data.repository

import com.google.common.truth.Truth.assertThat
import com.therealsylva.roaches.data.model.StreamSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Test
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger

class MovieBoxStreamRecoveryTest {
    private val notice = "https://macdn.aoneroom.com/other/2026/10/10/upgrade.mp4"
    private fun row(id: String = "r1", url: String = notice, season: Int = 2, episode: Int = 3) =
        JSONObject().put("resourceId", id).put("resourceLink", url).put("se", season).put("ep", episode)
    private fun listing(vararg rows: JSONObject): JSONObject = JSONObject().put("list", JSONArray(rows.toList()))

    private suspend fun resolve(
        exact: Any = listing(row()),
        legacy: suspend () -> List<JSONObject> = { emptyList() },
        recover: suspend (String, String) -> Any = { id, _ -> listing(row(id, "https://cdn.example.com/$id.mp4")) },
        verify: suspend (StreamSource) -> Boolean = { true },
        budget: Int = 6,
        timeout: Long = 20_000,
    ): List<StreamSource> = resolveMovieBoxSources(
        subjectId = "show", season = 2, episode = 3, languageHint = "English", userAgent = "TestAgent",
        fetchPlayInfo = { JSONObject() }, fetchExactResources = { exact }, fetchLegacyResources = legacy,
        recoverResource = recover, verifyRecovered = verify, maxRecoveries = budget, recoveryTimeoutMs = timeout,
    )

    @Test
    fun arrayAndNestedEnvelopesPreserveRowsAndPager() {
        val original = listing(row()).put("pager", JSONObject().put("hasMore", true))
        for (payload in listOf(original, JSONObject().put("data", original),
            JSONObject().put("data", JSONObject().put("data", original)))) {
            val page = parseResourcePage(payload)
            assertThat(page.items.single().getString("resourceId")).isEqualTo("r1")
            assertThat(page.hasMore).isTrue()
        }
        assertThat(parseResourcePage(JSONArray().put(row())).items).hasSize(1)
        assertThat(parseResourcePage(JSONObject().put("data", JSONArray().put(row()))).items).hasSize(1)
        assertThat(parseResourcePage(JSONObject().put("data", JSONObject.NULL)).items).isEmpty()
    }

    @Test
    fun recoversWrappedResponseWithoutLeakingDashHeadersAndPreservesMetadata() = runBlocking {
        val original = row().put("resolution", 1080).put("language", "Spanish")
            .put("fileName", "Episode.HEVC.mp4").put("size", "1048576")
        val sources = resolve(
            exact = JSONObject().put("data", JSONArray().put(original)),
            recover = { id, failedUrl ->
                assertThat(id).isEqualTo("r1")
                assertThat(failedUrl).isEqualTo(notice)
                JSONObject().put("data", listing(row(url = "https://cdn.example.com/video.mp4")))
            },
        )
        val recovered = sources.single()
        assertThat(recovered.resolution).isEqualTo(1080)
        assertThat(recovered.audio).isEqualTo("Spanish")
        assertThat(recovered.sizeBytes).isEqualTo(1048576L)
        assertThat(recovered.headers).containsExactly("User-Agent", "TestAgent")
        Unit
    }

    @Test
    fun rejectsWrongResourceSubjectSeasonEpisodeMissingTagsAndUnsafeUrlsBeforeProbing() = runBlocking {
        val candidates = listOf(
            row("other", "https://cdn.example.com/a.mp4"),
            row(url = "https://cdn.example.com/a.mp4", season = 1),
            row(url = "https://cdn.example.com/a.mp4", episode = 1),
            row(url = "https://cdn.example.com/a.mp4").put("subjectId", "other-show"),
            JSONObject().put("resourceId", "r1").put("resourceLink", "https://cdn.example.com/a.mp4"),
            row(url = "file:///tmp/video.mp4"), row(url = "javascript:alert(1)"),
            row(url = "https://user:password@cdn.example.com/a.mp4"), row(),
        )
        var probes = 0
        val sources = resolve(recover = { _, _ -> listing(*candidates.toTypedArray()) }, verify = { probes++; true })
        assertThat(sources).isEmpty()
        assertThat(probes).isEqualTo(0)
        Unit
    }

    @Test
    fun wrongEpisodeListingNeverTriggersRecovery() = runBlocking {
        var calls = 0
        val sources = resolve(exact = listing(row(episode = 1)), recover = { _, _ -> calls++; JSONObject() })
        assertThat(calls).isEqualTo(0)
        assertThat(sources).isEmpty()
        Unit
    }

    @Test
    fun emptyOrMalformedSuccessfulListingFallsBackToLegacy() = runBlocking {
        for (payload in listOf(JSONObject(), listing(), JSONObject().put("list", "invalid"))) {
            val sources = resolve(exact = payload, legacy = { listOf(row(url = "https://cdn.example.com/legacy.mp4")) })
            assertThat(sources.single().url).isEqualTo("https://cdn.example.com/legacy.mp4")
        }
        Unit
    }

    @Test
    fun failedCandidateAndQualityDoNotHideWorkingAlternatives() = runBlocking {
        val sources = resolve(
            exact = listing(row("bad"), row("good")),
            recover = { id, _ ->
                if (id == "bad") throw IOException("unavailable")
                listing(row(id, "https://cdn.example.com/failed.mp4"), row(id, "https://cdn.example.com/good.mp4"))
            },
            verify = { if (it.url.endsWith("failed.mp4")) throw IOException("CDN failure") else true },
        )
        assertThat(sources.single().url).isEqualTo("https://cdn.example.com/good.mp4")
        Unit
    }

    @Test
    fun unverifiedMediaIsNeverReturned() = runBlocking {
        assertThat(resolve(verify = { false })).isEmpty()
        Unit
    }

    @Test
    fun boundsConcurrentAndTotalRecoveryAcrossBothListings() = runBlocking {
        val rows = (1..10).map { row("r$it") }
        val active = AtomicInteger()
        val peak = AtomicInteger()
        var calls = 0
        val sources = resolve(
            exact = JSONArray(rows), legacy = { rows }, budget = 3,
            recover = { id, _ ->
                calls++
                val current = active.incrementAndGet()
                peak.updateAndGet { maxOf(it, current) }
                delay(5)
                active.decrementAndGet()
                listing(row(id, "https://cdn.example.com/$id.mp4"))
            },
            verify = { false },
        )
        assertThat(sources).isEmpty()
        assertThat(calls).isEqualTo(3)
        assertThat(peak.get()).isAtMost(2)
        Unit
    }

    @Test
    fun timesOutRecoveryWithoutCancellingTheCaller() = runBlocking {
        val sources = resolve(timeout = 10, recover = { _, _ -> delay(100); JSONObject() })
        assertThat(sources).isEmpty()
        Unit
    }

    @Test(expected = CancellationException::class)
    fun preservesCallerCancellation() = runBlocking {
        resolve(recover = { _, _ -> throw CancellationException("cancelled") })
        Unit
    }

    @Test
    fun healthyPlayInfoDoesNotMakeRecoveryRequests() = runBlocking {
        val sources = resolveMovieBoxSources(
            "show", 2, 3, null, "TestAgent",
            fetchPlayInfo = { JSONObject().put("streams", JSONArray().put(row(url = "https://cdn.example.com/good.mp4"))) },
            fetchExactResources = { error("unnecessary request") }, fetchLegacyResources = { error("unnecessary request") },
            recoverResource = { _, _ -> error("unnecessary request") }, verifyRecovered = { error("unnecessary probe") },
        )
        assertThat(sources).hasSize(1)
        Unit
    }

    @Test
    fun movieRecoveryAcceptsAbsentEpisodeTagsAndLargeNumericResourceId() = runBlocking {
        val original = row(season = 0, episode = 0).put("resourceId", 6154402015721538488L)
        val sources = resolveMovieBoxSources(
            "movie", 0, 0, null, "TestAgent", fetchPlayInfo = { JSONObject() },
            fetchExactResources = { listing(original) }, fetchLegacyResources = { emptyList() },
            recoverResource = { id, _ ->
                JSONArray().put(JSONObject().put("resourceId", id).put("resourceLink", "https://cdn.example.com/movie.mp4"))
            }, verifyRecovered = { true },
        )
        assertThat(sources.single().resourceId).isEqualTo("6154402015721538488")
        Unit
    }
}
