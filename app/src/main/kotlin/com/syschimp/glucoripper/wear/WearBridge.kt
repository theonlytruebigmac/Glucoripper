package com.syschimp.glucoripper.wear

import android.content.Context
import android.util.Log
import androidx.health.connect.client.records.BloodGlucoseRecord
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import com.syschimp.glucoripper.data.GlucoseUnit
import com.syschimp.glucoripper.data.Preferences
import com.syschimp.glucoripper.health.HealthConnectRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.tasks.await
import java.time.Duration
import java.time.Instant

/**
 * One-way phone → watch bridge. Reads the latest Health Connect state plus the
 * user's target range/unit and publishes a DataClient item the watch picks up
 * via [com.syschimp.glucoripper.wear.data.GlucoseListenerService] on its side.
 *
 * Fire-and-forget: any network/Play Services failure is logged, not propagated
 * — the watch is a nice-to-have, phone sync must never fail because of it.
 */
object WearBridge {

    private const val TAG = "WearBridge"
    private const val PATH = "/glucose/latest"
    private const val WINDOW_HOURS = 24L
    private const val MAX_WINDOW_POINTS = 200

    // Keys must match com.syschimp.glucoripper.wear.data.WearPaths on the watch.
    private const val KEY_TIME = "time"
    private const val KEY_MGDL = "mgDl"
    private const val KEY_MEAL = "meal"
    private const val KEY_LOW = "targetLow"
    private const val KEY_HIGH = "targetHigh"
    private const val KEY_UNIT = "unit"
    private const val KEY_WIN_TIMES = "winTimes"
    private const val KEY_WIN_VALUES = "winMgDls"
    private const val KEY_LAST_SYNC = "lastSync"

    suspend fun push(context: Context) {
        runCatching { pushInternal(context) }
            .onFailure { Log.w(TAG, "Wear push failed: ${it.message}") }
    }

    private suspend fun pushInternal(context: Context) {
        val hc = HealthConnectRepository(context)
        if (!hc.hasAllPermissions()) return

        val readings = hc.readRecentReadings(MAX_WINDOW_POINTS)
        val latest = readings.firstOrNull() ?: return

        val prefs = Preferences(context).flow.first()
        val cutoff = Instant.now().minus(Duration.ofHours(WINDOW_HOURS))
        val window = readings
            .filter { it.time.isAfter(cutoff) }
            .sortedBy { it.time }
            .take(MAX_WINDOW_POINTS)

        val req = PutDataMapRequest.create(PATH).apply {
            dataMap.putLong(KEY_TIME, latest.time.toEpochMilli())
            dataMap.putDouble(KEY_MGDL, latest.level.inMilligramsPerDeciliter)
            dataMap.putInt(KEY_MEAL, latest.relationToMeal)
            dataMap.putDouble(KEY_LOW, prefs.targetLowMgDl)
            dataMap.putDouble(KEY_HIGH, prefs.targetHighMgDl)
            dataMap.putString(KEY_UNIT, prefs.unit.wireName)
            dataMap.putLongArray(
                KEY_WIN_TIMES,
                window.map { it.time.toEpochMilli() }.toLongArray(),
            )
            dataMap.putFloatArray(
                KEY_WIN_VALUES,
                window.map { it.level.inMilligramsPerDeciliter.toFloat() }.toFloatArray(),
            )
            dataMap.putLong(KEY_LAST_SYNC, System.currentTimeMillis())
        }

        val request = req.asPutDataRequest().setUrgent()
        Wearable.getDataClient(context).putDataItem(request).await()
    }

    private val GlucoseUnit.wireName: String
        get() = when (this) {
            GlucoseUnit.MG_PER_DL -> "MG_PER_DL"
            GlucoseUnit.MMOL_PER_L -> "MMOL_PER_L"
        }

    // Unused but kept for symmetry with the watch-side equivalent.
    @Suppress("unused")
    private fun BloodGlucoseRecord.mgDl(): Double = level.inMilligramsPerDeciliter
}
