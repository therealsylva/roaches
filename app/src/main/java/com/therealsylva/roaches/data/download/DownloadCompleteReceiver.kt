package com.therealsylva.roaches.data.download

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.therealsylva.roaches.data.local.LocalStore

class DownloadCompleteReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != DownloadManager.ACTION_DOWNLOAD_COMPLETE) return
        val downloadId = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L)
        if (downloadId <= 0L || !LocalStore(context).ownsSeasonDownload(downloadId)) return
        SeasonDownloadCoordinator.schedule(context)
    }
}
