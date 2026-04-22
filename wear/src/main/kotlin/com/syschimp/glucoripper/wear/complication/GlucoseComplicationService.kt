package com.syschimp.glucoripper.wear.complication

import android.app.PendingIntent
import android.content.ComponentName
import android.content.Intent
import android.graphics.drawable.Icon
import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.LongTextComplicationData
import androidx.wear.watchface.complications.data.MonochromaticImage
import androidx.wear.watchface.complications.data.PlainComplicationText
import androidx.wear.watchface.complications.data.RangedValueComplicationData
import androidx.wear.watchface.complications.data.ShortTextComplicationData
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import androidx.wear.watchface.complications.datasource.SuspendingComplicationDataSourceService
import com.syschimp.glucoripper.shared.MGDL_PER_MMOL
import com.syschimp.glucoripper.wear.MainActivity
import com.syschimp.glucoripper.wear.R
import com.syschimp.glucoripper.wear.data.GlucosePayload
import com.syschimp.glucoripper.wear.data.GlucoseStore
import com.syschimp.glucoripper.wear.data.GlucoseUnit
import com.syschimp.glucoripper.wear.ui.trendDelta
import kotlinx.coroutines.flow.first
import java.time.Duration
import java.time.Instant

/**
 * Publishes glucose data to watch faces via the ComplicationDataSource API.
 *
 * Supported surfaces:
 *  - SHORT_TEXT:  "138" (reading only)
 *  - RANGED_VALUE: arc showing current reading positioned within the target band
 *  - LONG_TEXT:   "138 mg/dL · In range"
 */
class GlucoseComplicationService : SuspendingComplicationDataSourceService() {

    override suspend fun onComplicationRequest(request: ComplicationRequest): ComplicationData? {
        val payload = runCatching { GlucoseStore(applicationContext).flow.first() }
            .getOrElse { return null }
        if (payload.latestTimeMillis == 0L) return placeholderFor(request.complicationType)
        return when (request.complicationType) {
            ComplicationType.SHORT_TEXT -> shortTextFor(payload)
            ComplicationType.LONG_TEXT -> longTextFor(payload)
            ComplicationType.RANGED_VALUE -> rangedValueFor(payload)
            else -> null
        }
    }

    override fun getPreviewData(type: ComplicationType): ComplicationData? = when (type) {
        ComplicationType.SHORT_TEXT -> shortTextFor(previewPayload())
        ComplicationType.LONG_TEXT -> longTextFor(previewPayload())
        ComplicationType.RANGED_VALUE -> rangedValueFor(previewPayload())
        else -> null
    }

    private fun previewPayload(): GlucosePayload = GlucosePayload.Empty.copy(
        latestTimeMillis = Instant.now().toEpochMilli(),
        latestMgDl = 118.0,
        lastSyncMillis = Instant.now().toEpochMilli(),
    )

    private fun shortTextFor(payload: GlucosePayload): ShortTextComplicationData {
        val arrow = trendArrow(payload)
        val text = formatValue(payload.latestMgDl, payload.unit) + arrow
        return ShortTextComplicationData.Builder(
            text = PlainComplicationText.Builder(text).build(),
            contentDescription = PlainComplicationText
                .Builder("Latest glucose $text ${unitLabel(payload.unit)}").build(),
        )
            .setTitle(PlainComplicationText.Builder(unitLabel(payload.unit)).build())
            .setMonochromaticImage(monochromaticIcon())
            .setTapAction(launchAppIntent())
            .build()
    }

    private fun longTextFor(payload: GlucosePayload): LongTextComplicationData {
        val value = formatValue(payload.latestMgDl, payload.unit)
        val (low, high) = payload.targetRangeFor(payload.latestMealRelation)
        val band = classifyLabel(payload.latestMgDl, low, high)
        val arrow = trendArrow(payload)
        val long = "$value ${unitLabel(payload.unit)} $arrow · $band".trim()
        return LongTextComplicationData.Builder(
            text = PlainComplicationText.Builder(long).build(),
            contentDescription = PlainComplicationText.Builder(long).build(),
        )
            .setTitle(PlainComplicationText.Builder("Glucose · ${relativeTime(payload.latestInstant)}").build())
            .setMonochromaticImage(monochromaticIcon())
            .setTapAction(launchAppIntent())
            .build()
    }

