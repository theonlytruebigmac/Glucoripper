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
    val windowMealRelations: IntArray,
    val fastingLowMgDl: Double,
    val fastingHighMgDl: Double,
    val preMealLowMgDl: Double,
    val preMealHighMgDl: Double,
    val postMealLowMgDl: Double,
    val postMealHighMgDl: Double,
    val lastSyncMillis: Long,
) {
    val latestInstant: Instant get() = Instant.ofEpochMilli(latestTimeMillis)
    val lastSyncInstant: Instant get() = Instant.ofEpochMilli(lastSyncMillis)

    /**
     * Returns the low/high mg/dL target band appropriate for a reading's meal
     * relation, mirroring the phone-side `UserPreferences.targetRangeFor`.
     */
    fun targetRangeFor(mealRelation: Int): Pair<Double, Double> = when (mealRelation) {
        RELATION_FASTING -> fastingLowMgDl to fastingHighMgDl
        RELATION_BEFORE_MEAL -> preMealLowMgDl to preMealHighMgDl
        RELATION_AFTER_MEAL -> postMealLowMgDl to postMealHighMgDl
        else -> targetLowMgDl to targetHighMgDl
    }

    companion object {
        // Constants match androidx.health.connect.client.records.BloodGlucoseRecord
        // so the wear module doesn't need to depend on Health Connect.
        const val RELATION_UNKNOWN = 0
        const val RELATION_GENERAL = 1
        const val RELATION_FASTING = 2
        const val RELATION_BEFORE_MEAL = 3
        const val RELATION_AFTER_MEAL = 4

        val Empty = GlucosePayload(
            latestTimeMillis = 0L,
            latestMgDl = 0.0,
            latestMealRelation = 0,
            targetLowMgDl = 70.0,
            targetHighMgDl = 140.0,
            unit = GlucoseUnit.MG_PER_DL,
            windowTimesMillis = LongArray(0),
            windowMgDls = FloatArray(0),
            windowMealRelations = IntArray(0),
            fastingLowMgDl = 80.0,
            fastingHighMgDl = 130.0,
            preMealLowMgDl = 80.0,
            preMealHighMgDl = 130.0,
            postMealLowMgDl = 80.0,
            postMealHighMgDl = 180.0,
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
                fastingLowMgDl == other.fastingLowMgDl &&
                fastingHighMgDl == other.fastingHighMgDl &&
                preMealLowMgDl == other.preMealLowMgDl &&
                preMealHighMgDl == other.preMealHighMgDl &&
                postMealLowMgDl == other.postMealLowMgDl &&
                postMealHighMgDl == other.postMealHighMgDl &&
                lastSyncMillis == other.lastSyncMillis &&
                windowTimesMillis.contentEquals(other.windowTimesMillis) &&
                windowMgDls.contentEquals(other.windowMgDls) &&
                windowMealRelations.contentEquals(other.windowMealRelations)
    }

    override fun hashCode(): Int {
        var r = latestTimeMillis.hashCode()
        r = 31 * r + latestMgDl.hashCode()
        r = 31 * r + latestMealRelation
        r = 31 * r + targetLowMgDl.hashCode()
        r = 31 * r + targetHighMgDl.hashCode()
        r = 31 * r + unit.hashCode()
        r = 31 * r + fastingLowMgDl.hashCode()
        r = 31 * r + fastingHighMgDl.hashCode()
        r = 31 * r + preMealLowMgDl.hashCode()
        r = 31 * r + preMealHighMgDl.hashCode()
        r = 31 * r + postMealLowMgDl.hashCode()
        r = 31 * r + postMealHighMgDl.hashCode()
        r = 31 * r + lastSyncMillis.hashCode()
        r = 31 * r + windowTimesMillis.contentHashCode()
        r = 31 * r + windowMgDls.contentHashCode()
        r = 31 * r + windowMealRelations.contentHashCode()
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
    const val KEY_WIN_MEALS = "winMeals"
    const val KEY_LAST_SYNC = "lastSync"
    const val KEY_FASTING_LOW = "fastingLow"
    const val KEY_FASTING_HIGH = "fastingHigh"
    const val KEY_PRE_MEAL_LOW = "preMealLow"
    const val KEY_PRE_MEAL_HIGH = "preMealHigh"
    const val KEY_POST_MEAL_LOW = "postMealLow"
    const val KEY_POST_MEAL_HIGH = "postMealHigh"
}
