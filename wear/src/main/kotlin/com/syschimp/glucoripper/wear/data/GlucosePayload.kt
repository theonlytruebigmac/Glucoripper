package com.syschimp.glucoripper.wear.data

import java.time.Instant

/**
 * Snapshot of phone-side glucose state pushed to the watch via DataClient.
 * Fields mirror the DataMap keys in [WearPaths] so serialization stays dumb.
 */
data class GlucosePayload(
    val latestTimeMillis: Long,
    val latestMgDl: Double,
    val latestMealRelation: Int,
    val targetLowMgDl: Double,
    val targetHighMgDl: Double,
    val unit: GlucoseUnit,
    val windowTimesMillis: LongArray,
    val windowMgDls: FloatArray,
    val lastSyncMillis: Long,
) {
    val latestInstant: Instant get() = Instant.ofEpochMilli(latestTimeMillis)
    val lastSyncInstant: Instant get() = Instant.ofEpochMilli(lastSyncMillis)

    companion object {
        val Empty = GlucosePayload(
            latestTimeMillis = 0L,
            latestMgDl = 0.0,
            latestMealRelation = 0,
            targetLowMgDl = 70.0,
            targetHighMgDl = 140.0,
            unit = GlucoseUnit.MG_PER_DL,
            windowTimesMillis = LongArray(0),
            windowMgDls = FloatArray(0),
            lastSyncMillis = 0L,
        )
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is GlucosePayload) return false
        return latestTimeMillis == other.latestTimeMillis &&
                latestMgDl == other.latestMgDl &&
                latestMealRelation == other.latestMealRelation &&
                targetLowMgDl == other.targetLowMgDl &&
                targetHighMgDl == other.targetHighMgDl &&
                unit == other.unit &&
                lastSyncMillis == other.lastSyncMillis &&
                windowTimesMillis.contentEquals(other.windowTimesMillis) &&
                windowMgDls.contentEquals(other.windowMgDls)
    }

    override fun hashCode(): Int {
        var r = latestTimeMillis.hashCode()
        r = 31 * r + latestMgDl.hashCode()
        r = 31 * r + latestMealRelation
        r = 31 * r + targetLowMgDl.hashCode()
        r = 31 * r + targetHighMgDl.hashCode()
        r = 31 * r + unit.hashCode()
        r = 31 * r + lastSyncMillis.hashCode()
        r = 31 * r + windowTimesMillis.contentHashCode()
        r = 31 * r + windowMgDls.contentHashCode()
        return r
    }
}

enum class GlucoseUnit { MG_PER_DL, MMOL_PER_L }

object WearPaths {
    const val LATEST = "/glucose/latest"

    const val KEY_TIME = "time"
    const val KEY_MGDL = "mgDl"
    const val KEY_MEAL = "meal"
    const val KEY_LOW = "targetLow"
    const val KEY_HIGH = "targetHigh"
    const val KEY_UNIT = "unit"
    const val KEY_WIN_TIMES = "winTimes"
    const val KEY_WIN_VALUES = "winMgDls"
    const val KEY_LAST_SYNC = "lastSync"
}
