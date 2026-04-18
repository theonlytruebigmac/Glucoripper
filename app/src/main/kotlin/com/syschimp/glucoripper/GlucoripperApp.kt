package com.syschimp.glucoripper

import android.app.Application
import com.syschimp.glucoripper.sync.SyncForegroundService

class GlucoripperApp : Application() {
    override fun onCreate() {
        super.onCreate()
        SyncForegroundService.ensureChannel(this)
    }
}
