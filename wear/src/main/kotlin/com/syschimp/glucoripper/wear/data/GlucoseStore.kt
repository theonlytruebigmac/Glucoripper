package com.syschimp.glucoripper.wear.data

import android.content.Context
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.glucoseDataStore by preferencesDataStore(name = "wear_glucose")

/** Persists the latest [GlucosePayload] from the phone across watch restarts. */
class GlucoseStore(private val context: Context) {

    private val kTime = longPreferencesKey("latest_time")
    private val kMgDl = doublePreferencesKey("latest_mgDl")
    private val kMeal = intPreferencesKey("latest_meal")
    private val kLow = doublePreferencesKey("target_low")
    private val kHigh = doublePreferencesKey("target_high")
    private val kUnit = stringPreferencesKey("unit")
    private val kWinT = stringPreferencesKey("win_times")
    private val kWinV = stringPreferencesKey("win_values")
    private val kWinM = stringPreferencesKey("win_meals")
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
        GlucosePayload(
            latestTimeMillis = p[kTime] ?: 0L,
            latestMgDl = p[kMgDl] ?: 0.0,
            latestMealRelation = p[kMeal] ?: 0,
            targetLowMgDl = p[kLow] ?: 70.0,
            targetHighMgDl = p[kHigh] ?: 140.0,
            unit = unit,
            windowTimesMillis = p[kWinT].decodeLongs(),
            windowMgDls = p[kWinV].decodeFloats(),
            windowMealRelations = p[kWinM].decodeInts(),
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
        context.glucoseDataStore.edit { p ->
            p[kTime] = payload.latestTimeMillis
            p[kMgDl] = payload.latestMgDl
            p[kMeal] = payload.latestMealRelation
            p[kLow] = payload.targetLowMgDl
            p[kHigh] = payload.targetHighMgDl
            p[kUnit] = payload.unit.name
            p[kWinT] = payload.windowTimesMillis.joinToString(",")
            p[kWinV] = payload.windowMgDls.joinToString(",")
            p[kWinM] = payload.windowMealRelations.joinToString(",")
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

private fun String?.decodeLongs(): LongArray =
    if (isNullOrBlank()) LongArray(0)
    else split(",").mapNotNull { it.trim().toLongOrNull() }.toLongArray()

private fun String?.decodeFloats(): FloatArray =
    if (isNullOrBlank()) FloatArray(0)
    else split(",").mapNotNull { it.trim().toFloatOrNull() }.toFloatArray()

private fun String?.decodeInts(): IntArray =
    if (isNullOrBlank()) IntArray(0)
    else split(",").mapNotNull { it.trim().toIntOrNull() }.toIntArray()
