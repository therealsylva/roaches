package com.therealsylva.roaches.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/** A bounded range read, never a download of the full recovered file. */
internal class RecoveredStreamProbe(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(3, TimeUnit.SECONDS)
        .readTimeout(3, TimeUnit.SECONDS)
        .callTimeout(5, TimeUnit.SECONDS)
        .build(),
) {
    suspend fun isMp4(url: String, userAgent: String): Boolean = withContext(Dispatchers.IO) {
        currentCoroutineContext().ensureActive()
        if (!DashResolver.isMediaUrl(url)) return@withContext false
        val request = Request.Builder().url(url)
            .header("User-Agent", userAgent)
            .header("Range", "bytes=0-63")
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful || !DashResolver.isMediaUrl(response.request.url.toString())) return@use false
            if (response.code == 206 &&
                response.header("Content-Range")?.startsWith("bytes 0-") != true
            ) return@use false
            val source = response.body?.source() ?: return@use false
            if (!source.request(12)) return@use false
            val prefix = source.buffer.clone().readByteArray(12)
            currentCoroutineContext().ensureActive()
            prefix.copyOfRange(4, 8).contentEquals(byteArrayOf(0x66, 0x74, 0x79, 0x70))
        }
    }
}
