package com.syschimp.glucoripper.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.prefsStore by preferencesDataStore(name = "user_prefs")

enum class GlucoseUnit { MG_PER_DL, MMOL_PER_L }

enum class ThemeMode(val label: String) {
    SYSTEM("System default"),
    LIGHT("Light"),
    DARK("Dark");
    companion object {
        fun fromName(s: String?) = entries.firstOrNull { it.name == s } ?: SYSTEM
    }
}

/**
 * When to automatically push staged readings to Health Connect.
 *
 *  - [OFF]           — stays in staging until the user taps "Send".
 *  - [AFTER_SYNC]    — immediately after each successful BLE sync.
 *  - [EVERY_6H]/[EVERY_12H]/[DAILY] — WorkManager periodic push.
 */
enum class AutoPushMode(val label: String, val periodicHours: Int?) {
    OFF("Manual only", null),
    AFTER_SYNC("After each sync", null),
    EVERY_6H("Every 6 hours", 6),
    EVERY_12H("Every 12 hours", 12),
    DAILY("Daily", 24);

    val isPeriodic: Boolean get() = periodicHours != null

    companion object {
        fun fromName(s: String?) = entries.firstOrNull { it.name == s } ?: OFF
    }
}

data class UserPreferences(
    val unit: GlucoseUnit,
    /** General / unknown-context fallback range. */
    val targetLowMgDl: Double,
    val targetHighMgDl: Double,
    val autoPushMode: AutoPushMode,
    val chartMinMgDl: Double = 40.0,
    val chartMaxMgDl: Double = 200.0,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    // ADA-aligned defaults; user-adjustable per their condition.
    val fastingLowMgDl: Double = 80.0,
    val fastingHighMgDl: Double = 130.0,
    val preMealLowMgDl: Double = 80.0,
    val preMealHighMgDl: Double = 130.0,
    val postMealLowMgDl: Double = 80.0,
    val postMealHighMgDl: Double = 180.0,
    /** Amber "warning" cushion around each target range; set to 0 for strict red/green. */
    val warningBufferMgDl: Double = 0.0,
)

class Preferences(private val context: Context) {
    private val useMmolKey = booleanPreferencesKey("use_mmol")
    private val targetLowKey = doublePreferencesKey("target_low_mgdl")
    private val targetHighKey = doublePreferencesKey("target_high_mgdl")
    private val autoPushKey = stringPreferencesKey("auto_push_mode")
    private val chartMinKey = doublePreferencesKey("chart_min_mgdl")
    private val chartMaxKey = doublePreferencesKey("chart_max_mgdl")
    private val themeKey = stringPreferencesKey("theme_mode")
    private val fastingLowKey = doublePreferencesKey("fasting_low_mgdl")
    private val fastingHighKey = doublePreferencesKey("fasting_high_mgdl")
    private val preMealLowKey = doublePreferencesKey("pre_meal_low_mgdl")
    private val preMealHighKey = doublePreferencesKey("pre_meal_high_mgdl")
    private val postMealLowKey = doublePreferencesKey("post_meal_low_mgdl")
    private val postMealHighKey = doublePreferencesKey("post_meal_high_mgdl")
    private val warningBufferKey = doublePreferencesKey("warning_buffer_mgdl")

    val flow: Flow<UserPreferences> = context.prefsStore.data.map { p ->
        UserPreferences(
            unit = if (p[useMmolKey] == true) GlucoseUnit.MMOL_PER_L else GlucoseUnit.MG_PER_DL,
            targetLowMgDl = p[targetLowKey] ?: 70.0,
            targetHighMgDl = p[targetHighKey] ?: 140.0,
            autoPushMode = AutoPushMode.fromName(p[autoPushKey]),
            chartMinMgDl = p[chartMinKey] ?: 40.0,
            chartMaxMgDl = p[chartMaxKey] ?: 200.0,
            themeMode = ThemeMode.fromName(p[themeKey]),
            fastingLowMgDl = p[fastingLowKey] ?: 80.0,
            fastingHighMgDl = p[fastingHighKey] ?: 130.0,
            preMealLowMgDl = p[preMealLowKey] ?: 80.0,
            preMealHighMgDl = p[preMealHighKey] ?: 130.0,
            postMealLowMgDl = p[postMealLowKey] ?: 80.0,
            postMealHighMgDl = p[postMealHighKey] ?: 180.0,
            warningBufferMgDl = p[warningBufferKey] ?: 0.0,
        )
    }