    private fun rangedValueFor(payload: GlucosePayload): RangedValueComplicationData {
        // Visualise the reading on an arc whose endpoints are 40 and 300 mg/dL —
        // wide enough to cover every realistic blood glucose value while keeping
        // the "in range" band meaningful and visible.
        val minV = 40f
        val maxV = 300f
        val value = payload.latestMgDl.toFloat().coerceIn(minV, maxV)
        val arrow = trendArrow(payload)
        val text = formatValue(payload.latestMgDl, payload.unit) + arrow
        val (low, high) = payload.targetRangeFor(payload.latestMealRelation)
        val band = classifyLabel(payload.latestMgDl, low, high)
        return RangedValueComplicationData.Builder(
            value = value,
            min = minV,
            max = maxV,
            contentDescription = PlainComplicationText
                .Builder("Latest glucose $text ${unitLabel(payload.unit)} — $band").build(),
        )
            .setText(PlainComplicationText.Builder(text).build())
            .setTitle(PlainComplicationText.Builder(unitLabel(payload.unit)).build())
            .setMonochromaticImage(monochromaticIcon())
            .setTapAction(launchAppIntent())
            .build()
    }

    private fun monochromaticIcon(): MonochromaticImage =
        MonochromaticImage.Builder(Icon.createWithResource(this, R.drawable.ic_glucose_drop))
            .build()

    private fun trendArrow(payload: GlucosePayload): String {
        val delta = trendDelta(payload) ?: return ""
        return when {
            delta > 15f -> "↗"
            delta < -15f -> "↘"
            else -> "→"
        }
    }

    private fun placeholderFor(type: ComplicationType): ComplicationData? {
        val placeholder = PlainComplicationText.Builder("—").build()
        val cd = PlainComplicationText.Builder("No reading yet").build()
        return when (type) {
            ComplicationType.SHORT_TEXT -> ShortTextComplicationData.Builder(placeholder, cd)
                .setTapAction(launchAppIntent()).build()
            ComplicationType.LONG_TEXT -> LongTextComplicationData.Builder(placeholder, cd)
                .setTapAction(launchAppIntent()).build()
            ComplicationType.RANGED_VALUE -> RangedValueComplicationData.Builder(
                value = 40f, min = 40f, max = 300f, contentDescription = cd,
            )
                .setText(placeholder)
                .setTapAction(launchAppIntent())
                .build()
            else -> null
        }
    }

    private fun launchAppIntent(): PendingIntent {
        val intent = Intent().apply {
            component = ComponentName(applicationContext, MainActivity::class.java)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        return PendingIntent.getActivity(
            applicationContext,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    private fun formatValue(mgDl: Double, unit: GlucoseUnit): String = when (unit) {
        GlucoseUnit.MG_PER_DL -> "%.0f".format(mgDl)
        GlucoseUnit.MMOL_PER_L -> "%.1f".format(mgDl / MGDL_PER_MMOL)
    }

    private fun unitLabel(unit: GlucoseUnit): String = when (unit) {
        GlucoseUnit.MG_PER_DL -> "mg/dL"
        GlucoseUnit.MMOL_PER_L -> "mmol/L"
    }

    private fun classifyLabel(mgDl: Double, low: Double, high: Double): String = when {
        mgDl < low -> "Low"
        mgDl <= high -> "In range"
        mgDl < 180 -> "Elevated"
        else -> "High"
    }

    private fun relativeTime(t: Instant): String {
        val d = Duration.between(t, Instant.now())
        return when {
            d.isNegative -> "now"
            d.toMinutes() < 1 -> "now"
            d.toMinutes() < 60 -> "${d.toMinutes()}m ago"
            d.toHours() < 24 -> "${d.toHours()}h ago"
            d.toDays() < 7 -> "${d.toDays()}d ago"
            else -> "> 1w"
        }
    }
}
