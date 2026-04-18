package com.syschimp.glucoripper.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.prefsStore by preferencesDataStore(name = "user_prefs")

enum class GlucoseUnit { MG_PER_DL, MMOL_PER_L }

data class UserPreferences(
    val unit: GlucoseUnit,
    val targetLowMgDl: Double,
    val targetHighMgDl: Double,
)

class Preferences(private val context: Context) {
    private val useMmolKey = booleanPreferencesKey("use_mmol")
    private val targetLowKey = doublePreferencesKey("target_low_mgdl")
    private val targetHighKey = doublePreferencesKey("target_high_mgdl")

    val flow: Flow<UserPreferences> = context.prefsStore.data.map { p ->
        UserPreferences(
            unit = if (p[useMmolKey] == true) GlucoseUnit.MMOL_PER_L else GlucoseUnit.MG_PER_DL,
            targetLowMgDl = p[targetLowKey] ?: 70.0,
            targetHighMgDl = p[targetHighKey] ?: 140.0,
        )
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
}

/** 1 mmol/L of glucose ≈ 18.0156 mg/dL. */
const val MGDL_PER_MMOL = 18.0156

fun Double.mgDlToMmol(): Double = this / MGDL_PER_MMOL
fun Double.mmolToMgDl(): Double = this * MGDL_PER_MMOL
