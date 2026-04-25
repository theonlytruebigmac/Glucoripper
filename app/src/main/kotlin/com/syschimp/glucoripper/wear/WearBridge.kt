package com.syschimp.glucoripper.wear

import android.content.Context
import androidx.health.connect.client.records.BloodGlucoseRecord
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import com.syschimp.glucoripper.data.GlucoseUnit
import com.syschimp.glucoripper.data.Preferences
import com.syschimp.glucoripper.health.HealthConnectRepository
import com.syschimp.glucoripper.shared.WearKeys
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.tasks.await
import timber.log.Timber
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

    private const val WINDOW_HOURS = 24L * 7L
    private const val MAX_WINDOW_POINTS = 500

    // Fingerprint of the last pushed payload (excluding KEY_LAST_SYNC, which is
    // just a liveness timestamp and would defeat dedup). DataClient itself
    // dedupes identical DataItems, but gating here avoids the Play Services RPC
    // entirely when nothing user-visible has changed.
    @Volatile private var lastFingerprint: String? = null
    @Volatile private var lastFingerprintAtMs: Long = 0L
    // Re-push at least every 24h so a freshly-installed watch app (whose local
    // DataItem cache is empty) still receives the latest reading even if the
    // phone hasn't seen new HC data since the previous push.
    private const val FINGERPRINT_TTL_MS = 24L * 60L * 60L * 1000L

    /** Forget the cached fingerprint so the next [push] is unconditional. */
    fun invalidate() {
        lastFingerprint = null
        lastFingerprintAtMs = 0L
    }

    suspend fun push(context: Context) {
        runCatching { pushInternal(context) }
            .onFailure { Timber.w(it, "Wear push failed") }
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

        val winTimes = window.map { it.time.toEpochMilli() }.toLongArray()
        val winValues = window.map { it.level.inMilligramsPerDeciliter.toFloat() }.toFloatArray()
        val winMeals = window.map { it.relationToMeal }

        val fingerprint = listOf(
            latest.time.toEpochMilli(),
            latest.level.inMilligramsPerDeciliter,
            latest.relationToMeal,
            prefs.targetLowMgDl, prefs.targetHighMgDl,
            prefs.unit.wireName,
            prefs.fastingLowMgDl, prefs.fastingHighMgDl,
            prefs.preMealLowMgDl, prefs.preMealHighMgDl,
            prefs.postMealLowMgDl, prefs.postMealHighMgDl,
            winTimes.contentHashCode(),
            winValues.contentHashCode(),
            winMeals.hashCode(),
        ).joinToString("|")
        val now = System.currentTimeMillis()
        if (fingerprint == lastFingerprint && now - lastFingerprintAtMs < FINGERPRINT_TTL_MS) return

        val req = PutDataMapRequest.create(WearKeys.PATH_LATEST).apply {
            dataMap.putInt(WearKeys.KEY_SCHEMA, WearKeys.SCHEMA_VERSION)
            dataMap.putLong(WearKeys.KEY_TIME, latest.time.toEpochMilli())
            dataMap.putDouble(WearKeys.KEY_MGDL, latest.level.inMilligramsPerDeciliter)
            dataMap.putInt(WearKeys.KEY_MEAL, latest.relationToMeal)
            dataMap.putDouble(WearKeys.KEY_LOW, prefs.targetLowMgDl)
            dataMap.putDouble(WearKeys.KEY_HIGH, prefs.targetHighMgDl)
            dataMap.putString(WearKeys.KEY_UNIT, prefs.unit.wireName)
            dataMap.putLongArray(WearKeys.KEY_WIN_TIMES, winTimes)
            dataMap.putFloatArray(WearKeys.KEY_WIN_VALUES, winValues)
            dataMap.putIntegerArrayList(WearKeys.KEY_WIN_MEALS, ArrayList(winMeals))
            dataMap.putDouble(WearKeys.KEY_FASTING_LOW, prefs.fastingLowMgDl)
            dataMap.putDouble(WearKeys.KEY_FASTING_HIGH, prefs.fastingHighMgDl)
            dataMap.putDouble(WearKeys.KEY_PRE_MEAL_LOW, prefs.preMealLowMgDl)
            dataMap.putDouble(WearKeys.KEY_PRE_MEAL_HIGH, prefs.preMealHighMgDl)
            dataMap.putDouble(WearKeys.KEY_POST_MEAL_LOW, prefs.postMealLowMgDl)
            dataMap.putDouble(WearKeys.KEY_POST_MEAL_HIGH, prefs.postMealHighMgDl)
            dataMap.putLong(WearKeys.KEY_LAST_SYNC, System.currentTimeMillis())
        }

        val request = req.asPutDataRequest().setUrgent()
        Wearable.getDataClient(context).putDataItem(request).await()
        lastFingerprint = fingerprint
        lastFingerprintAtMs = now
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
