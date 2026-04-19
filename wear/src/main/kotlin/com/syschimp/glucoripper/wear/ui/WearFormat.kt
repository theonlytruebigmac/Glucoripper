package com.syschimp.glucoripper.wear.ui

import androidx.compose.ui.graphics.Color
import com.syschimp.glucoripper.wear.data.GlucosePayload
import com.syschimp.glucoripper.wear.data.GlucoseUnit
import java.time.Duration
import java.time.Instant

// Band colors mirror the phone app theme (ui/theme/Color.kt).
internal val GlucoseLow = Color(0xFFE5484D)
internal val GlucoseInRange = Color(0xFF30A46C)
internal val GlucoseElevated = Color(0xFFF5A524)
internal val GlucoseHigh = Color(0xFFE5484D)
internal val AccentTeal = Color(0xFF4FD8EB)

internal data class Band(val label: String, val color: Color)

internal fun classify(mgDl: Double, low: Double, high: Double): Band = when {
    mgDl <= 0.0 -> Band("—", Color.Gray)
    mgDl < low -> Band("Low", GlucoseLow)
    mgDl <= high -> Band("In range", GlucoseInRange)
    mgDl < 180.0 -> Band("Elevated", GlucoseElevated)
    else -> Band("High", GlucoseHigh)
}

internal fun formatGlucose(mgDl: Double, unit: GlucoseUnit): String = when (unit) {
    GlucoseUnit.MG_PER_DL -> "%.0f".format(mgDl)
    GlucoseUnit.MMOL_PER_L -> "%.1f".format(mgDl / 18.0)
}

internal fun unitLabel(unit: GlucoseUnit): String = when (unit) {
    GlucoseUnit.MG_PER_DL -> "mg/dL"
    GlucoseUnit.MMOL_PER_L -> "mmol/L"
}

internal fun mealLabel(relation: Int): String = when (relation) {
    GlucosePayload.RELATION_FASTING -> "Fasting"
    GlucosePayload.RELATION_BEFORE_MEAL -> "Before meal"
    GlucosePayload.RELATION_AFTER_MEAL -> "After meal"
    GlucosePayload.RELATION_GENERAL -> "General"
    else -> ""
}

internal fun relativeTime(t: Instant, now: Instant = Instant.now()): String {
    val d = Duration.between(t, now)
    return when {
        d.isNegative -> "just now"
        d.toMinutes() < 1 -> "just now"
        d.toMinutes() < 60 -> "${d.toMinutes()}m ago"
        d.toHours() < 24 -> "${d.toHours()}h ago"
        d.toDays() < 7 -> "${d.toDays()}d ago"
        else -> "> 1w ago"
    }
}

/**
 * Direction of change between the latest reading and the most recent reading at
 * least [minGapMinutes] before it. Mirrors the trend arrow used by the phone app
 * and the complication service. Returns null when the window is too thin.
 */
internal fun trendDelta(payload: GlucosePayload, minGapMinutes: Long = 15): Float? {
    val v = payload.windowMgDls
    val t = payload.windowTimesMillis
    if (v.size < 2) return null
    val latestIdx = v.size - 1
    val gapMs = minGapMinutes * 60_000L
    var priorIdx = -1
    for (i in latestIdx - 1 downTo 0) {
        if (t[latestIdx] - t[i] >= gapMs) {
            priorIdx = i
            break
        }
    }
    if (priorIdx < 0) return null
    return v[latestIdx] - v[priorIdx]
}
