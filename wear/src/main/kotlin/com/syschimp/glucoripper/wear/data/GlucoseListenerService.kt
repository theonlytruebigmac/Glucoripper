package com.syschimp.glucoripper.wear.data

import android.content.ComponentName
import androidx.wear.tiles.TileService
import androidx.wear.watchface.complications.datasource.ComplicationDataSourceUpdateRequester
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.WearableListenerService
import com.syschimp.glucoripper.wear.complication.GlucoseComplicationService
import com.syschimp.glucoripper.wear.tile.GlucoseTileService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Receives the DataClient payload pushed by the phone and persists it into
 * [GlucoseStore] so the watch UI can read the latest state on resume.
 */
class GlucoseListenerService : WearableListenerService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onDataChanged(events: DataEventBuffer) {
        for (event in events) {
            if (event.type != DataEvent.TYPE_CHANGED) continue
            val item = event.dataItem
            if (item.uri.path != WearPaths.LATEST) continue
            val dm = DataMapItem.fromDataItem(item).dataMap
            val payload = GlucosePayload(
                latestTimeMillis = dm.getLong(WearPaths.KEY_TIME),
                latestMgDl = dm.getDouble(WearPaths.KEY_MGDL),
                latestMealRelation = dm.getInt(WearPaths.KEY_MEAL),
                targetLowMgDl = dm.getDouble(WearPaths.KEY_LOW),
                targetHighMgDl = dm.getDouble(WearPaths.KEY_HIGH),
                unit = runCatching {
                    GlucoseUnit.valueOf(dm.getString(WearPaths.KEY_UNIT) ?: "MG_PER_DL")
                }.getOrDefault(GlucoseUnit.MG_PER_DL),
                windowTimesMillis = dm.getLongArray(WearPaths.KEY_WIN_TIMES) ?: LongArray(0),
                windowMgDls = dm.getFloatArray(WearPaths.KEY_WIN_VALUES) ?: FloatArray(0),
                windowMealRelations = dm.getIntegerArrayList(WearPaths.KEY_WIN_MEALS)
                    ?.toIntArray() ?: IntArray(0),
                fastingLowMgDl = dm.getDouble(WearPaths.KEY_FASTING_LOW, 80.0),
                fastingHighMgDl = dm.getDouble(WearPaths.KEY_FASTING_HIGH, 130.0),
                preMealLowMgDl = dm.getDouble(WearPaths.KEY_PRE_MEAL_LOW, 80.0),
                preMealHighMgDl = dm.getDouble(WearPaths.KEY_PRE_MEAL_HIGH, 130.0),
                postMealLowMgDl = dm.getDouble(WearPaths.KEY_POST_MEAL_LOW, 80.0),
                postMealHighMgDl = dm.getDouble(WearPaths.KEY_POST_MEAL_HIGH, 180.0),
                lastSyncMillis = dm.getLong(WearPaths.KEY_LAST_SYNC),
            )
            scope.launch {
                GlucoseStore(applicationContext).save(payload)
                requestSurfaceRefresh()
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
}
