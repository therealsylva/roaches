package com.therealsylva.roaches.data.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class CobaltModelsTest {
    @Test
    fun extractsAWebLinkFromSharedText() {
        assertThat(extractHttpUrl("watch this: https://example.com/post/42)."))
            .isEqualTo("https://example.com/post/42")
        assertThat(extractHttpUrl("javascript:alert(1)")).isNull()
    }

    @Test
    fun detectsMediaTypesAndMimeTypesFromFilenames() {
        assertThat(downloadMediaType("clip.webm", DownloadMediaType.Video))
            .isEqualTo(DownloadMediaType.Video)
        assertThat(downloadMediaType("sound.opus", DownloadMediaType.Video))
            .isEqualTo(DownloadMediaType.Audio)
        assertThat(downloadMediaType("frame.gif", DownloadMediaType.Video))
            .isEqualTo(DownloadMediaType.Gif)
        assertThat(downloadMimeType("sound.opus", DownloadMediaType.Audio))
            .isEqualTo("audio/opus")
    }

    @Test
    fun fallbackNamesAreStableAndSafeToDisplay() {
        assertThat(fallbackCobaltFilename("https://www.example.com/post/42", DownloadMediaType.Image, 1))
            .isEqualTo("example-2.jpg")
        assertThat(cobaltDisplayTitle("night_train.mp4", "https://example.com/post/42"))
            .isEqualTo("night train")
    }
}
