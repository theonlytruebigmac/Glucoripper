package com.syschimp.glucoripper.wear.data

import android.content.Context
import androidx.datastore.preferences.core.byteArrayPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.nio.ByteBuffer
import java.nio.ByteOrder

private val Context.glucoseDataStore by preferencesDataStore(name = "wear_glucose")

/**
 * Persists the latest [GlucosePayload] from the phone across watch restarts.
 *
 * The 7-day window arrays are stored as a single length-prefixed binary blob so
 * the three parallel arrays can never get out of sync (the previous comma-joined
 * string format dropped malformed entries silently and let lengths drift). Read
 * defensively rejects blobs that don't match the declared length and falls back
 * to empty arrays — better to render "no readings" than to render mismatched ones.
 */
class GlucoseStore(private val context: Context) {

    private val kTime = longPreferencesKey("latest_time")
    private val kMgDl = doublePreferencesKey("latest_mgDl")
    private val kMeal = intPreferencesKey("latest_meal")
    private val kLow = doublePreferencesKey("target_low")
    private val kHigh = doublePreferencesKey("target_high")
    private val kUnit = stringPreferencesKey("unit")
    private val kWindow = byteArrayPreferencesKey("win_blob_v1")
    private val kFastLow = doublePreferencesKey("fasting_low")
    private val kFastHigh = doublePreferencesKey("fasting_high")
    private val kPreLow = doublePreferencesKey("pre_meal_low")
    private val kPreHigh = doublePreferencesKey("pre_meal_high")
    private val kPostLow = doublePreferencesKey("post_meal_low")
    private val kPostHigh = doublePreferencesKey("post_meal_high")
    private val kLastSync = longPreferencesKey("last_sync")

    val flow: Flow<GlucosePayload> = context.glucoseDataStore.data.map { p ->
        val unit = p[kUnit]
            ?.let { runCatching { GlucoseUnit.valueOf(it) }.getOrNull() }
            ?: GlucoseUnit.MG_PER_DL
        val window = decodeWindow(p[kWindow])
        GlucosePayload(
            latestTimeMillis = p[kTime] ?: 0L,
            latestMgDl = p[kMgDl] ?: 0.0,
            latestMealRelation = p[kMeal] ?: 0,
            targetLowMgDl = p[kLow] ?: 70.0,
            targetHighMgDl = p[kHigh] ?: 140.0,
            unit = unit,
            windowTimesMillis = window.times,
            windowMgDls = window.values,
            windowMealRelations = window.meals,
            fastingLowMgDl = p[kFastLow] ?: 80.0,
            fastingHighMgDl = p[kFastHigh] ?: 130.0,
            preMealLowMgDl = p[kPreLow] ?: 80.0,
            preMealHighMgDl = p[kPreHigh] ?: 130.0,
            postMealLowMgDl = p[kPostLow] ?: 80.0,
            postMealHighMgDl = p[kPostHigh] ?: 180.0,
            lastSyncMillis = p[kLastSync] ?: 0L,
        )
    }

    suspend fun save(payload: GlucosePayload) {
        val blob = encodeWindow(
            payload.windowTimesMillis,
            payload.windowMgDls,
            payload.windowMealRelations,
        )
        context.glucoseDataStore.edit { p ->
            p[kTime] = payload.latestTimeMillis
            p[kMgDl] = payload.latestMgDl
            p[kMeal] = payload.latestMealRelation
            p[kLow] = payload.targetLowMgDl
            p[kHigh] = payload.targetHighMgDl
            p[kUnit] = payload.unit.name
            p[kWindow] = blob
            p[kFastLow] = payload.fastingLowMgDl
            p[kFastHigh] = payload.fastingHighMgDl
            p[kPreLow] = payload.preMealLowMgDl
            p[kPreHigh] = payload.preMealHighMgDl
            p[kPostLow] = payload.postMealLowMgDl
            p[kPostHigh] = payload.postMealHighMgDl
            p[kLastSync] = payload.lastSyncMillis
        }
    }
}

private data class Window(val times: LongArray, val values: FloatArray, val meals: IntArray)

private val EMPTY_WINDOW = Window(LongArray(0), FloatArray(0), IntArray(0))

/**
 * Encodes the 7-day window arrays as: i32 count | count*i64 times | count*f32 values | count*i32 meals.
 * Truncates to the shortest array if the inputs disagree, since storing an inconsistent
 * blob would be worse than dropping the trailing entries.
 */
private fun encodeWindow(times: LongArray, values: FloatArray, meals: IntArray): ByteArray {
    val count = minOf(times.size, values.size, meals.size)
    val buf = ByteBuffer.allocate(4 + count * (8 + 4 + 4)).order(ByteOrder.LITTLE_ENDIAN)
    buf.putInt(count)
    for (i in 0 until count) buf.putLong(times[i])
    for (i in 0 until count) buf.putFloat(values[i])
    for (i in 0 until count) buf.putInt(meals[i])
    return buf.array()
}

private fun decodeWindow(bytes: ByteArray?): Window {
    if (bytes == null || bytes.size < 4) return EMPTY_WINDOW
    val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
    val count = buf.int
    val expected = 4 + count * (8 + 4 + 4)
    if (count < 0 || bytes.size != expected) return EMPTY_WINDOW
    val t = LongArray(count) { buf.long }
    val v = FloatArray(count) { buf.float }
    val m = IntArray(count) { buf.int }
    return Window(t, v, m)
}
