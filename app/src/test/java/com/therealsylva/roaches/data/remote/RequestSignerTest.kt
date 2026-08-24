package com.therealsylva.roaches.data.remote

import com.google.common.truth.Truth.assertThat
import okhttp3.HttpUrl.Companion.toHttpUrl
import okio.Buffer
import org.json.JSONObject
import org.junit.Test

class RequestSignerTest {
    private val identity = ClientIdentity(
        installId = "12345678-1234-1234-1234-123456789012",
        sessionId = "87654321-4321-4321-4321-210987654321",
        forwardedIp = "103.241.80.40",
    )

    @Test
    fun usesLiveValidatedTransportIdentity() {
        val signer = RequestSigner(identity)
        val info = JSONObject(signer.clientInfo)

        assertThat(signer.userAgent).isEqualTo(
            "com.community.oneroom/50020046 (Linux; U; Android 13; en_US; " +
                "23078RKD5C; Build/TQ2A.230405.003; Cronet/135.0.7012.3)",
        )
        assertThat(info.getString("brand")).isEqualTo("Redmi")
        assertThat(info.getString("model")).isEqualTo("23078RKD5C")
        assertThat(info.getString("region")).isEqualTo("US")
        assertThat(info.getString("timezone")).isEqualTo("America/New_York")
    }

    @Test
    fun signsPostWithStableForwardedAddress() {
        val headers = RequestSigner(identity).headers(
            method = "POST",
            url = "https://api6.aoneroom.com/wefeed-mobile-bff/subject-api/search/v2".toHttpUrl(),
            body = "{\"keyword\":\"Dune\"}",
            bearerToken = null,
            timestampMs = 1_787_500_000_000,
        )

        assertThat(headers["X-Forwarded-For"]).isEqualTo("103.241.80.40")
        assertThat(headers["X-Tr-Signature"]).startsWith("1787500000000|2|")
    }

    @Test
    fun postBodyPreservesTheContentTypeUsedByTheSignature() {
        val json = "{\"keyword\":\"Dune\"}"
        val body = MovieBoxApi.jsonRequestBody(json)
        val signedContentType = RequestSigner(identity).headers(
            method = "POST",
            url = "https://api6.aoneroom.com/wefeed-mobile-bff/subject-api/search/v2".toHttpUrl(),
            body = json,
            bearerToken = null,
        )["Content-Type"]
        val buffer = Buffer()

        body.writeTo(buffer)

        assertThat(body.contentType().toString()).isEqualTo(signedContentType)
        assertThat(buffer.readUtf8()).isEqualTo(json)
    }

    @Test
    fun hostFallbacksMatchTheUpstreamProvider() {
        assertThat(MovieBoxApi.HOSTS).containsExactly(
            "https://api6.aoneroom.com",
            "https://api5.aoneroom.com",
            "https://api4.aoneroom.com",
            "https://api4sg.aoneroom.com",
            "https://api3.aoneroom.com",
            "https://api6sg.aoneroom.com",
            "https://api.inmoviebox.com",
        ).inOrder()
    }
}
