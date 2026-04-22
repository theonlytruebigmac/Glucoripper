package com.syschimp.glucoripper.companion

import android.companion.AssociationInfo
import android.companion.AssociationRequest
import android.companion.BluetoothLeDeviceFilter
import android.companion.CompanionDeviceManager
import android.content.Context
import android.content.IntentSender
import android.os.Build
import android.os.ParcelUuid
import com.syschimp.glucoripper.ble.GlucoseUuids
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine
import timber.log.Timber

/**
 * Associates the user's glucose meter with the app using CompanionDeviceManager.
 * After association, the OS wakes [MeterCompanionService] whenever the bonded
 * device comes into BLE range — no manual background scanning required.
 */
class MeterPairingManager(private val context: Context) {
    private val cdm = context.getSystemService(CompanionDeviceManager::class.java)

    data class Association(val id: Int, val address: String?, val displayName: String?)

    /** Existing associations for this package (empty if never paired). */
    fun associations(): List<Association> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            cdm.myAssociations.map {
                Association(it.id, it.deviceMacAddress?.toString(), it.displayName?.toString())
            }
        } else {
            @Suppress("DEPRECATION")
            cdm.associations.map { Association(id = 0, address = it, displayName = null) }
        }
    }

    /**
     * Start a pairing request. Returns an [IntentSender] that the caller must launch
     * with an ActivityResultLauncher to show the system device-picker dialog.
     */
    suspend fun requestAssociation(): IntentSender = suspendCancellableCoroutine { cont ->
        val filter = BluetoothLeDeviceFilter.Builder()
            .setScanFilter(
                android.bluetooth.le.ScanFilter.Builder()
                    .setServiceUuid(ParcelUuid(GlucoseUuids.SERVICE))
                    .build()
            )
            .build()

        val request = AssociationRequest.Builder()
            .addDeviceFilter(filter)
            .setSingleDevice(false)
            .build()

        val callback = object : CompanionDeviceManager.Callback() {
            override fun onAssociationPending(sender: IntentSender) {
                if (cont.isActive) cont.resume(sender)
            }

            @Deprecated("Legacy callback prior to Android 14")
            override fun onDeviceFound(sender: IntentSender) {
                if (cont.isActive) cont.resume(sender)
            }

            override fun onAssociationCreated(associationInfo: AssociationInfo) {
                Timber.i("Association created: id=${associationInfo.id}")
                enableBackgroundOps(associationInfo.id)
            }

            override fun onFailure(error: CharSequence?) {
                if (cont.isActive) cont.resumeWithException(
                    RuntimeException("Association failed: $error")
                )
            }
        }

        cdm.associate(request, callback, /* handler = */ null)
    }

    /**
     * Called after association; asks the OS to wake our [MeterCompanionService] whenever
     * the bonded device is in range.
     */
    fun enableBackgroundOps(associationId: Int) {
        val address = associations().firstOrNull { it.id == associationId }?.address ?: return
        @Suppress("DEPRECATION")
        runCatching { cdm.startObservingDevicePresence(address) }
            .onFailure { Timber.w(it, "startObservingDevicePresence failed") }
    }

    fun disassociate(associationId: Int) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            runCatching { cdm.disassociate(associationId) }
        }
    }
}
