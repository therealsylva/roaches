package com.therealsylva.roaches.data.remote

import com.therealsylva.roaches.data.model.CobaltInstanceInfo
import com.therealsylva.roaches.data.model.CobaltPickerSelection
import com.therealsylva.roaches.data.model.CobaltPrepareResult
import com.therealsylva.roaches.data.model.CobaltPreparedFile
import com.therealsylva.roaches.data.model.CobaltSaveRequest
import com.therealsylva.roaches.data.model.DownloadMediaType
import com.therealsylva.roaches.data.model.LinkDownloadMode
import com.therealsylva.roaches.data.model.downloadMediaType
import com.therealsylva.roaches.data.model.downloadMimeType
import com.therealsylva.roaches.data.model.fallbackCobaltFilename
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.URI
import java.util.Locale
import java.util.concurrent.TimeUnit

class CobaltChallengeRequired(val siteKey: String) : IOException("Complete the connection check to continue.")

class CobaltApiFailure(
    val code: String,
    message: String,
) : IOException(message)

class CobaltApi(
    private val client: OkHttpClient = defaultClient(),
    private val baseUrl: HttpUrl = HttpUrl.Builder()
        .scheme("https")
        .host("api.cobalt.tools")
        .addPathSegment("")
        .build(),
    private val clockSeconds: () -> Long = { System.currentTimeMillis() / 1_000L },
) {
    private data class Session(val token: String, val expiresAt: Long)
    private data class CachedInfo(val value: CobaltInstanceInfo, val expiresAt: Long)

    @Volatile private var session: Session? = null
    @Volatile private var cachedInfo: CachedInfo? = null

    suspend fun instanceInfo(force: Boolean = false): CobaltInstanceInfo {
        val now = clockSeconds()
        cachedInfo?.takeIf { !force && it.expiresAt > now }?.let { return it.value }
        return requestInstanceInfo().also { info ->
            cachedInfo = CachedInfo(info, now + INSTANCE_CACHE_SECONDS)
            if (info.turnstileSiteKey == null) session = null
        }
    }

    suspend fun prepare(request: CobaltSaveRequest): CobaltPrepareResult {
        val info = instanceInfo()
        val authorization = activeSessionToken()
        if (info.turnstileSiteKey != null && authorization == null) {
            throw CobaltChallengeRequired(info.turnstileSiteKey)
        }
        return try {
            requestSave(request, authorization)
        } catch (failure: CobaltApiFailure) {
            if (!failure.code.isSessionFailure()) throw failure
            session = null
            val freshInfo = instanceInfo(force = true)
            freshInfo.turnstileSiteKey?.let { throw CobaltChallengeRequired(it) }
            throw failure
        }
    }

    fun clearSession() {
        session = null
    }

    fun completeBrowserChallenge(
        request: CobaltSaveRequest,
        payload: String,
    ): CobaltPrepareResult {
        if (payload.length !in 1..MAX_BROWSER_RESPONSE_LENGTH) {
            throw CobaltApiFailure("error.api.browser.invalid", "Cobalt returned an invalid browser response.")
        }
        val envelope = runCatching { JSONObject(payload) }.getOrNull()
            ?: throw CobaltApiFailure("error.api.browser.invalid", "Cobalt returned an invalid browser response.")
        val browserSession = envelope.optJSONObject("session")
            ?: throw CobaltApiFailure("error.api.session.invalid", "Cobalt did not open a valid session.")
        if (browserSession.optString("status") == "error") throw apiFailure(browserSession)
        val token = browserSession.optString("token").takeIf(String::isNotBlank)
            ?: throw CobaltApiFailure("error.api.session.invalid", "Cobalt did not open a valid session.")
        val lifetime = browserSession.optLong("exp").coerceAtLeast(1L)
        session = Session(token, clockSeconds() + lifetime)

        val response = envelope.optJSONObject("response")
            ?: throw CobaltApiFailure("error.api.response.invalid", "Cobalt returned an invalid media response.")
        return parsePrepareResponse(response, request)
    }

    private suspend fun requestInstanceInfo(): CobaltInstanceInfo {
        val request = Request.Builder().url(baseUrl).get().build()
        val root = executeJson(request)
        val cobalt = root.optJSONObject("cobalt")
            ?: throw CobaltApiFailure("error.api.info.invalid", "Cobalt returned invalid server information.")
        return CobaltInstanceInfo(
            version = cobalt.optString("version"),
            turnstileSiteKey = cobalt.nullableString("turnstileSitekey"),
            services = cobalt.optJSONArray("services").strings(),
        )
    }

    private suspend fun requestSave(
        request: CobaltSaveRequest,
        authorization: String?,
    ): CobaltPrepareResult {
        val body = cobaltRequestJson(request)
            .toByteArray(Charsets.UTF_8)
            .toRequestBody(JSON_MEDIA_TYPE)
        val builder = Request.Builder()
            .url(baseUrl)
            .header("Accept", "application/json")
            .post(body)
        authorization?.let { builder.header("Authorization", "Bearer $it") }
        val root = executeJson(builder.build())
        return parsePrepareResponse(root, request)
    }

    private fun parsePrepareResponse(
        root: JSONObject,
        request: CobaltSaveRequest,
    ): CobaltPrepareResult = when (root.optString("status")) {
        "tunnel", "redirect" -> CobaltPrepareResult.File(root.preparedFile(request))
        "picker" -> root.preparedPicker(request)
        "local-processing" -> throw CobaltApiFailure(
            "error.api.local_processing",
            "This file needs processing that Roaches does not perform on-device.",
        )
        "error" -> throw apiFailure(root)
        else -> throw CobaltApiFailure("error.api.response.invalid", "Cobalt returned an unknown response.")
    }

    private fun JSONObject.preparedFile(request: CobaltSaveRequest): CobaltPreparedFile {
        val url = requiredRemoteUrl("url")
        val fallbackType = if (request.mode == LinkDownloadMode.Audio) {
            DownloadMediaType.Audio
        } else {
            DownloadMediaType.Video
        }
        val rawFilename = nullableString("filename")
        val mediaType = downloadMediaType(rawFilename, fallbackType)
        val filename = rawFilename ?: fallbackCobaltFilename(request.sourceUrl, mediaType)
        return CobaltPreparedFile(
            url = url,
            filename = filename,
            mediaType = mediaType,
            mimeType = downloadMimeType(filename, mediaType),
        )
    }

    private fun JSONObject.preparedPicker(request: CobaltSaveRequest): CobaltPrepareResult.Picker {
        val values = optJSONArray("picker") ?: JSONArray()
        val items = buildList {
            repeat(values.length()) { index ->
                val value = values.optJSONObject(index) ?: return@repeat
                val mediaType = when (value.optString("type").lowercase(Locale.US)) {
                    "video" -> DownloadMediaType.Video
                    "gif" -> DownloadMediaType.Gif
                    else -> DownloadMediaType.Image
                }
                val url = value.nullableString("url")?.takeIf(::isRemoteUrl) ?: return@repeat
                val filename = value.nullableString("filename")
                    ?: fallbackCobaltFilename(request.sourceUrl, mediaType, index)
                add(
                    CobaltPreparedFile(
                        url = url,
                        filename = filename,
                        mediaType = mediaType,
                        mimeType = downloadMimeType(filename, mediaType),
                        thumbnailUrl = value.nullableString("thumb")?.takeIf(::isRemoteUrl),
                        selection = CobaltPickerSelection(index, mediaType),
                    ),
                )
            }
        }
        val audio = nullableString("audio")?.takeIf(::isRemoteUrl)?.let { url ->
            val filename = nullableString("audioFilename")
                ?: fallbackCobaltFilename(request.sourceUrl, DownloadMediaType.Audio)
            CobaltPreparedFile(
                url = url,
                filename = filename,
                mediaType = DownloadMediaType.Audio,
                mimeType = downloadMimeType(filename, DownloadMediaType.Audio),
                selection = CobaltPickerSelection(
                    mediaType = DownloadMediaType.Audio,
                    pickerAudio = true,
                ),
            )
        }
        if (items.isEmpty() && audio == null) {
            throw CobaltApiFailure("error.api.picker.empty", "Cobalt did not return any downloadable items.")
        }
        return CobaltPrepareResult.Picker(items, audio)
    }

    private suspend fun executeJson(request: Request): JSONObject = withContext(Dispatchers.IO) {
        client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            val json = runCatching { JSONObject(body) }.getOrNull()
            if (json != null && (response.isSuccessful || json.optString("status") == "error")) {
                if (json.optString("status") == "error") {
                    throw apiFailure(json, response.header("RateLimit-Reset"))
                }
                return@withContext json
            }
            throw CobaltApiFailure(
                "error.api.http.${response.code}",
                if (response.code == 429) "Cobalt is busy. Try again shortly." else "Cobalt could not be reached.",
            )
        }
    }

    private fun activeSessionToken(): String? {
        val current = session ?: return null
        if (current.expiresAt - SESSION_EXPIRY_SKEW_SECONDS <= clockSeconds()) {
            session = null
            return null
        }
        return current.token
    }

    private fun apiFailure(root: JSONObject, rateLimitReset: String? = null): CobaltApiFailure {
        val error = root.optJSONObject("error")
        val code = error?.optString("code")?.takeIf(String::isNotBlank) ?: "error.api.unknown"
        val message = when {
            code == "error.api.rate_exceeded" -> rateLimitReset
                ?.toLongOrNull()
                ?.takeIf { it > 0 }
                ?.let { "Cobalt rate limit reached. Try again in $it seconds." }
                ?: "Cobalt rate limit reached. Try again shortly."
            code.contains("link.invalid") || code.contains("link.missing") -> "This link is not supported by Cobalt."
            code.contains("content.too_long") -> "This media is longer than the Cobalt instance allows."
            code.contains("content.video.unavailable") -> "This video is unavailable from its source."
            code.contains("content.post.unavailable") -> "This post is unavailable from its source."
            code.contains("auth.turnstile") -> "The connection check expired. Try it again."
            code.contains("timed_out") -> "Cobalt took too long to respond. Try again."
            else -> "Cobalt could not prepare this link."
        }
        return CobaltApiFailure(code, message)
    }

    private fun JSONObject.requiredRemoteUrl(key: String): String = nullableString(key)
        ?.takeIf(::isRemoteUrl)
        ?: throw CobaltApiFailure("error.api.url.invalid", "Cobalt returned an invalid download link.")

    companion object {
        private val JSON_MEDIA_TYPE = "application/json".toMediaType()
        private const val INSTANCE_CACHE_SECONDS = 300L
        private const val SESSION_EXPIRY_SKEW_SECONDS = 2L
        private const val MAX_BROWSER_RESPONSE_LENGTH = 1_048_576

        private fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(25, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)
            .followRedirects(false)
            .followSslRedirects(false)
            .build()
    }
}

