package com.therealsylva.roaches.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import javax.net.ssl.SSLException

internal class MovieBoxApi(
    identity: ClientIdentity,
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(3, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .callTimeout(12, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build(),
) {
    companion object {
        internal val HOSTS = listOf(
            "https://api6.aoneroom.com",
            "https://api5.aoneroom.com",
            "https://api4.aoneroom.com",
            "https://api4sg.aoneroom.com",
            "https://api3.aoneroom.com",
            "https://api6sg.aoneroom.com",
            "https://api.inmoviebox.com",
        )
        private val AUTH_FAILURES = setOf(401, 441)
        private val RETRYABLE = setOf(401, 403, 406, 407, 429, 441, 500, 502, 503, 504)
        private val JSON = "application/json".toMediaType()
        private const val SESSION_PATH = "/wefeed-mobile-bff/tab-operating?page=1&tabId=0&version="

        internal fun jsonRequestBody(body: String) =
            body.toByteArray(StandardCharsets.UTF_8).toRequestBody(JSON)

        internal fun episodeResourcePath(
            subjectId: String,
            season: Int,
            episode: Int,
            page: Int = 1,
        ): String {
            require(season > 0 && episode > 0)
            return "/wefeed-mobile-bff/subject-api/resource?subjectId=${encodeValue(subjectId)}" +
                "&se=$season&ep=$episode&page=$page&perPage=20"
        }

        private fun encodeValue(value: String): String =
            URLEncoder.encode(value, StandardCharsets.UTF_8.name())
    }

    private val signer = RequestSigner(identity)
    private val webSearch = MovieBoxWebSearch(client)
    private val token = AtomicReference<String?>(null)
    private val activeHost = AtomicInteger(0)
    private val sessionMutex = Mutex()

    suspend fun initialize() {
        ensureSession()
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

    suspend fun websiteSearch(query: String): JSONArray = webSearch.search(query)

    suspend fun catalogue(
        genre: String,
        country: String,
        page: Int = 1,
        sort: String = "Hottest",
    ): Any {
        val body = JSONObject()
            .put("tabId", 2)
            .put("page", page)
            .put("perPage", 20)
            .put("classify", "All")
            .put("country", country)
            .put("genre", genre)
            .put("sort", sort)
            .put("year", "All")
            .toString()
        return request("POST", "/wefeed-mobile-bff/subject-api/list", body)
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

    suspend fun resourcePage(
        subjectId: String,
        resolution: Int = 0,
        page: Int = 1,
    ): Any {
        val resolutionQuery = if (resolution > 0) "&resolution=$resolution" else ""
        return request(
            "GET",
            "/wefeed-mobile-bff/subject-api/resource?subjectId=${encode(subjectId)}" +
                "&page=$page&perPage=20$resolutionQuery",
        )
    }

    suspend fun episodeResourcePage(
        subjectId: String,
        season: Int,
        episode: Int,
        page: Int = 1,
    ): Any = request("GET", episodeResourcePath(subjectId, season, episode, page))

    suspend fun captions(subjectId: String, resourceId: String): Any = request(
        "GET",
        "/wefeed-mobile-bff/subject-api/get-ext-captions?subjectId=${encode(subjectId)}" +
            "&resourceId=${encode(resourceId)}",
    )

    private suspend fun request(method: String, path: String, body: String? = null): Any {
        if (!requiresSession(path)) return requestAcrossHosts(method, path, body)

        ensureSession()
        return try {
            requestAcrossHosts(method, path, body)
        } catch (failure: ProviderUnavailableException) {
            if (failure.statuses.none(AUTH_FAILURES::contains)) throw failure
            token.set(null)
            ensureSession()
            requestAcrossHosts(method, path, body)
        }
    }

    private suspend fun ensureSession() {
        if (token.get() != null) return
        sessionMutex.withLock {
            if (token.get() != null) return
            requestAcrossHosts("GET", SESSION_PATH, null, requireSessionToken = true)
            if (token.get().isNullOrBlank()) {
                throw ProviderUnavailableException(
                    statuses = emptySet(),
                    failureKinds = setOf(FailureKind.Session),
                    cause = null,
                )
            }
        }
    }

    private fun requiresSession(path: String): Boolean =
        !path.contains("tab-operating") && !path.contains("subject-api/list")

    private suspend fun requestAcrossHosts(
        method: String,
        path: String,
        body: String?,
        requireSessionToken: Boolean = false,
    ): Any =
        withContext(Dispatchers.IO) {
            val start = activeHost.get()
            var lastFailure: Throwable? = null
            val statuses = linkedSetOf<Int>()
            val failureKinds = linkedSetOf<FailureKind>()
            var nextDelayMs = 0L
            repeat(HOSTS.size) { offset ->
                currentCoroutineContext().ensureActive()
                if (nextDelayMs > 0) delay(nextDelayMs)
                nextDelayMs = (50L shl offset.coerceAtMost(3)).coerceAtMost(400L)
                val index = (start + offset) % HOSTS.size
                val url = (HOSTS[index] + path).toHttpUrlOrThrow()
                val headers = signer.headers(method, url, body, token.get())
                val request = Request.Builder().url(url).apply {
                    headers.forEach { (name, value) -> header(name, value) }
                    if (method == "POST") post(jsonRequestBody(body.orEmpty())) else get()
                }.build()

                try {
                    client.newCall(request).execute().use { response ->
                        absorbToken(response.header("x-user"))
                        if (requireSessionToken && token.get().isNullOrBlank() && response.isSuccessful) {
                            failureKinds += FailureKind.Session
                            lastFailure = IOException("Provider response did not establish a session")
                            return@use
                        }
                        if (response.code in RETRYABLE) {
                            statuses += response.code
                            failureKinds += when (response.code) {
                                429 -> FailureKind.RateLimited
                                in AUTH_FAILURES -> FailureKind.Session
                                403, 406, 407 -> FailureKind.Access
                                else -> FailureKind.Server
                            }
                            if (response.code == 429) {
                                nextDelayMs = response.retryAfterMillis() ?: 800L
                            }
                            lastFailure = IOException("Provider host returned ${response.code}")
                            return@use
                        }
                        if (!response.isSuccessful) {
                            statuses += response.code
                            failureKinds += FailureKind.Access
                            lastFailure = IOException("Provider request failed with ${response.code}")
                            return@use
                        }
                        val raw = response.body?.string().orEmpty()
                        val parsed = parseJson(raw)
                        activeHost.set(index)
                        return@withContext unwrapData(parsed)
                    }
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (failure: UnknownHostException) {
                    failureKinds += FailureKind.Dns
                    lastFailure = failure
                } catch (failure: SSLException) {
                    failureKinds += FailureKind.Tls
                    lastFailure = failure
                } catch (failure: SocketTimeoutException) {
                    failureKinds += FailureKind.Timeout
                    lastFailure = failure
                } catch (failure: Throwable) {
                    failureKinds += FailureKind.Network
                    lastFailure = failure
                }
            }
            throw ProviderUnavailableException(statuses, failureKinds, lastFailure)
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

    private fun encode(value: String): String = encodeValue(value)
}

private enum class FailureKind { Access, Dns, Network, RateLimited, Server, Session, Timeout, Tls }

private class ProviderUnavailableException(
    val statuses: Set<Int>,
    failureKinds: Set<FailureKind>,
    cause: Throwable?,
) : IOException(providerFailureMessage(statuses, failureKinds), cause)

private fun providerFailureMessage(statuses: Set<Int>, kinds: Set<FailureKind>): String = when {
    FailureKind.RateLimited in kinds || 429 in statuses -> "Provider is busy. Try again in a moment."
    FailureKind.Session in kinds -> "Provider session could not be established."
    FailureKind.Dns in kinds -> "Provider addresses could not be resolved. Check your connection or Private DNS."
    FailureKind.Tls in kinds -> "Secure connection to the provider failed."
    FailureKind.Timeout in kinds -> "Provider connection timed out. Try again."
    FailureKind.Access in kinds -> "Provider rejected this network route."
    else -> "Provider connection failed. Try again."
}

private fun okhttp3.Response.retryAfterMillis(): Long? = header("Retry-After")
    ?.trim()
    ?.toLongOrNull()
    ?.coerceIn(1L, 3L)
    ?.times(1_000L)

private fun String.toHttpUrlOrThrow() = toHttpUrl()
