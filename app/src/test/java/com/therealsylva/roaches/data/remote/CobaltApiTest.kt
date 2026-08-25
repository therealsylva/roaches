package com.therealsylva.roaches.data.remote

import com.google.common.truth.Truth.assertThat
import com.therealsylva.roaches.data.model.CobaltPrepareResult
import com.therealsylva.roaches.data.model.CobaltSaveRequest
import com.therealsylva.roaches.data.model.DownloadMediaType
import com.therealsylva.roaches.data.model.LinkAudioBitrate
import com.therealsylva.roaches.data.model.LinkDownloadMode
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONObject
import org.junit.Test

class CobaltApiTest {
    @Test
    fun publicInstanceRequestsAChallengeBeforePreparing() = withServer { server ->
        server.enqueue(jsonResponse(instanceInfo(siteKey = "site-key")))
        val api = CobaltApi(baseUrl = server.url("/"))

        val failure = runCatching {
            runBlocking { api.prepare(CobaltSaveRequest("https://media.example/post")) }
        }.exceptionOrNull()

        assertThat(failure).isInstanceOf(CobaltChallengeRequired::class.java)
        assertThat((failure as CobaltChallengeRequired).siteKey).isEqualTo("site-key")
        assertThat(server.takeRequest().path).isEqualTo("/")
        assertThat(server.requestCount).isEqualTo(1)
    }

    @Test
    fun sessionAndPrepareUseTheStandardCobaltContract() = withServer { server ->
        server.enqueue(jsonResponse(instanceInfo(siteKey = "site-key")))
        server.enqueue(jsonResponse("""{"token":"jwt-token","exp":120}"""))
        server.enqueue(
            jsonResponse(
                """{"status":"tunnel","url":"https://files.example/video.mp4","filename":"video.mp4"}""",
            ),
        )
        val api = CobaltApi(baseUrl = server.url("/"))

        runBlocking {
            api.instanceInfo()
            api.createSession("turnstile-token")
            api.prepare(
                CobaltSaveRequest(
                    sourceUrl = "https://media.example/post",
                    mode = LinkDownloadMode.Audio,
                    audioBitrate = LinkAudioBitrate.High,
                ),
            )
        }

        server.takeRequest()
        val session = server.takeRequest()
        assertThat(session.path).isEqualTo("/session")
        assertThat(session.getHeader("cf-turnstile-response")).isEqualTo("turnstile-token")
        val prepare = server.takeRequest()
        assertThat(prepare.path).isEqualTo("/")
        assertThat(prepare.getHeader("Accept")).isEqualTo("application/json")
        assertThat(prepare.getHeader("Content-Type")).isEqualTo("application/json")
        assertThat(prepare.getHeader("Authorization")).isEqualTo("Bearer jwt-token")
        assertThat(prepare.getHeader("User-Agent")).doesNotContain("Roaches")
        val body = JSONObject(prepare.body.readUtf8())
        assertThat(body.getString("url")).isEqualTo("https://media.example/post")
        assertThat(body.getString("downloadMode")).isEqualTo("audio")
        assertThat(body.getString("audioFormat")).isEqualTo("mp3")
        assertThat(body.getString("audioBitrate")).isEqualTo("320")
        assertThat(body.getString("localProcessing")).isEqualTo("disabled")
        assertThat(body.has("videoQuality")).isFalse()
    }

    @Test
    fun pickerResponseKeepsImagesVideoAndAccompanyingAudio() = withServer { server ->
        server.enqueue(jsonResponse(instanceInfo()))
        server.enqueue(
            jsonResponse(
                """
                {
                  "status":"picker",
                  "picker":[
                    {"type":"photo","url":"https://files.example/one.jpg"},
                    {"type":"video","url":"https://files.example/two.mp4","thumb":"https://files.example/two.jpg"}
                  ],
                  "audio":"https://files.example/audio.mp3",
                  "audioFilename":"sound.mp3"
                }
                """.trimIndent(),
            ),
        )
        val result = runBlocking {
            CobaltApi(baseUrl = server.url("/"))
                .prepare(CobaltSaveRequest("https://media.example/post"))
        } as CobaltPrepareResult.Picker

        assertThat(result.items.map { it.mediaType })
            .containsExactly(DownloadMediaType.Image, DownloadMediaType.Video)
            .inOrder()
        assertThat(result.audio?.mediaType).isEqualTo(DownloadMediaType.Audio)
        assertThat(result.audio?.filename).isEqualTo("sound.mp3")
    }

    private fun instanceInfo(siteKey: String? = null): String = JSONObject()
        .put(
            "cobalt",
            JSONObject()
                .put("version", "11.7.1")
                .put("turnstileSitekey", siteKey)
                .put("services", listOf("reddit", "tiktok")),
        )
        .toString()

    private fun jsonResponse(body: String) = MockResponse()
        .setResponseCode(200)
        .setHeader("Content-Type", "application/json")
        .setBody(body)

    private fun withServer(block: (MockWebServer) -> Unit) {
        MockWebServer().use(block)
    }
}
