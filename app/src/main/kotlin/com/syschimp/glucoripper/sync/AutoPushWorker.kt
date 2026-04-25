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
                if (runAttemptCount >= MAX_RETRIES) {
                    Timber.e(t, "Auto-push gave up after %d attempts", runAttemptCount)
                    Result.failure()
                } else {
                    Timber.w(t, "Auto-push failed (attempt %d); will retry", runAttemptCount)
                    Result.retry()
                }
            },
        )
    }

    companion object {
        // Caps the WorkManager backoff chain so a permanently-bad payload (e.g.
        // HC denied permission) doesn't retry forever and burn battery.
        const val MAX_RETRIES = 5
    }
}
