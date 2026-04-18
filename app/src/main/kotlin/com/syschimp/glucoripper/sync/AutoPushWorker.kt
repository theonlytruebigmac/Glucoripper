package com.syschimp.glucoripper.sync

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

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
                Log.i(TAG, "Auto-push: attempted=${report.attempted} written=${report.written}")
                Result.success()
            },
            onFailure = { t ->
                Log.w(TAG, "Auto-push failed", t)
                Result.retry()
            },
        )
    }

    companion object {
        private const val TAG = "AutoPushWorker"
    }
}
