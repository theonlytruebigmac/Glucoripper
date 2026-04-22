package com.syschimp.glucoripper.wear.data

import android.content.ComponentName
import androidx.wear.tiles.TileService
import androidx.wear.watchface.complications.datasource.ComplicationDataSourceUpdateRequester
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.WearableListenerService
import com.syschimp.glucoripper.shared.WearKeys
import com.syschimp.glucoripper.wear.complication.GlucoseComplicationService
import com.syschimp.glucoripper.wear.tile.GlucoseTileService
import kotlinx.coroutines.runBlocking

/**
 * Receives the DataClient payload pushed by the phone and persists it into
 * [GlucoseStore] so the watch UI can read the latest state on resume.
 *
 * WearableListenerService callbacks run on a background thread, so we use
 * runBlocking to guarantee the DataStore write completes before the system
 * is free to tear the service down.
 */
class GlucoseListenerService : WearableListenerService() {

    override fun onDataChanged(events: DataEventBuffer) {
        val store = GlucoseStore(applicationContext)
        runBlocking {
            for (event in events) {
                if (event.type != DataEvent.TYPE_CHANGED) continue
                val item = event.dataItem
                if (item.uri.path != WearKeys.PATH_LATEST) continue
                val dm = DataMapItem.fromDataItem(item).dataMap
                val payload = GlucosePayload(
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
                    fastingLowMgDl = dm.getDouble(WearKeys.KEY_FASTING_LOW, 80.0),
                    fastingHighMgDl = dm.getDouble(WearKeys.KEY_FASTING_HIGH, 130.0),
                    preMealLowMgDl = dm.getDouble(WearKeys.KEY_PRE_MEAL_LOW, 80.0),
                    preMealHighMgDl = dm.getDouble(WearKeys.KEY_PRE_MEAL_HIGH, 130.0),
                    postMealLowMgDl = dm.getDouble(WearKeys.KEY_POST_MEAL_LOW, 80.0),
                    postMealHighMgDl = dm.getDouble(WearKeys.KEY_POST_MEAL_HIGH, 180.0),
                    lastSyncMillis = dm.getLong(WearKeys.KEY_LAST_SYNC),
                )
                store.save(payload)
            }
        }
        requestSurfaceRefresh()
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
}
