package com.syschimp.glucoripper.companion

import android.companion.AssociationInfo
import android.companion.CompanionDeviceService
import android.os.Build
import com.syschimp.glucoripper.sync.SyncForegroundService
import timber.log.Timber

/**
 * Invoked by the OS when our bonded meter is detected nearby. We hand off to a
 * short-lived foreground service that runs one sync and stops.
 *
 * Some Android 13+ OEM builds dispatch *both* the [AssociationInfo] overload
 * and the legacy [String] overload, which used to start the sync foreground
 * service twice. The legacy overloads now bail out on API 33+ so only the
 * AssociationInfo path runs there.
 */
class MeterCompanionService : CompanionDeviceService() {

    override fun onDeviceAppeared(associationInfo: AssociationInfo) {
        Timber.i("Meter appeared: id=${associationInfo.id} addr=${associationInfo.deviceMacAddress}")
        val address = associationInfo.deviceMacAddress?.toString() ?: return
        SyncForegroundService.trigger(this, address)
    }

    @Deprecated("Legacy callback used pre-Android 13")
    override fun onDeviceAppeared(address: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) return
        Timber.i("Meter appeared (legacy): $address")
        SyncForegroundService.trigger(this, address)
    }

    override fun onDeviceDisappeared(associationInfo: AssociationInfo) {
        Timber.i("Meter disappeared: id=${associationInfo.id}")
    }

    @Deprecated("Legacy callback used pre-Android 13")
    override fun onDeviceDisappeared(address: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) return
        Timber.i("Meter disappeared (legacy): $address")
    }
}
