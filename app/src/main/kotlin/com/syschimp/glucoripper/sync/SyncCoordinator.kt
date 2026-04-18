package com.syschimp.glucoripper.sync

import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.content.Context
import android.util.Log
import androidx.health.connect.client.records.BloodGlucoseRecord
import com.syschimp.glucoripper.ble.GlucoseGattClient
import com.syschimp.glucoripper.ble.GlucoseRecord
import com.syschimp.glucoripper.data.AutoPushMode
import com.syschimp.glucoripper.data.Preferences
import com.syschimp.glucoripper.data.StagedReading
import com.syschimp.glucoripper.data.StagingStore
import com.syschimp.glucoripper.data.SyncHistory
import com.syschimp.glucoripper.data.SyncHistoryEntry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.withLock

class SyncCoordinator(private val context: Context) {
    private val tag = "SyncCoordinator"
    private val syncState = SyncState(context)
    private val staging = StagingStore(context)
    private val history = SyncHistory(context)
    private val prefs = Preferences(context)
    private val pusher = StagingPusher(context)

    data class SyncResult(
        val pulled: Int,
        val staged: Int,
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

            val (measured, control) = records.partition {
                !it.isControlSolution && it.mgPerDl != null
            }

            val staged = measured.map { it.toStaged(address) }
            if (staged.isNotEmpty()) staging.addOrReplace(staged)

            val highest = records.maxOfOrNull { it.sequenceNumber }
            if (highest != null) syncState.setLastSequence(address, highest)

            // If the user chose "after each sync" auto-push, immediately push
            // everything in staging. Swallow errors here so a failed push doesn't
            // mark the BLE sync itself as failed — the user can retry manually.
            val autoMode = prefs.flow.first().autoPushMode
            if (autoMode == AutoPushMode.AFTER_SYNC) {
                runCatching { pusher.push() }
                    .onFailure { Log.w(tag, "Auto-push after sync failed", it) }
            }

            SyncResult(
                pulled = records.size,
                staged = staged.size,
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
                        written = r.staged,
                        skippedControl = r.skippedControlSolutions,
                        message = null,
                    )
                )
                SyncBus.setIdle(
                    message = "Staged ${r.staged} reading${if (r.staged == 1) "" else "s"}" +
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

    private fun GlucoseRecord.toStaged(address: String): StagedReading =
        StagedReading(
            meterAddress = address,
            sequenceNumber = sequenceNumber,
            time = time,
            mgPerDl = mgPerDl ?: 0.0,
            meterMeal = when (mealRelation) {
                GlucoseRecord.MealRelation.PREPRANDIAL -> BloodGlucoseRecord.RELATION_TO_MEAL_BEFORE_MEAL
                GlucoseRecord.MealRelation.POSTPRANDIAL -> BloodGlucoseRecord.RELATION_TO_MEAL_AFTER_MEAL
                GlucoseRecord.MealRelation.FASTING -> BloodGlucoseRecord.RELATION_TO_MEAL_FASTING
                GlucoseRecord.MealRelation.CASUAL -> BloodGlucoseRecord.RELATION_TO_MEAL_GENERAL
                GlucoseRecord.MealRelation.BEDTIME, null -> BloodGlucoseRecord.RELATION_TO_MEAL_UNKNOWN
            },
            specimenSource = when (sampleType) {
                GlucoseRecord.SampleType.CAPILLARY_WHOLE_BLOOD,
                GlucoseRecord.SampleType.UNDETERMINED_WHOLE_BLOOD,
                GlucoseRecord.SampleType.VENOUS_WHOLE_BLOOD,
                GlucoseRecord.SampleType.ARTERIAL_WHOLE_BLOOD ->
                    BloodGlucoseRecord.SPECIMEN_SOURCE_WHOLE_BLOOD
                GlucoseRecord.SampleType.CAPILLARY_PLASMA,
                GlucoseRecord.SampleType.UNDETERMINED_PLASMA,
                GlucoseRecord.SampleType.VENOUS_PLASMA,
                GlucoseRecord.SampleType.ARTERIAL_PLASMA ->
                    BloodGlucoseRecord.SPECIMEN_SOURCE_PLASMA
                GlucoseRecord.SampleType.INTERSTITIAL_FLUID ->
                    BloodGlucoseRecord.SPECIMEN_SOURCE_INTERSTITIAL_FLUID
                else -> BloodGlucoseRecord.SPECIMEN_SOURCE_UNKNOWN
            },
        )
}
