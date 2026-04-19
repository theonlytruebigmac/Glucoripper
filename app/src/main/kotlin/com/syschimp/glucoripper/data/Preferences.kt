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
    val targetLowMgDl: Double,
    val targetHighMgDl: Double,
    val autoPushMode: AutoPushMode,
    val chartMinMgDl: Double = 40.0,
    val chartMaxMgDl: Double = 200.0,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
)

class Preferences(private val context: Context) {
    private val useMmolKey = booleanPreferencesKey("use_mmol")
    private val targetLowKey = doublePreferencesKey("target_low_mgdl")
    private val targetHighKey = doublePreferencesKey("target_high_mgdl")
    private val autoPushKey = stringPreferencesKey("auto_push_mode")
    private val chartMinKey = doublePreferencesKey("chart_min_mgdl")
    private val chartMaxKey = doublePreferencesKey("chart_max_mgdl")
    private val themeKey = stringPreferencesKey("theme_mode")

    val flow: Flow<UserPreferences> = context.prefsStore.data.map { p ->
        UserPreferences(
            unit = if (p[useMmolKey] == true) GlucoseUnit.MMOL_PER_L else GlucoseUnit.MG_PER_DL,
            targetLowMgDl = p[targetLowKey] ?: 70.0,
            targetHighMgDl = p[targetHighKey] ?: 140.0,
            autoPushMode = AutoPushMode.fromName(p[autoPushKey]),
            chartMinMgDl = p[chartMinKey] ?: 40.0,
            chartMaxMgDl = p[chartMaxKey] ?: 200.0,
            themeMode = ThemeMode.fromName(p[themeKey]),
        )
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.prefsStore.edit { it[themeKey] = mode.name }
    }

    suspend fun setUnit(unit: GlucoseUnit) {
        context.prefsStore.edit { it[useMmolKey] = (unit == GlucoseUnit.MMOL_PER_L) }
    }

    suspend fun setTargetRange(lowMgDl: Double, highMgDl: Double) {
        context.prefsStore.edit {
            it[targetLowKey] = lowMgDl.coerceIn(40.0, 200.0)
            it[targetHighKey] = highMgDl.coerceIn(100.0, 400.0)
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
}

/** 1 mmol/L of glucose ≈ 18.0156 mg/dL. */
const val MGDL_PER_MMOL = 18.0156

fun Double.mgDlToMmol(): Double = this / MGDL_PER_MMOL
fun Double.mmolToMgDl(): Double = this * MGDL_PER_MMOL
