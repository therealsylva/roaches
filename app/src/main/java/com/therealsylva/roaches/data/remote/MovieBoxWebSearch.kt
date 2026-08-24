package com.therealsylva.roaches.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

private const val WEB_SEARCH_URL = "https://movieboxhd.net/web/searchResult"
private const val MAX_WEB_SEARCH_BYTES = 2_000_000L

internal class MovieBoxWebSearch(client: OkHttpClient) {
    private val webClient = client.newBuilder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .callTimeout(25, TimeUnit.SECONDS)
        .build()

    suspend fun search(query: String): JSONArray = withContext(Dispatchers.IO) {
        val url = WEB_SEARCH_URL.toHttpUrl().newBuilder()
            .addQueryParameter("keyword", query)
            .build()
        val request = Request.Builder()
            .url(url)
            .header(
                "User-Agent",
                "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 " +
                    "(KHTML, like Gecko) Chrome/135.0 Mobile Safari/537.36",
            )
            .header("Accept", "text/html,application/xhtml+xml")
            .header("Accept-Language", "en-US,en;q=0.9")
            .get()
            .build()

        webClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("Website search returned HTTP ${response.code}")
            }
            val body = response.body ?: throw IOException("Website search returned no body")
            val contentLength = body.contentLength()
            if (contentLength > MAX_WEB_SEARCH_BYTES) {
                throw IOException("Website search response was unexpectedly large")
            }
            val html = body.string()
            if (html.toByteArray().size > MAX_WEB_SEARCH_BYTES) {
                throw IOException("Website search response was unexpectedly large")
            }
            parseWebSearchHtml(html)
        }
    }
}

private val NUXT_DATA_SCRIPT = Regex(
    pattern = """<script[^>]*\bid\s*=\s*[\"']__NUXT_DATA__[\"'][^>]*>(.*?)</script>""",
    options = setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
)

internal fun parseWebSearchHtml(html: String): JSONArray {
    val encoded = NUXT_DATA_SCRIPT.find(html)?.groupValues?.getOrNull(1)?.trim()
        ?.takeIf(String::isNotBlank)
        ?: return JSONArray()
    val values = runCatching { JSONArray(encoded) }.getOrElse { return JSONArray() }
    val subjects = linkedMapOf<String, JSONObject>()

    repeat(values.length()) { index ->
        val source = values.optJSONObject(index) ?: return@repeat
        if (!source.has("subjectId") || !source.has("title") || !source.has("subjectType")) {
            return@repeat
        }
        val id = values.resolvedString(source.opt("subjectId")) ?: return@repeat
        val title = values.resolvedString(source.opt("title")) ?: return@repeat
        val subjectType = values.resolvedInt(source.opt("subjectType")) ?: return@repeat
        if (subjectType !in 0..2) return@repeat

        val item = JSONObject()
            .put("subjectId", id)
            .put("subjectType", subjectType)
            .put("title", title)
        values.copyResolvedString(source, item, "releaseDate")
        values.copyResolvedString(source, item, "genre")
        values.copyResolvedString(source, item, "countryName")
        values.copyResolvedString(source, item, "language")
        values.copyResolvedString(source, item, "description")
        values.copyResolvedString(source, item, "imdbRatingValue")
        values.resolvedImage(source, "cover", "poster", "image")?.let { poster ->
            item.put("poster", poster)
        }
        subjects.putIfAbsent(id, item)
    }

    return JSONArray(subjects.values)
}

private fun JSONArray.copyResolvedString(source: JSONObject, target: JSONObject, key: String) {
    resolvedString(source.opt(key))?.let { value -> target.put(key, value) }
}

private fun JSONArray.resolvedImage(source: JSONObject, vararg keys: String): String? =
    keys.firstNotNullOfOrNull { key ->
        when (val value = resolve(source.opt(key))) {
            is String -> value.trim().takeIf(String::isNotBlank)
            is JSONObject -> listOf("url", "src", "image").firstNotNullOfOrNull { imageKey ->
                resolvedString(value.opt(imageKey))
            }
            else -> null
        }
    }

private fun JSONArray.resolvedString(value: Any?): String? = resolve(value)
    ?.toString()
    ?.trim()
    ?.takeIf(String::isNotBlank)

private fun JSONArray.resolvedInt(value: Any?): Int? = when (val resolved = resolve(value)) {
    is Number -> resolved.toInt()
    is String -> resolved.toIntOrNull()
    else -> null
}

private fun JSONArray.resolve(value: Any?): Any? {
    if (value == null || value == JSONObject.NULL) return null
    if (value !is Number) return value
    val reference = value.toInt()
    if (reference < 0 || reference >= length()) return null
    return opt(reference).takeUnless { it == JSONObject.NULL }
}
