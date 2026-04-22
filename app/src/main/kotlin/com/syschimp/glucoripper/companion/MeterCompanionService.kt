package com.syschimp.glucoripper.companion

import android.companion.AssociationInfo
import android.companion.CompanionDeviceService
import com.syschimp.glucoripper.sync.SyncForegroundService
import timber.log.Timber

/**
 * Invoked by the OS when our bonded meter is detected nearby. We hand off to a
 * short-lived foreground service that runs one sync and stops.
 */
class MeterCompanionService : CompanionDeviceService() {

    override fun onDeviceAppeared(associationInfo: AssociationInfo) {
        Timber.i("Meter appeared: id=${associationInfo.id} addr=${associationInfo.deviceMacAddress}")
        val address = associationInfo.deviceMacAddress?.toString() ?: return
        SyncForegroundService.trigger(this, address)
    }

    @Deprecated("Legacy callback used pre-Android 14")
    override fun onDeviceAppeared(address: String) {
        Timber.i("Meter appeared (legacy): $address")
        SyncForegroundService.trigger(this, address)
    }

    override fun onDeviceDisappeared(associationInfo: AssociationInfo) {
        Timber.i("Meter disappeared: id=${associationInfo.id}")
    }

    @Deprecated("Legacy callback used pre-Android 14")
    override fun onDeviceDisappeared(address: String) {
        Timber.i("Meter disappeared (legacy): $address")
    }
}
