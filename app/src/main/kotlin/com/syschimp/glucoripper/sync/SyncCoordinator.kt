package com.syschimp.glucoripper.sync

import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.content.Context
import android.util.Log
import com.syschimp.glucoripper.ble.GlucoseGattClient
import com.syschimp.glucoripper.data.SyncHistory
import com.syschimp.glucoripper.data.SyncHistoryEntry
import com.syschimp.glucoripper.health.HealthConnectRepository
import kotlinx.coroutines.sync.withLock

class SyncCoordinator(private val context: Context) {
    private val tag = "SyncCoordinator"
    private val syncState = SyncState(context)
    private val healthRepo = HealthConnectRepository(context)
    private val history = SyncHistory(context)

    data class SyncResult(
        val pulled: Int,
        val written: Int,
        val skippedControlSolutions: Int,
        val highestSequence: Int?,
        val anyLowBattery: Boolean,
    )

    @SuppressLint("MissingPermission")
    suspend fun runOnce(address: String, forceFull: Boolean = false): Result<SyncResult> =
        SyncBus.mutex.withLock { doRun(address, forceFull) }

    private suspend fun doRun(address: String, forceFull: Boolean): Result<SyncResult> {
        SyncBus.setRunning(address)
        val result = runCatching {
            val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter
                ?: error("Bluetooth not available")
            require(adapter.isEnabled) { "Bluetooth is disabled" }

            val device = adapter.getRemoteDevice(address.uppercase())
                ?: error("No device for address $address")

            if (forceFull) syncState.reset(address)
            val checkpoint = syncState.lastSequence(address)
            Log.i(tag, "Syncing $address, checkpoint=$checkpoint forceFull=$forceFull")

            val records = GlucoseGattClient(context, device).pullRecords(checkpoint)
            Log.i(tag, "Pulled ${records.size} record(s)")

            val (measured, control) = records.partition { !it.isControlSolution && it.mgPerDl != null }
            val written = if (measured.isNotEmpty()) {
                healthRepo.writeGlucoseRecords(address, measured)
            } else 0

            val highest = records.maxOfOrNull { it.sequenceNumber }
            if (highest != null) syncState.setLastSequence(address, highest)

            SyncResult(
                pulled = records.size,
                written = written,
                skippedControlSolutions = control.size,
                highestSequence = highest,
                anyLowBattery = records.any { it.wasDeviceBatteryLow },
            )
        }

        result.fold(
            onSuccess = { r ->
                history.append(
                    SyncHistoryEntry(
                        timestampMillis = System.currentTimeMillis(),
                        meterAddress = address,
                        success = true,
                        pulled = r.pulled,
                        written = r.written,
                        skippedControl = r.skippedControlSolutions,
                        message = null,
                    )
                )
                SyncBus.setIdle(
                    message = "Pulled ${r.pulled}, wrote ${r.written}" +
                            if (r.skippedControlSolutions > 0) ", skipped ${r.skippedControlSolutions}" else "",
                    lowBattery = r.anyLowBattery,
                )
            },
            onFailure = { t ->
                history.append(
                    SyncHistoryEntry(
                        timestampMillis = System.currentTimeMillis(),
                        meterAddress = address,
                        success = false,
                        pulled = 0,
                        written = 0,
                        skippedControl = 0,
                        message = t.message,
                    )
                )
                SyncBus.setIdle(
                    message = "Sync failed: ${t.message}",
                    lowBattery = false,
                )
            },
        )
        return result
    }
}
