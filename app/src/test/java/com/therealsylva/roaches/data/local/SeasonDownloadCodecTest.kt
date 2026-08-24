package com.therealsylva.roaches.data.local

import com.google.common.truth.Truth.assertThat
import com.therealsylva.roaches.data.model.DownloadEntry
import com.therealsylva.roaches.data.model.DownloadPreference
import com.therealsylva.roaches.data.model.Episode
import com.therealsylva.roaches.data.model.MediaItem
import com.therealsylva.roaches.data.model.MediaKind
import com.therealsylva.roaches.data.model.SeasonDownloadTask
import com.therealsylva.roaches.data.model.StreamSource
import org.json.JSONArray
import org.junit.Test

class SeasonDownloadCodecTest {
    private val media = MediaItem("series", "Series", MediaKind.Series, posterUrl = "https://image.test/poster")
    private val source = StreamSource("resource", "https://video.test/episode", 720, audio = "English")

    @Test
    fun seasonTaskRoundTripsRetryAndSelectionMetadata() {
        val task = SeasonDownloadTask(
            id = "task",
            batchId = "batch",
            media = media,
            season = 2,
            episode = 4,
            episodeTitle = "The fourth episode",
            preference = DownloadPreference(720, "English"),
            batchSize = 8,
            createdAt = 42L,
            attempts = 2,
            lastError = "Provider busy",
        )

        assertThat(decodeSeasonDownloadTasks(encodeSeasonDownloadTasks(listOf(task))))
            .containsExactly(task)
    }

    @Test
    fun downloadRoundTripsEpisodeIdentityAndReadsLegacyRows() {
        val entry = DownloadEntry(
            downloadId = 9L,
            media = media,
            source = source,
            createdAt = 42L,
            season = 2,
            episode = 4,
            episodeTitle = "Episode 4",
            batchId = "batch",
            batchSize = 8,
        )
        val decoded = decodeDownloadEntries(encodeDownloadEntries(listOf(entry))).single()

        assertThat(decoded).isEqualTo(entry)

        val legacy = JSONArray(encodeDownloadEntries(listOf(entry)))
        legacy.getJSONObject(0).remove("season")
        legacy.getJSONObject(0).remove("episode")
        legacy.getJSONObject(0).remove("episodeTitle")
        legacy.getJSONObject(0).remove("batch")
        legacy.getJSONObject(0).remove("batchSize")
        val oldEntry = decodeDownloadEntries(legacy.toString()).single()

        assertThat(oldEntry.season).isEqualTo(0)
        assertThat(oldEntry.episode).isEqualTo(0)
        assertThat(oldEntry.batchId).isNull()
    }

    @Test
    fun malformedQueueRowsAreIgnored() {
        assertThat(decodeSeasonDownloadTasks("not-json")).isEmpty()
        assertThat(decodeSeasonDownloadTasks("[{}]")).isEmpty()
    }

    @Test
    fun duplicateEpisodesAreSkippedByTheirStableTargetKey() {
        val episodes = listOf(Episode(2, 1), Episode(2, 2), Episode(2, 3))

        val missing = missingSeasonEpisodes("series", 2, episodes, setOf("series:2:2"))

        assertThat(missing.map(Episode::number)).containsExactly(1, 3).inOrder()
    }

    @Test
    fun episodeFilenamesCannotCollideWithinASeason() {
        val first = downloadFileName("Series", 2, 1, source)
        val second = downloadFileName("Series", 2, 2, source)

        assertThat(first).isEqualTo("Series-S02E01-720p.mp4")
        assertThat(second).isEqualTo("Series-S02E02-720p.mp4")
        assertThat(first).isNotEqualTo(second)
    }
}
