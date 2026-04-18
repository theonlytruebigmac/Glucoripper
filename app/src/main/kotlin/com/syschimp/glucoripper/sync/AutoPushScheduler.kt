package com.syschimp.glucoripper.sync

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.syschimp.glucoripper.data.AutoPushMode
import java.util.concurrent.TimeUnit

/**
 * Applies a user-chosen [AutoPushMode] to WorkManager.
 *
 * Call [apply] whenever the preference changes and once on app start, so the
 * schedule stays in sync across process deaths.
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
        val request = PeriodicWorkRequestBuilder<AutoPushWorker>(hours.toLong(), TimeUnit.HOURS)
            .setInitialDelay(hours.toLong(), TimeUnit.HOURS)
            .build()
        wm.enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request,
        )
    }
}
