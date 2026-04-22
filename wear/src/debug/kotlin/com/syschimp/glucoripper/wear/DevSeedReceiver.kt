package com.syschimp.glucoripper.wear

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.wear.tiles.TileService
import androidx.wear.watchface.complications.datasource.ComplicationDataSourceUpdateRequester
import com.syschimp.glucoripper.wear.complication.GlucoseComplicationService
import com.syschimp.glucoripper.wear.data.GlucosePayload
import com.syschimp.glucoripper.wear.data.GlucoseStore
import com.syschimp.glucoripper.wear.data.GlucoseUnit
import com.syschimp.glucoripper.wear.tile.GlucoseTileService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Debug-only receiver that seeds [GlucoseStore] with a realistic sample payload
 * so the tile UI can be verified without a paired phone. Lives in `src/debug/`
 * and is never compiled into release builds.
 *
 * Trigger with:
 *   adb shell am broadcast -a com.syschimp.glucoripper.wear.DEV_SEED \
 *     -n com.syschimp.glucoripper/com.syschimp.glucoripper.wear.DevSeedReceiver
 */
class DevSeedReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val now = System.currentTimeMillis()
        val step = 15 * 60_000L
        val samples = intArrayOf(118, 132, 125, 148, 140, 128, 122, 135, 130, 132)
        val times = LongArray(samples.size) { i -> now - (samples.size - 1 - i) * step }
        val values = FloatArray(samples.size) { samples[it].toFloat() }
        val meals = IntArray(samples.size)

        val payload = GlucosePayload(
            latestTimeMillis = now,
            latestMgDl = samples.last().toDouble(),
            latestMealRelation = GlucosePayload.RELATION_UNKNOWN,
            targetLowMgDl = 70.0,
            targetHighMgDl = 140.0,
            unit = GlucoseUnit.MG_PER_DL,
            windowTimesMillis = times,
            windowMgDls = values,
            windowMealRelations = meals,
            fastingLowMgDl = 80.0,
            fastingHighMgDl = 130.0,
            preMealLowMgDl = 80.0,
            preMealHighMgDl = 130.0,
            postMealLowMgDl = 80.0,
            postMealHighMgDl = 180.0,
            lastSyncMillis = now,
        )

        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val appCtx = context.applicationContext
                GlucoseStore(appCtx).save(payload)
                TileService.getUpdater(appCtx)
                    .requestUpdate(GlucoseTileService::class.java)
                ComplicationDataSourceUpdateRequester.create(
                    context = appCtx,
                    complicationDataSourceComponent = ComponentName(
                        appCtx,
                        GlucoseComplicationService::class.java,
                    ),
                ).requestUpdateAll()
            } finally {
                pending.finish()
            }
        }
    }
}
