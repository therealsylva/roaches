package com.therealsylva.roaches.data.download

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.therealsylva.roaches.data.local.LocalStore
import com.therealsylva.roaches.data.model.DownloadPreference
import com.therealsylva.roaches.data.model.StreamSource
import com.therealsylva.roaches.data.repository.RoachesRepository
import kotlinx.coroutines.CancellationException
import java.io.IOException
import java.util.Locale
import java.util.concurrent.TimeUnit

object SeasonDownloadCoordinator {
    private const val UNIQUE_WORK = "roaches-season-download-coordinator"

    fun schedule(context: Context) {
        val appContext = context.applicationContext
        val wifiOnly = LocalStore(appContext).settings().wifiOnlyDownloads
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(if (wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED)
            .build()
        val request = OneTimeWorkRequestBuilder<SeasonDownloadWorker>()
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(appContext).enqueueUniqueWork(
            UNIQUE_WORK,
            ExistingWorkPolicy.KEEP,
            request,
        )
    }
}

class SeasonDownloadWorker(
    appContext: Context,
    parameters: WorkerParameters,
) : CoroutineWorker(appContext, parameters) {
    override suspend fun doWork(): Result {
        val store = LocalStore(applicationContext)
        if (store.hasActiveSeasonDownload()) return Result.success()
        val repository = RoachesRepository(store)

        while (true) {
            val task = store.nextSeasonDownloadTask() ?: return Result.success()
            try {
                val sources = repository.sources(
                    subjectId = task.media.id,
                    season = task.season,
                    episode = task.episode,
                    languageHint = null,
                )
                val source = selectSeasonDownloadSource(sources, task.preference)
                    ?: throw IOException("No source was available for episode ${task.episode}")
                if (store.startSeasonDownload(task, source) != null) return Result.success()
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (failure: Throwable) {
                val terminal = store.recordSeasonTaskFailure(
                    task.id,
                    failure.message ?: "Source could not be prepared",
                )
                if (!terminal) return Result.retry()
            }
        }
    }
}

internal fun selectSeasonDownloadSource(
    sources: List<StreamSource>,
    preference: DownloadPreference,
): StreamSource? = sources.minWithOrNull(
    compareBy<StreamSource> { source -> audioPenalty(source.audio, preference.audio) }
        .thenBy { source -> resolutionPenalty(source.resolution, preference.resolution) }
        .thenByDescending(StreamSource::resolution)
        .thenBy { it.sizeBytes ?: Long.MAX_VALUE },
)

private fun audioPenalty(actual: String?, preferred: String?): Int {
    val wanted = preferred.normalizedAudio()
    if (wanted.isBlank()) return 0
    val available = actual.normalizedAudio()
    if (available.isBlank()) return 1
    return if (available == wanted || available.contains(wanted) || wanted.contains(available)) 0 else 2
}

private fun resolutionPenalty(actual: Int, preferred: Int): Int = when {
    preferred <= 0 -> -actual
    actual == preferred -> 0
    actual in 1 until preferred -> preferred - actual
    actual > preferred -> 10_000 + actual - preferred
    else -> 20_000
}

private fun String?.normalizedAudio(): String = orEmpty()
    .lowercase(Locale.US)
    .replace(Regex("[^a-z0-9]+"), " ")
    .trim()
