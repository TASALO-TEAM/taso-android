package com.tasalo.android.work

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.tasalo.android.container
import com.tasalo.android.widget.WidgetUpdater
import java.util.concurrent.TimeUnit

/** Refresca el caché y actualiza los widgets. Es el único camino de red en segundo plano. */
class RefreshWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val result = applicationContext.container.repository.refreshAll()
        // Se actualizan siempre: aunque falle la red, los widgets deben poder atenuarse por datos viejos.
        WidgetUpdater.updateAll(applicationContext)
        return if (result.anyOk || runAttemptCount >= 2) Result.success() else Result.retry()
    }
}

object RefreshScheduler {
    private const val PERIODIC = "tasalo_refresh_periodic"
    private const val NOW = "tasalo_refresh_now"

    private val needsNetwork: Constraints
        get() = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

    /** Cada 15 min (el mínimo de WorkManager). KEEP => se agenda una sola vez. */
    fun schedulePeriodic(context: Context) {
        val request = PeriodicWorkRequestBuilder<RefreshWorker>(15, TimeUnit.MINUTES)
            .setConstraints(needsNetwork)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.MINUTES)
            .build()
        WorkManager.getInstance(context)
            .enqueueUniquePeriodicWork(PERIODIC, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    fun refreshNow(context: Context) {
        val request = OneTimeWorkRequestBuilder<RefreshWorker>()
            .setConstraints(needsNetwork)
            .build()
        WorkManager.getInstance(context)
            .enqueueUniqueWork(NOW, ExistingWorkPolicy.KEEP, request)
    }
}
