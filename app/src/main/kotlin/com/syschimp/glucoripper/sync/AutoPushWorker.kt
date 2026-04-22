package com.syschimp.glucoripper.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import timber.log.Timber

/**
 * Periodic WorkManager job that pushes whatever is currently in staging to
 * Health Connect. Scheduled by [AutoPushScheduler] based on the user's
 * [com.syschimp.glucoripper.data.AutoPushMode] preference.
 */
class AutoPushWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val pusher = StagingPusher(applicationContext)
        return pusher.push().fold(
            onSuccess = { report ->
                Timber.i("Auto-push: attempted=%d written=%d", report.attempted, report.written)
                Result.success()
            },
            onFailure = { t ->
                Timber.w(t, "Auto-push failed")
                Result.retry()
            },
        )
    }
}
