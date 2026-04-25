package com.syschimp.glucoripper.sync

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.syschimp.glucoripper.data.AutoPushMode
import java.time.Duration
import java.util.concurrent.TimeUnit

/**
 * Applies a user-chosen [AutoPushMode] to WorkManager.
 *
 * [apply] is the cold-start path: idempotent via `KEEP`, so an existing schedule
 * is left alone instead of having its initial delay reset every time the app
 * starts (the previous `UPDATE` policy was indefinitely deferring the next run
 * for users who opened the app often).
 *
 * [replace] is the user-changed-the-mode path: cancels the existing schedule
 * and enqueues a fresh one with the new period.
 */
object AutoPushScheduler {
    private const val WORK_NAME = "glucoripper.auto_push"

    fun apply(context: Context, mode: AutoPushMode) {
        val wm = WorkManager.getInstance(context)
        val hours = mode.periodicHours
        if (hours == null) {
            // OFF and AFTER_SYNC are both non-periodic from WorkManager's POV.
            wm.cancelUniqueWork(WORK_NAME)
            return
        }
        wm.enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            buildRequest(hours),
        )
    }

    fun replace(context: Context, mode: AutoPushMode) {
        val wm = WorkManager.getInstance(context)
        val hours = mode.periodicHours
        if (hours == null) {
            wm.cancelUniqueWork(WORK_NAME)
            return
        }
        wm.enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            buildRequest(hours),
        )
    }

    private fun buildRequest(hours: Int) =
        PeriodicWorkRequestBuilder<AutoPushWorker>(hours.toLong(), TimeUnit.HOURS)
            .setInitialDelay(hours.toLong(), TimeUnit.HOURS)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, Duration.ofMinutes(15))
            .build()
}
