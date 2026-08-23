package com.therealsylva.roaches.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

internal class MovieBoxApi(
    identity: ClientIdentity,
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(4, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .callTimeout(20, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build(),
) {
    companion object {
        private val HOSTS = listOf(
            "https://api6.aoneroom.com",
            "https://api5.aoneroom.com",
            "https://api4.aoneroom.com",
            "https://api4sg.aoneroom.com",
            "https://api3.aoneroom.com",
            "https://api6sg.aoneroom.com",
            "https://api.inmoviebox.com",
        )
        private val RETRYABLE = setOf(403, 406, 407, 429, 500, 502, 503, 504)
        private val JSON = "application/json".toMediaType()
    }

    private val signer = RequestSigner(identity)
    private val token = AtomicReference<String?>(null)
    private val activeHost = AtomicInteger(0)

    suspend fun initialize() {
        requestAcrossHosts("GET", "/wefeed-mobile-bff/tab-operating?page=1&tabId=0&version=", null)
    }

    suspend fun home(page: Int = 1): Any = request(
        "GET",
        "/wefeed-mobile-bff/tab-operating?page=$page&tabId=0&version=",
    )

    suspend fun search(query: String, page: Int = 1): Any {
        val body = JSONObject()
            .put("keyword", query)
            .put("page", page)
            .put("perPage", 20)
            .put("subjectType", "All")
            .put("tabId", "All")
            .toString()
        return request("POST", "/wefeed-mobile-bff/subject-api/search/v2", body)
    }

    suspend fun details(subjectId: String): JSONObject {
        val encoded = encode(subjectId)
        val payload = request("GET", "/wefeed-mobile-bff/subject-api/get?subjectId=$encoded")
        val details = payload as? JSONObject ?: JSONObject()
        val type = details.optInt("subjectType", details.optInt("stype", 1))
        if (type == 2) {
            runCatching {
                details.put(
                    "seasons",
                    request("GET", "/wefeed-mobile-bff/subject-api/season-info?subjectId=$encoded"),
                )
            }
        }
        return details
    }

    suspend fun resources(
        subjectId: String,
        season: Int = 0,
        episode: Int = 0,
        page: Int = 1,
    ): Any {
        val episodeQuery = if (season > 0 || episode > 0) "&se=$season&ep=$episode" else ""
        return request(
            "GET",
            "/wefeed-mobile-bff/subject-api/resource?subjectId=${encode(subjectId)}" +
                "$episodeQuery&page=$page&perPage=50",
        )
    }

    suspend fun captions(subjectId: String, resourceId: String): Any = request(
        "GET",
        "/wefeed-mobile-bff/subject-api/get-ext-captions?subjectId=${encode(subjectId)}" +
            "&resourceId=${encode(resourceId)}",
    )

    private suspend fun request(method: String, path: String, body: String? = null): Any {
        if (token.get() == null && !path.contains("tab-operating")) {
            runCatching { initialize() }
        }
        return requestAcrossHosts(method, path, body)
    }

    private suspend fun requestAcrossHosts(method: String, path: String, body: String?): Any =
        withContext(Dispatchers.IO) {
            val start = activeHost.get()
            var lastFailure: Throwable? = null
            repeat(HOSTS.size) { offset ->
                if (offset > 0) delay(80)
                val index = (start + offset) % HOSTS.size
                val url = (HOSTS[index] + path).toHttpUrlOrThrow()
                val headers = signer.headers(method, url, body, token.get())
                val request = Request.Builder().url(url).apply {
                    headers.forEach { (name, value) -> header(name, value) }
                    if (method == "POST") post(body.orEmpty().toRequestBody(JSON)) else get()
                }.build()

                try {
                    client.newCall(request).execute().use { response ->
                        absorbToken(response.header("x-user"))
                        if (response.code in RETRYABLE) {
                            lastFailure = IOException("Provider host returned ${response.code}")
                            return@use
                        }
                        if (!response.isSuccessful) {
                            lastFailure = IOException("Provider request failed with ${response.code}")
                            return@use
                        }
                        val raw = response.body?.string().orEmpty()
                        val parsed = parseJson(raw)
                        activeHost.set(index)
                        return@withContext unwrapData(parsed)
                    }
                } catch (failure: Throwable) {
                    lastFailure = failure
                }
            }
            throw IOException("The catalogue is temporarily unreachable", lastFailure)
        }

    private fun absorbToken(rawHeader: String?) {
        if (rawHeader.isNullOrBlank()) return
        runCatching { JSONObject(rawHeader).optString("token") }
            .getOrNull()
            ?.takeIf(String::isNotBlank)
            ?.let(token::set)
    }

    private fun parseJson(raw: String): Any {
        val trimmed = raw.trim()
        return when {
            trimmed.startsWith("{") -> JSONObject(trimmed)
            trimmed.startsWith("[") -> JSONArray(trimmed)
            else -> throw IOException("Provider returned an invalid response")
        }
    }

    private fun unwrapData(parsed: Any): Any =
        (parsed as? JSONObject)?.opt("data")?.takeUnless { it == JSONObject.NULL } ?: parsed

    private fun encode(value: String): String =
        URLEncoder.encode(value, StandardCharsets.UTF_8.name())
}

private fun String.toHttpUrlOrThrow() = toHttpUrl()
