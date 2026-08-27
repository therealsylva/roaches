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

    @Test
    fun sourceRetainsUsefulProviderMetadata() {
        val source = StreamSource(
            resourceId = "id",
            url = "https://example.invalid/video",
            filename = "Film.1080p.mkv",
            durationSeconds = 7_200,
            uploader = "Cinema source",
        )

        assertThat(source.filename).isEqualTo("Film.1080p.mkv")
        assertThat(source.durationSeconds).isEqualTo(7_200L)
        assertThat(source.uploader).isEqualTo("Cinema source")
    }

    @Test
    fun settingsDefaultToGlobalEnglishWithoutTracking() {
        val settings = AppSettings()
        assertThat(settings.contentRegion).isEqualTo(ContentRegion.GlobalEnglish)
        assertThat(settings.wifiOnlyDownloads).isFalse()
        assertThat(settings.eggsEnabled).isFalse()
        assertThat(settings.matureContentUnlocked).isFalse()
    }

    @Test
    fun onlyCompletedLocalVideosCanBeShared() {
        val media = MediaItem("film", "Film", MediaKind.Movie)
        val source = StreamSource("source", "https://example.invalid/video")
        val completeVideo = DownloadEntry(
            downloadId = 1,
            media = media,
            source = source,
            createdAt = 0,
            state = DownloadState.Complete,
            localUri = "content://downloads/1",
        )

        assertThat(completeVideo.canShareVideo).isTrue()
        assertThat(completeVideo.canRenameFile).isTrue()
        assertThat(completeVideo.visibleName).isEqualTo("Film")
        assertThat(completeVideo.copy(displayName = "Final cut").visibleName).isEqualTo("Final cut")
        assertThat(completeVideo.copy(state = DownloadState.Downloading).canShareVideo).isFalse()
        assertThat(completeVideo.copy(state = DownloadState.Downloading).canRenameFile).isFalse()
        assertThat(completeVideo.copy(localUri = null).canShareVideo).isFalse()
        assertThat(completeVideo.copy(localUri = null).canRenameFile).isFalse()
        assertThat(completeVideo.copy(mediaType = DownloadMediaType.Audio).canShareVideo).isFalse()
        assertThat(completeVideo.copy(mediaType = DownloadMediaType.Audio).canRenameFile).isTrue()
    }
}
