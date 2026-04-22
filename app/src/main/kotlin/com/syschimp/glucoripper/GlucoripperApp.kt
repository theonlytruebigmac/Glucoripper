package com.syschimp.glucoripper

import android.app.Application
import com.syschimp.glucoripper.data.Preferences
import com.syschimp.glucoripper.sync.AutoPushScheduler
import com.syschimp.glucoripper.sync.SyncForegroundService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import timber.log.Timber

class GlucoripperApp : Application() {
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) Timber.plant(Timber.DebugTree())
        SyncForegroundService.ensureChannel(this)
        // Re-apply the auto-push schedule on cold start so WorkManager stays in
        // sync if the process was killed and the user's mode preference changed.
        appScope.launch {
            val mode = Preferences(this@GlucoripperApp).flow.first().autoPushMode
            AutoPushScheduler.apply(this@GlucoripperApp, mode)
        }
    }
}