internal fun cobaltRequestJson(request: CobaltSaveRequest): String = JSONObject()
    .put("url", request.sourceUrl)
    .put("downloadMode", request.mode.apiValue)
    .put("filenameStyle", "basic")
    .put("localProcessing", "disabled")
    .apply {
        if (request.mode == LinkDownloadMode.Audio) {
            put("audioFormat", "mp3")
            put("audioBitrate", request.audioBitrate.apiValue)
        } else {
            put("videoQuality", request.videoQuality.apiValue)
        }
    }
    .toString()

internal const val COBALT_PUBLIC_API_ORIGIN = "https://api.cobalt.tools"

private fun String.isSessionFailure(): Boolean = this in setOf(
    "error.api.auth.jwt.missing",
    "error.api.auth.jwt.invalid",
    "error.api.http.401",
    "error.api.http.403",
)

private fun isRemoteUrl(value: String): Boolean = runCatching { URI(value) }
    .getOrNull()
    ?.let { uri -> uri.scheme?.lowercase(Locale.US) in setOf("http", "https") && !uri.host.isNullOrBlank() }
    ?: false

private fun JSONObject.nullableString(key: String): String? = opt(key)
    ?.takeUnless { it == JSONObject.NULL }
    ?.toString()
    ?.takeIf(String::isNotBlank)

private fun JSONArray?.strings(): List<String> = buildList {
    val values = this@strings ?: return@buildList
    repeat(values.length()) { index ->
        values.optString(index).takeIf(String::isNotBlank)?.let(::add)
    }
}
