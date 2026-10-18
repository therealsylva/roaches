package com.therealsylva.roaches.data.remote

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Buffer
import org.junit.Test

class RecoveredStreamProbeTest {
    private val header = byteArrayOf(0, 0, 0, 24, 0x66, 0x74, 0x79, 0x70, 0x69, 0x73, 0x6f, 0x6d)

    @Test
    fun checksMediaBytesUsingOnlyUserAgentAndBoundedRange() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setResponseCode(206).setHeader("Content-Range", "bytes 0-63/1000")
                .setBody(Buffer().write(header)))
            val result = RecoveredStreamProbe().isMp4(server.url("/movie.mp4?sign=a").toString(), "TestAgent")
            assertThat(result).isTrue()
            val request = server.takeRequest()
            assertThat(request.getHeader("Range")).isEqualTo("bytes=0-63")
            assertThat(request.getHeader("User-Agent")).isEqualTo("TestAgent")
            for (name in listOf("Cookie", "Referer", "Authorization")) assertThat(request.getHeader(name)).isNull()
        }
    }

    @Test
    fun rejectsHtmlTruncatedBodiesHttpErrorsAndWrongRanges() = runBlocking {
        MockWebServer().use { server ->
            for (response in listOf(
                MockResponse().setBody("<html>upgrade</html>"),
                MockResponse().setBody("ftyp"),
                MockResponse().setResponseCode(429),
                MockResponse().setResponseCode(206).setHeader("Content-Range", "bytes 64-127/1000")
                    .setBody(Buffer().write(header)),
            )) {
                server.enqueue(response)
                assertThat(RecoveredStreamProbe().isMp4(server.url("/movie.mp4").toString(), "TestAgent")).isFalse()
            }
        }
    }
}