    suspend fun setWarningBuffer(buffer: Double) {
        context.prefsStore.edit {
            it[warningBufferKey] = buffer.coerceIn(0.0, 50.0)
        }
    }

    suspend fun setFastingRange(low: Double, high: Double) {
        val (l, h) = orderedRange(low, high, lowMin = 40.0, lowMax = 200.0, highMin = 80.0, highMax = 300.0)
        context.prefsStore.edit {
            it[fastingLowKey] = l
            it[fastingHighKey] = h
        }
    }
    suspend fun setPreMealRange(low: Double, high: Double) {
        val (l, h) = orderedRange(low, high, lowMin = 40.0, lowMax = 200.0, highMin = 80.0, highMax = 300.0)
        context.prefsStore.edit {
            it[preMealLowKey] = l
            it[preMealHighKey] = h
        }
    }
    suspend fun setPostMealRange(low: Double, high: Double) {
        val (l, h) = orderedRange(low, high, lowMin = 40.0, lowMax = 250.0, highMin = 100.0, highMax = 400.0)
        context.prefsStore.edit {
            it[postMealLowKey] = l
            it[postMealHighKey] = h
        }
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.prefsStore.edit { it[themeKey] = mode.name }
    }

    suspend fun setUnit(unit: GlucoseUnit) {
        context.prefsStore.edit { it[useMmolKey] = (unit == GlucoseUnit.MMOL_PER_L) }
    }

    suspend fun setTargetRange(lowMgDl: Double, highMgDl: Double) {
        val (l, h) = orderedRange(lowMgDl, highMgDl, lowMin = 40.0, lowMax = 200.0, highMin = 100.0, highMax = 400.0)
        context.prefsStore.edit {
            it[targetLowKey] = l
            it[targetHighKey] = h
        }
    }

    suspend fun setChartRange(minMgDl: Double, maxMgDl: Double) {
        context.prefsStore.edit {
            it[chartMinKey] = minMgDl.coerceIn(0.0, 400.0)
            it[chartMaxKey] = maxMgDl.coerceIn(100.0, 600.0)
        }
    }

    suspend fun setAutoPushMode(mode: AutoPushMode) {
        context.prefsStore.edit { it[autoPushKey] = mode.name }
    }

    /**
     * Coerce both bounds into their slider ranges, then guarantee `low < high`
     * so the UI can't persist a degenerate range (e.g. low=150, high=100) via a
     * glitchy slider drag or programmatic set.
     */
    private fun orderedRange(
        low: Double, high: Double,
        lowMin: Double, lowMax: Double,
        highMin: Double, highMax: Double,
    ): Pair<Double, Double> {
        val l = low.coerceIn(lowMin, lowMax)
        val h = high.coerceIn(highMin, highMax)
        return if (l < h) l to h else l to (l + 1.0).coerceAtMost(highMax)
    }
}

/**
 * Returns the low/high mg/dL target band for a reading based on its meal
 * relation. Unknown/general readings fall back to the general [targetLowMgDl]
 * and [targetHighMgDl] pair.
 */
fun UserPreferences.targetRangeFor(mealRelation: Int): Pair<Double, Double> = when (mealRelation) {
    androidx.health.connect.client.records.BloodGlucoseRecord.RELATION_TO_MEAL_FASTING ->
        fastingLowMgDl to fastingHighMgDl
    androidx.health.connect.client.records.BloodGlucoseRecord.RELATION_TO_MEAL_BEFORE_MEAL ->
        preMealLowMgDl to preMealHighMgDl
    androidx.health.connect.client.records.BloodGlucoseRecord.RELATION_TO_MEAL_AFTER_MEAL ->
        postMealLowMgDl to postMealHighMgDl
    else -> targetLowMgDl to targetHighMgDl
}
