package com.therealsylva.roaches.data.remote

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import com.therealsylva.roaches.BuildConfig
import com.therealsylva.roaches.data.model.ReleaseUpdate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest

data class UpdateInstallProgress(val message: String, val percent: Int? = null)

class AppUpdateInstaller(context: Context) {
    private val applicationContext = context.applicationContext
    private val downloadManager = applicationContext.getSystemService(DownloadManager::class.java)

    suspend fun downloadAndRequestInstall(
        release: ReleaseUpdate,
        onProgress: (UpdateInstallProgress) -> Unit,
    ) = withContext(Dispatchers.IO) {
        val apkUrl = release.apkUrl ?: error("This release has no installable APK.")
        val expectedSha256 = release.sha256 ?: error("This release has no verification checksum.")
        val directory = applicationContext.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            ?: error("Update storage is unavailable.")
        val target = File(directory, "Roaches-${release.versionName}.apk")
        if (target.exists() && !target.delete()) error("The previous update file could not be replaced.")

        val request = DownloadManager.Request(Uri.parse(apkUrl))
            .setTitle("Roaches ${release.versionName}")
            .setDescription("Downloading verified app update")
            .setMimeType(APK_MIME)
            .setAllowedOverRoaming(false)
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalFilesDir(
                applicationContext,
                Environment.DIRECTORY_DOWNLOADS,
                target.name,
            )

        val downloadId = downloadManager.enqueue(request)
        try {
            awaitDownload(downloadId, onProgress)
            onProgress(UpdateInstallProgress("Verifying update"))
            val actualSha256 = target.sha256()
            if (!actualSha256.equals(expectedSha256, ignoreCase = true)) {
                error("The update checksum did not match the published release.")
            }
            validateArchive(target)
            val uri = downloadManager.getUriForDownloadedFile(downloadId)
                ?: error("Android could not open the downloaded update.")
            onProgress(UpdateInstallProgress("Opening Android installer"))
            requestInstall(uri)
        } catch (cancelled: CancellationException) {
            downloadManager.remove(downloadId)
            throw cancelled
        }
    }

    private suspend fun awaitDownload(
        downloadId: Long,
        onProgress: (UpdateInstallProgress) -> Unit,
    ) {
        while (currentCoroutineContext().isActive) {
            downloadManager.query(DownloadManager.Query().setFilterById(downloadId)).use { cursor ->
                if (!cursor.moveToFirst()) error("The update download was removed.")
                val status = cursor.int(DownloadManager.COLUMN_STATUS)
                val downloaded = cursor.long(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
                val total = cursor.long(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)
                val percent = if (total > 0L) ((downloaded * 100L) / total).toInt().coerceIn(0, 100) else null
                when (status) {
                    DownloadManager.STATUS_SUCCESSFUL -> return
                    DownloadManager.STATUS_FAILED -> {
                        val reason = cursor.int(DownloadManager.COLUMN_REASON)
                        error("Android download failed with reason $reason.")
                    }
                    else -> onProgress(UpdateInstallProgress("Downloading update", percent))
                }
            }
            delay(650)
        }
    }

    private fun validateArchive(file: File) {
        val packageManager = applicationContext.packageManager
        val archive = packageManager.archiveInfo(file)
            ?: error("The downloaded file is not a valid Android package.")
        val stablePackage = BuildConfig.APPLICATION_ID.removeSuffix(".debug")
        if (archive.packageName != stablePackage) error("The update belongs to a different application.")
        if (archive.longVersion() <= BuildConfig.VERSION_CODE.toLong()) {
            error("The downloaded build is not newer than this installation.")
        }
        if (!BuildConfig.DEBUG) {
            val installed = packageManager.installedInfo(BuildConfig.APPLICATION_ID)
            val installedSigners = installed.signerDigests()
            val archiveSigners = archive.signerDigests()
            if (installedSigners.isEmpty() || installedSigners != archiveSigners) {
                error("The update signature does not match this installation.")
            }
        }
    }

    private fun requestInstall(uri: Uri) {
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, APK_MIME)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        if (intent.resolveActivity(applicationContext.packageManager) == null) {
            error("No Android package installer is available.")
        }
        applicationContext.startActivity(intent)
    }

    private fun android.database.Cursor.int(column: String): Int = getInt(getColumnIndexOrThrow(column))
    private fun android.database.Cursor.long(column: String): Long = getLong(getColumnIndexOrThrow(column))

    companion object {
        private const val APK_MIME = "application/vnd.android.package-archive"
    }
}

private fun File.sha256(): String = inputStream().buffered().use { input ->
    val digest = MessageDigest.getInstance("SHA-256")
    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
    while (true) {
        val count = input.read(buffer)
        if (count < 0) break
        if (count > 0) digest.update(buffer, 0, count)
    }
    digest.digest().joinToString("") { byte -> "%02x".format(byte) }
}

@Suppress("DEPRECATION")
private fun PackageManager.archiveInfo(file: File): PackageInfo? = if (Build.VERSION.SDK_INT >= 33) {
    getPackageArchiveInfo(
        file.absolutePath,
        PackageManager.PackageInfoFlags.of(PackageManager.GET_SIGNING_CERTIFICATES.toLong()),
    )
} else {
    getPackageArchiveInfo(
        file.absolutePath,
        if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES,
    )
}

@Suppress("DEPRECATION")
private fun PackageManager.installedInfo(packageName: String): PackageInfo = if (Build.VERSION.SDK_INT >= 33) {
    getPackageInfo(
        packageName,
        PackageManager.PackageInfoFlags.of(PackageManager.GET_SIGNING_CERTIFICATES.toLong()),
    )
} else {
    getPackageInfo(
        packageName,
        if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES,
    )
}

@Suppress("DEPRECATION")
private fun PackageInfo.longVersion(): Long = if (Build.VERSION.SDK_INT >= 28) longVersionCode else versionCode.toLong()

@Suppress("DEPRECATION")
private fun PackageInfo.signerDigests(): Set<String> {
    val signatures = if (Build.VERSION.SDK_INT >= 28) {
        signingInfo?.apkContentsSigners.orEmpty()
    } else {
        this.signatures.orEmpty()
    }
    return signatures.mapTo(linkedSetOf()) { signature ->
        MessageDigest.getInstance("SHA-256")
            .digest(signature.toByteArray())
            .joinToString("") { byte -> "%02x".format(byte) }
    }
}
