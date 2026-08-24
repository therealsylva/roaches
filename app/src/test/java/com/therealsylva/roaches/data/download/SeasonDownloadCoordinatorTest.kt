package com.therealsylva.roaches.data.download

import com.google.common.truth.Truth.assertThat
import com.therealsylva.roaches.data.model.DownloadPreference
import com.therealsylva.roaches.data.model.StreamSource
import org.junit.Test

class SeasonDownloadCoordinatorTest {
    @Test
    fun sourceSelectionPrefersMatchingAudioAndExactResolution() {
        val sources = listOf(
            source("english-1080", 1080, "English"),
            source("french-720", 720, "French"),
            source("english-720", 720, "English"),
        )

        val selected = selectSeasonDownloadSource(sources, DownloadPreference(720, "English"))

        assertThat(selected?.resourceId).isEqualTo("english-720")
    }

    @Test
    fun sourceSelectionPrefersLowerResolutionBeforeExceedingLimit() {
        val sources = listOf(
            source("high", 1080, "English"),
            source("lower", 480, "English"),
        )

        val selected = selectSeasonDownloadSource(sources, DownloadPreference(720, "English"))

        assertThat(selected?.resourceId).isEqualTo("lower")
    }

    @Test
    fun unknownAudioIsSaferThanAReportedMismatch() {
        val sources = listOf(
            source("mismatch", 720, "French"),
            source("unknown", 720, null),
        )

        val selected = selectSeasonDownloadSource(sources, DownloadPreference(720, "English"))

        assertThat(selected?.resourceId).isEqualTo("unknown")
    }

    private fun source(id: String, resolution: Int, audio: String?) = StreamSource(
        resourceId = id,
        url = "https://video.test/$id",
        resolution = resolution,
        audio = audio,
    )
}
