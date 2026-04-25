package com.syschimp.glucoripper.wear.data

import android.content.ComponentName
import android.util.Log
import androidx.wear.tiles.TileService
import androidx.wear.watchface.complications.datasource.ComplicationDataSourceUpdateRequester
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.WearableListenerService
import com.syschimp.glucoripper.shared.WearKeys
import com.syschimp.glucoripper.wear.complication.GlucoseComplicationService
import com.syschimp.glucoripper.wear.tile.GlucoseTileService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

/**
 * Receives the DataClient payload pushed by the phone and persists it into
 * [GlucoseStore] so the watch UI can read the latest state on resume.
 *
 * The DataStore write runs on a service-scoped IO coroutine with a bounded
 * timeout — replacing the previous `runBlocking` that pinned the binder thread
 * and silently swallowed write failures. Surface refresh only fires after a
 * successful write so a corrupted DataStore doesn't trigger empty re-renders.
 */
class GlucoseListenerService : WearableListenerService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val store by lazy { GlucoseStore(applicationContext) }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    override fun onDataChanged(events: DataEventBuffer) {
        val payloads = events.mapNotNull { event ->
            // TYPE_CHANGED is the only event we act on. TYPE_DELETED would mean
            // the phone explicitly cleared its glucose state — currently the phone
            // never deletes the data item, so logging is enough.
            if (event.type != DataEvent.TYPE_CHANGED) return@mapNotNull null
            val item = event.dataItem
            if (item.uri.path != WearKeys.PATH_LATEST) return@mapNotNull null
            val dm = DataMapItem.fromDataItem(item).dataMap
            // Schema gate: an older watch should ignore a higher-major payload
            // rather than mis-parse it. Default to current schema for legacy
            // payloads that predate the schema field.
            val schema = dm.getInt(WearKeys.KEY_SCHEMA, WearKeys.SCHEMA_VERSION)
            if (schema > WearKeys.SCHEMA_VERSION) {
                Log.w(TAG, "Ignoring payload with newer schema=$schema (we support ${WearKeys.SCHEMA_VERSION})")
                return@mapNotNull null
            }
            GlucosePayload(
                latestTimeMillis = dm.getLong(WearKeys.KEY_TIME),
                latestMgDl = dm.getDouble(WearKeys.KEY_MGDL),
                latestMealRelation = dm.getInt(WearKeys.KEY_MEAL),
                targetLowMgDl = dm.getDouble(WearKeys.KEY_LOW),
                targetHighMgDl = dm.getDouble(WearKeys.KEY_HIGH),
                unit = runCatching {
                    GlucoseUnit.valueOf(dm.getString(WearKeys.KEY_UNIT) ?: "MG_PER_DL")
                }.getOrDefault(GlucoseUnit.MG_PER_DL),
                windowTimesMillis = dm.getLongArray(WearKeys.KEY_WIN_TIMES) ?: LongArray(0),
                windowMgDls = dm.getFloatArray(WearKeys.KEY_WIN_VALUES) ?: FloatArray(0),
                windowMealRelations = dm.getIntegerArrayList(WearKeys.KEY_WIN_MEALS)
                    ?.toIntArray() ?: IntArray(0),
                fastingLowMgDl = dm.getDouble(WearKeys.KEY_FASTING_LOW, WearKeys.DEFAULT_FASTING_LOW),
                fastingHighMgDl = dm.getDouble(WearKeys.KEY_FASTING_HIGH, WearKeys.DEFAULT_FASTING_HIGH),
                preMealLowMgDl = dm.getDouble(WearKeys.KEY_PRE_MEAL_LOW, WearKeys.DEFAULT_PRE_MEAL_LOW),
                preMealHighMgDl = dm.getDouble(WearKeys.KEY_PRE_MEAL_HIGH, WearKeys.DEFAULT_PRE_MEAL_HIGH),
                postMealLowMgDl = dm.getDouble(WearKeys.KEY_POST_MEAL_LOW, WearKeys.DEFAULT_POST_MEAL_LOW),
                postMealHighMgDl = dm.getDouble(WearKeys.KEY_POST_MEAL_HIGH, WearKeys.DEFAULT_POST_MEAL_HIGH),
                lastSyncMillis = dm.getLong(WearKeys.KEY_LAST_SYNC),
            )
        }
        if (payloads.isEmpty()) return

        scope.launch {
            try {
                withTimeout(WRITE_TIMEOUT_MS) {
                    payloads.forEach { store.save(it) }
                }
                requestSurfaceRefresh()
            } catch (t: Throwable) {
                Log.w(TAG, "Failed to persist GlucosePayload from phone", t)
            }
        }
    }

    private fun requestSurfaceRefresh() {
        runCatching {
            TileService.getUpdater(applicationContext)
                .requestUpdate(GlucoseTileService::class.java)
        }
        runCatching {
            ComplicationDataSourceUpdateRequester.create(
                context = applicationContext,
                complicationDataSourceComponent = ComponentName(
                    applicationContext,
                    GlucoseComplicationService::class.java,
                ),
            ).requestUpdateAll()
        }
    }

    companion object {
        private const val TAG = "GlucoseListener"
        private const val WRITE_TIMEOUT_MS = 5_000L
    }
}
