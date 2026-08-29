package com.therealsylva.roaches.data.remote

import okhttp3.HttpUrl
import org.json.JSONObject
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.Base64
import java.util.Locale
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

internal data class ClientIdentity(
    val installId: String,
    val sessionId: String,
    val forwardedIp: String,
)

internal class RequestSigner(private val identity: ClientIdentity) {
    companion object {
        private const val SECRET = "76iRl07s0xSN9jqmEWAt79EBJZulIQIsV64FZr2O"
        private const val BODY_HASH_LIMIT = 102_400
        private const val VERSION_CODE = 50_020_121
        private const val VERSION_NAME = "4.0.01.0813.02"
        private const val ACCEPT = "application/json"
        private const val OS_VERSION = "13"
        private const val BRAND = "Redmi"
        private const val MODEL = "23078RKD5C"
        private const val USER_AGENT =
            "com.community.oneroom/50020121 (Linux; U; Android 13; en_US; " +
                "23078RKD5C; Build/TQ2A.230405.003; Cronet/135.0.7012.3)"
    }

    val userAgent: String = USER_AGENT

    internal val clientInfo = JSONObject()
        .put("package_name", "com.community.oneroom")
        .put("version_name", VERSION_NAME)
        .put("version_code", VERSION_CODE)
        .put("os", "android")
        .put("os_version", OS_VERSION)
        .put("install_ch", "ps")
        .put("device_id", identity.installId.replace("-", ""))
        .put("install_store", "ps")
        .put("gaid", identity.sessionId)
        .put("brand", BRAND)
        .put("model", MODEL)
        .put("system_language", "en")
        .put("net", "NETWORK_WIFI")
        .put("region", "US")
        .put("timezone", "America/New_York")
        .put("sp_code", "40401")
        .put("X-Play-Mode", "2")
        .toString()

    fun headers(
        method: String,
        url: HttpUrl,
        body: String?,
        bearerToken: String?,
        timestampMs: Long = System.currentTimeMillis(),
    ): Map<String, String> {
        val reversedTimestamp = timestampMs.toString().reversed()
        val clientToken = "$timestampMs,${md5Hex(reversedTimestamp.toByteArray())}"
        val canonical = canonical(method, url, body, timestampMs)
        val key = Base64.getDecoder().decode(padBase64(SECRET))
        val mac = Mac.getInstance("HmacMD5").apply {
            init(SecretKeySpec(key, "HmacMD5"))
        }
        val signature = Base64.getEncoder().encodeToString(
            mac.doFinal(canonical.toByteArray(StandardCharsets.UTF_8)),
        )

        return buildMap {
            put("User-Agent", userAgent)
            put("Accept", ACCEPT)
            put("Content-Type", ACCEPT)
            put("X-Client-Token", clientToken)
            put("X-Tr-Signature", "$timestampMs|2|$signature")
            put("X-Client-Info", clientInfo)
            put("X-Client-Status", "0")
            put("X-Forwarded-For", identity.forwardedIp)
            bearerToken?.takeIf(String::isNotBlank)?.let { put("Authorization", "Bearer $it") }
        }
    }

    internal fun canonical(
        method: String,
        url: HttpUrl,
        body: String?,
        timestampMs: Long,
    ): String {
        val sortedQuery = url.queryParameterNames.sorted().flatMap { key ->
            url.queryParameterValues(key).map { value -> "$key=$value" }
        }.joinToString("&")
        val pathAndQuery = if (sortedQuery.isBlank()) url.encodedPath else "${url.encodedPath}?$sortedQuery"
        val bodyBytes = body?.toByteArray(StandardCharsets.UTF_8)
        val bodyHash = bodyBytes?.let { md5Hex(it.copyOfRange(0, minOf(it.size, BODY_HASH_LIMIT))) }.orEmpty()
        val bodyLength = bodyBytes?.size?.toString().orEmpty()
        return listOf(
            method.uppercase(Locale.US),
            ACCEPT,
            ACCEPT,
            bodyLength,
            timestampMs.toString(),
            bodyHash,
            pathAndQuery,
        ).joinToString("\n")
    }

    private fun md5Hex(bytes: ByteArray): String = MessageDigest
        .getInstance("MD5")
        .digest(bytes)
        .joinToString("") { "%02x".format(it) }

    private fun padBase64(value: String): String = value + "=".repeat((4 - value.length % 4) % 4)
}
