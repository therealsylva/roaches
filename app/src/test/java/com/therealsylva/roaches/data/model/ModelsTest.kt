package com.therealsylva.roaches.data.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ModelsTest {
    @Test
    fun watchProgressIsBounded() {
        val media = MediaItem("one", "One", MediaKind.Movie)
        assertThat(WatchEntry(media, 25, 100, 0).progress).isWithin(0.001f).of(0.25f)
        assertThat(WatchEntry(media, 125, 100, 0).progress).isEqualTo(1f)
        assertThat(WatchEntry(media, 25, 0, 0).progress).isEqualTo(0f)
    }

    @Test
    fun sourceLabelContainsOnlyReportedValues() {
        val source = StreamSource("id", "https://example.invalid/video", 1080, "H.264", "English")
        assertThat(source.technicalLabel).isEqualTo("1080p · H.264 · English")
    }
}
