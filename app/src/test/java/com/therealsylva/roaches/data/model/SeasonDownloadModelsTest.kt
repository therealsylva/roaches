package com.therealsylva.roaches.data.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class SeasonDownloadModelsTest {
    private val media = MediaItem("series", "Series", MediaKind.Series)

    @Test
    fun episodeTargetKeySeparatesEpisodesAndMovies() {
        assertThat(downloadTargetKey(media.id, 2, 4)).isEqualTo("series:2:4")
        assertThat(downloadTargetKey(media.id, 2, 4)).isNotEqualTo(downloadTargetKey(media.id, 2, 5))
        assertThat(downloadTargetKey("movie", 0, 0)).isEqualTo("movie:0:0")
    }

    @Test
    fun seasonProgressIncludesOnlyTheActiveEpisodeFraction() {
        val progress = SeasonDownloadProgress(
            batchId = "batch",
            media = media,
            season = 2,
            totalCount = 10,
            readyCount = 3,
            failedCount = 0,
            queuedCount = 6,
            activeEpisode = 4,
            activeProgress = 0.5f,
        )

        assertThat(progress.progress).isWithin(0.001f).of(0.35f)
    }
}
