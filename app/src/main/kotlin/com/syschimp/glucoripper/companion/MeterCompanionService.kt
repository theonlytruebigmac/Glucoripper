package com.syschimp.glucoripper.companion

import android.bluetooth.BluetoothDevice
import android.companion.AssociationInfo
import android.companion.CompanionDeviceService
import android.util.Log
import com.syschimp.glucoripper.sync.SyncForegroundService

/**
 * Invoked by the OS when our bonded meter is detected nearby. We hand off to a
 * short-lived foreground service that runs one sync and stops.
 */
class MeterCompanionService : CompanionDeviceService() {
    private val tag = "MeterCompanion"

    override fun onDeviceAppeared(associationInfo: AssociationInfo) {
        Log.i(tag, "Meter appeared: id=${associationInfo.id} addr=${associationInfo.deviceMacAddress}")
        val address = associationInfo.deviceMacAddress?.toString() ?: return
        SyncForegroundService.trigger(this, address)
    }

    @Deprecated("Legacy callback used pre-Android 14")
    override fun onDeviceAppeared(address: String) {
        Log.i(tag, "Meter appeared (legacy): $address")
        SyncForegroundService.trigger(this, address)
    }

    override fun onDeviceDisappeared(associationInfo: AssociationInfo) {
        Log.i(tag, "Meter disappeared: id=${associationInfo.id}")
    }

    @Deprecated("Legacy callback used pre-Android 14")
    override fun onDeviceDisappeared(address: String) {
        Log.i(tag, "Meter disappeared (legacy): $address")
    }
}
