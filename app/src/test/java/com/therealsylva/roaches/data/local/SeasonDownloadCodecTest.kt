package com.therealsylva.roaches.data.local

import android.app.DownloadManager
import com.google.common.truth.Truth.assertThat
import com.therealsylva.roaches.data.model.CobaltPickerSelection
import com.therealsylva.roaches.data.model.CobaltRetry
import com.therealsylva.roaches.data.model.CobaltSaveRequest
import com.therealsylva.roaches.data.model.DownloadEntry
import com.therealsylva.roaches.data.model.DownloadMediaType
import com.therealsylva.roaches.data.model.DownloadPreference
import com.therealsylva.roaches.data.model.DownloadState
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
    fun savedLinkCodecSeparatesRetrySecretsFromBackedUpMetadata() {
        val retry = CobaltRetry(
            request = CobaltSaveRequest("https://social.example/private-post"),
            selection = CobaltPickerSelection(2, DownloadMediaType.Image),
        )
        val entry = DownloadEntry(
            downloadId = 14L,
            media = media.copy(id = "link:14", title = "Saved photo"),
            source = source.copy(url = "https://signed.example/file?token=secret"),
            createdAt = 42L,
            mediaType = DownloadMediaType.Image,
            mimeType = "image/jpeg",
            cobaltRetryId = "retry-14",
            cobaltRetry = retry,
        )

        val publicJson = encodeDownloadEntries(listOf(entry))
        val decodedEntry = decodeDownloadEntries(publicJson).single()
        val privateJson = encodeCobaltRetries(mapOf("retry-14" to retry))

        assertThat(publicJson).doesNotContain("private-post")
        assertThat(publicJson).doesNotContain("token=secret")
        assertThat(decodedEntry.source.url).isEqualTo("cobalt://prepared-link")
        assertThat(decodedEntry.mediaType).isEqualTo(DownloadMediaType.Image)
        assertThat(decodedEntry.mimeType).isEqualTo("image/jpeg")
        assertThat(decodedEntry.cobaltRetry).isNull()
        assertThat(decodeCobaltRetries(privateJson)).containsExactly("retry-14", retry)
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

    @Test
    fun filenameUsesOnlyKnownMediaExtensions() {
        val releaseTagFilename = source.copy(
            filename = "Series.Release.1080p",
            url = "https://video.test/episode.webm?token=temporary",
        )
        val unknownExtension = source.copy(
            filename = "Series.Release.final",
            url = "https://video.test/download?token=temporary",
        )

        assertThat(downloadFileName("Series", 2, 1, releaseTagFilename))
            .isEqualTo("Series-S02E01-720p.webm")
        assertThat(downloadFileName("Series", 2, 1, unknownExtension))
            .isEqualTo("Series-S02E01-720p.mp4")
    }

    @Test
    fun renameKeepsTheStoredExtensionAndAcceptsUnicodeNames() {
        assertThat(renamedDownloadFileName("Final cut.mkv", "Series-S02E01-720p.mp4"))
            .isEqualTo("Final cut.mp4")
        assertThat(renamedDownloadFileName("Épisode définitif", "Series-S02E01-720p.webm"))
            .isEqualTo("Épisode définitif.webm")
        assertThat(renamedDownloadFileName("../unsafe:name", "Series.mp4"))
            .isEqualTo("unsafe name.mp4")
    }

    @Test
    fun renamedDisplayNameRoundTrips() {
        val entry = DownloadEntry(
            downloadId = 9L,
            media = media,
            source = source.copy(filename = "Final cut.mp4"),
            createdAt = 42L,
            displayName = "Final cut",
        )

        assertThat(decodeDownloadEntries(encodeDownloadEntries(listOf(entry))).single())
            .isEqualTo(entry)
    }

    @Test
    fun completedDownloadUriFallsBackToDownloadManagerUri() {
        assertThat(
            resolvedDownloadUri(
                DownloadManager.STATUS_SUCCESSFUL,
                cursorUri = null,
                managerUri = "content://downloads/9",
            ),
        ).isEqualTo("content://downloads/9")
        assertThat(
            resolvedDownloadUri(
                DownloadManager.STATUS_SUCCESSFUL,
                cursorUri = "file:///movie.mp4",
                managerUri = "content://downloads/9",
            ),
        ).isEqualTo("content://downloads/9")
    }

    @Test
    fun onlyNonActiveDownloadsAreRequeued() {
        assertThat(shouldRequeueDownload(DownloadState.Queued)).isTrue()
        assertThat(shouldRequeueDownload(DownloadState.Failed)).isTrue()
        assertThat(shouldRequeueDownload(DownloadState.Missing)).isTrue()
        assertThat(shouldRequeueDownload(DownloadState.Downloading)).isFalse()
        assertThat(shouldRequeueDownload(DownloadState.Complete)).isFalse()
    }

    @Test
    fun seasonRetrySignalUsesNativeAndTerminalTaskStates() {
        assertThat(
            seasonRetryAvailable(
                states = listOf(DownloadState.Complete, DownloadState.Queued),
                taskAttempts = emptyList(),
                maxAttempts = 3,
            ),
        ).isTrue()
        assertThat(
            seasonRetryAvailable(
                states = listOf(DownloadState.Complete, DownloadState.Downloading),
                taskAttempts = listOf(0, 2),
                maxAttempts = 3,
            ),
        ).isFalse()
        assertThat(
            seasonRetryAvailable(
                states = listOf(DownloadState.Complete),
                taskAttempts = listOf(3),
                maxAttempts = 3,
            ),
        ).isTrue()
    }
}
