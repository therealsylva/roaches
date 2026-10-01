package com.therealsylva.roaches.data.remote

import org.json.JSONObject
import java.nio.charset.StandardCharsets
import java.util.Base64

object DashResolver {
    private val NOTICE_MARKERS = listOf(
        "macdn.aoneroom.com/other/",
        "/notice.mp4",
        "1c7de0bd",
        "9a0461bc",
        "b164fbfb",
    )

    fun isNoticeUrl(url: String?): Boolean {
        if (url.isNullOrBlank()) return true
        val lower = url.lowercase()
        return NOTICE_MARKERS.any(lower::contains)
    }

    fun resolveDashManifestFromPolicy(signCookie: String): String? {
        for (part in signCookie.split(';')) {
            val trimmed = part.trim()
            if (trimmed.startsWith("Edge-Cache-Cookie=")) {
                val prefix = trimmed.substringAfter("urlprefix=", "").substringBefore(':')
                val resource = decodeBase64(prefix.replace('-', '+').replace('_', '/'))
                resource?.let(::manifestUrl)?.let { return it }
            }
            if (!trimmed.startsWith("CloudFront-Policy=")) continue

            val policy = trimmed
                .removePrefix("CloudFront-Policy=")
                .replace('-', '+')
                .replace('_', '=')
                .replace('~', '/')
            val decoded = decodeBase64(policy) ?: continue
            val json = try {
                JSONObject(decoded)
            } catch (_: Exception) {
                continue
            }
            val resource = json.optJSONArray("Statement")
                ?.optJSONObject(0)
                ?.optString("Resource")
                ?: continue
            manifestUrl(resource)?.let { return it }
        }
        return null
    }

    private fun decodeBase64(value: String): String? = runCatching {
        String(Base64.getDecoder().decode(value), StandardCharsets.UTF_8)
    }.getOrNull()

    private fun manifestUrl(resource: String): String? {
        val base = resource.trimEnd('*', '/')
        return if (base.startsWith("http://") || base.startsWith("https://")) {
            "$base/index.mpd"
        } else {
            null
        }
    }
}
