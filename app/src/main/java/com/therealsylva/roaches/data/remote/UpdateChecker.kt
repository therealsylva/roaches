package com.therealsylva.roaches.data.remote

import com.therealsylva.roaches.BuildConfig
import com.therealsylva.roaches.data.model.ReleaseUpdate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

class UpdateChecker(
    private val client: OkHttpClient = OkHttpClient(),
) {
    suspend fun check(): ReleaseUpdate = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(LATEST_RELEASE_URL)
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", "Roaches/${BuildConfig.VERSION_NAME}")
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                if (response.code == 404) error("No Roaches release has been published yet.")
                error("Update check failed with status ${response.code}.")
            }
            val json = JSONObject(response.body?.string().orEmpty())
            val version = json.optString("tag_name").trim().removePrefix("v")
            if (version.isBlank()) error("The latest release has no version number.")
            val assets = json.optJSONArray("assets")
            val apkUrl = buildList {
                if (assets != null) repeat(assets.length()) { index ->
                    val asset = assets.optJSONObject(index) ?: return@repeat
                    val name = asset.optString("name")
                    val url = asset.optString("browser_download_url")
                    if (name.endsWith(".apk", ignoreCase = true) && url.isNotBlank()) add(url)
                }
            }.firstOrNull()
            val releaseUrl = json.optString("html_url").takeIf(String::isNotBlank) ?: RELEASES_URL
            ReleaseUpdate(
                versionName = version,
                releaseUrl = releaseUrl,
                apkUrl = apkUrl,
                available = isNewer(version, BuildConfig.VERSION_NAME.removeSuffix("-dev")),
            )
        }
    }

    private fun isNewer(remote: String, current: String): Boolean {
        val remoteParts = remote.split('.', '-', '_').map { it.filter(Char::isDigit).toIntOrNull() ?: 0 }
        val currentParts = current.split('.', '-', '_').map { it.filter(Char::isDigit).toIntOrNull() ?: 0 }
        repeat(maxOf(remoteParts.size, currentParts.size)) { index ->
            val left = remoteParts.getOrElse(index) { 0 }
            val right = currentParts.getOrElse(index) { 0 }
            if (left != right) return left > right
        }
        return false
    }

    companion object {
        const val REPOSITORY_URL = "https://github.com/therealsylva/roaches"
        const val RELEASES_URL = "$REPOSITORY_URL/releases"
        private const val LATEST_RELEASE_URL = "https://api.github.com/repos/therealsylva/roaches/releases/latest"
    }
}
