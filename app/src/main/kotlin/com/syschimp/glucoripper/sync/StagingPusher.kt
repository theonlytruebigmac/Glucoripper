package com.syschimp.glucoripper.sync

import android.content.Context
import com.syschimp.glucoripper.data.Annotations
import com.syschimp.glucoripper.data.StagedReading
import com.syschimp.glucoripper.data.StagingStore
import com.syschimp.glucoripper.health.HealthConnectRepository
import kotlinx.coroutines.flow.first

/**
 * Reusable "push staged readings to Health Connect" helper used by both the
 * manual "Send" button in the UI and the scheduled [AutoPushWorker].
 *
 * On push, any user-added annotations (meal override, feeling, note) are copied
 * into the persistent [Annotations] store keyed by `clientRecordId`, so they
 * survive the move from staging → synced.
 */
class StagingPusher(context: Context) {
    private val staging = StagingStore(context)
    private val annotations = Annotations(context)
    private val health = HealthConnectRepository(context)

    data class Report(val attempted: Int, val written: Int)

    /** Push [toPush] to HC. If null, pushes every currently-staged reading. */
    suspend fun push(toPush: List<StagedReading>? = null): Result<Report> = runCatching {
        val list = toPush ?: staging.flow.first()
        if (list.isEmpty()) return@runCatching Report(0, 0)

        val written = health.pushStaged(list)

        list.forEach { r ->
            if (r.userMeal != null || r.feeling != null || !r.note.isNullOrBlank()) {
                annotations.update(r.id) { a ->
                    a.copy(
                        mealOverride = r.userMeal ?: a.mealOverride,
                        feeling = r.feeling ?: a.feeling,
                        note = r.note ?: a.note,
                    )
                }
            }
        }
        staging.remove(list.map { it.id })
        Report(attempted = list.size, written = written)
    }
}
