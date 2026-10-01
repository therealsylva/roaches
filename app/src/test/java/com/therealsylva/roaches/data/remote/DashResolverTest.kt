package com.therealsylva.roaches.data.remote

import com.google.common.truth.Truth.assertThat
import com.therealsylva.roaches.data.repository.parsePlayInfoSources
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Test
import java.util.Base64

class DashResolverTest {
    private val prefix = "https://cdn.example.com/dash/item1/"
    private val notice = "https://macdn.aoneroom.com/other/2026/09/04/b164fbfb4347792950bdfbfb563d39d9.mp4"

    private fun edgeCookie(resource: String = prefix, padded: Boolean = false): String {
        val encoder = if (padded) Base64.getUrlEncoder() else Base64.getUrlEncoder().withoutPadding()
        val encoded = encoder.encodeToString(resource.toByteArray(Charsets.UTF_8))
        return "Edge-Cache-Cookie=urlprefix=$encoded:sign=test:t=1893456000"
    }

    @Test
    fun resolvesPaddedAndUnpaddedEdgeCookies() {
        for (padded in listOf(true, false)) {
            assertThat(DashResolver.resolveDashManifestFromPolicy("other=1; ${edgeCookie(padded = padded)};"))
                .isEqualTo("${prefix}index.mpd")
        }
    }

    @Test
    fun resolvesUpstreamEdgeCookieFixture() {
        val cookie = "Edge-Cache-Cookie=urlprefix=aHR0cHM6Ly9zYmNkbjMuaGFrdW5heW1hdGF0YS5jb20vZGFzaC9pdGVtMQ:sign=abc:t=123"
        assertThat(DashResolver.resolveDashManifestFromPolicy(cookie))
            .isEqualTo("https://sbcdn3.hakunaymatata.com/dash/item1/index.mpd")
    }

    @Test
    fun preservesCloudFrontPolicySupportAfterMalformedEdgeCookie() {
        val policy = JSONObject().put(
            "Statement",
            JSONArray().put(JSONObject().put("Resource", "$prefix*")),
        ).toString()
        val encoded = Base64.getEncoder().encodeToString(policy.toByteArray(Charsets.UTF_8))
            .replace('+', '-').replace('=', '_').replace('/', '~')
        assertThat(DashResolver.resolveDashManifestFromPolicy(
            "Edge-Cache-Cookie=urlprefix=%%%:sign=test; CloudFront-Policy=$encoded",
        )).isEqualTo("${prefix}index.mpd")
    }

    @Test
    fun rejectsMalformedOrNonHttpPrefixes() {
        for (cookie in listOf("", "Edge-Cache-Cookie=sign=test", edgeCookie("file:///tmp/video"), edgeCookie(""))) {
            assertThat(DashResolver.resolveDashManifestFromPolicy(cookie)).isNull()
        }
    }

    @Test
    fun playbackUsesSignedManifestInsteadOfForcedUpdateVideo() {
        val cookie = edgeCookie()
        val streams = JSONArray().put(JSONObject()
            .put("id", "resource-id")
            .put("url", notice)
            .put("signCookie", cookie)
            .put("format", "DASH"))
        val sources = parsePlayInfoSources(JSONObject().put("streams", streams), null)

        assertThat(sources).hasSize(1)
        assertThat(sources.single().url).isEqualTo("${prefix}index.mpd")
        assertThat(sources.single().headers["Cookie"]).isEqualTo(cookie)
        assertThat(sources.single().resourceId).isEqualTo("resource-id")
    }

    @Test
    fun playbackNeverFallsBackToNoticeWhenCookieIsInvalid() {
        val streams = JSONArray().put(JSONObject()
            .put("url", notice)
            .put("signCookie", "Edge-Cache-Cookie=urlprefix=%%%:sign=test"))
        assertThat(parsePlayInfoSources(JSONObject().put("streams", streams), null)).isEmpty()
    }
}
