package com.syschimp.glucoripper.wear.tile

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import com.syschimp.glucoripper.wear.data.GlucosePayload
import com.syschimp.glucoripper.wear.data.GlucoseUnit
import java.io.ByteArrayOutputStream
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Draws the same ring-gauge the main `:wear` app shows in [NowScreen], but into
 * a [Bitmap] so it can be surfaced as an inline image in the protolayout tile.
 * Tiles can't host Compose, so the drawing is ported to [android.graphics.Canvas].
 */
object TileGaugeRenderer {

    private const val GAUGE_MIN = 40f
    private const val GAUGE_MAX = 300f
    private const val START_ANGLE = 135f
    private const val TOTAL_SWEEP = 270f

    private const val TRACK_COLOR = 0xFF2A3034.toInt()
    private const val LOW_COLOR = 0xFFE5484D.toInt()
    private const val IN_RANGE_COLOR = 0xFF30A46C.toInt()
    private const val ELEVATED_COLOR = 0xFFF5A524.toInt()
    private const val HIGH_COLOR = 0xFFE5484D.toInt()
    private const val TEXT_COLOR = 0xFFE1E3E6.toInt()
    private const val TEXT_DIM = 0xB3E1E3E6.toInt()

    fun renderPng(payload: GlucosePayload, sizePx: Int): ByteArray {
        val bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        draw(canvas, payload, sizePx.toFloat())
        val out = ByteArrayOutputStream()
        bmp.compress(Bitmap.CompressFormat.PNG, 100, out)
        bmp.recycle()
        return out.toByteArray()
    }

    private fun draw(canvas: Canvas, payload: GlucosePayload, size: Float) {
        val (low, high) = payload.targetRangeFor(payload.latestMealRelation)
        val stroke = size * 0.067f
        val inset = stroke / 2f + size * 0.027f
        val rect = RectF(inset, inset, size - inset, size - inset)

        val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = stroke
            strokeCap = Paint.Cap.ROUND
            color = TRACK_COLOR
        }
        canvas.drawArc(rect, START_ANGLE, TOTAL_SWEEP, false, trackPaint)

        val segPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = stroke
            strokeCap = Paint.Cap.BUTT
        }
        val segments = listOf(
            Triple(GAUGE_MIN, low.toFloat(), LOW_COLOR),
            Triple(low.toFloat(), high.toFloat(), IN_RANGE_COLOR),
            Triple(high.toFloat(), min(180f, GAUGE_MAX), ELEVATED_COLOR),
            Triple(min(180f, GAUGE_MAX), GAUGE_MAX, HIGH_COLOR),
        )
        segments.forEach { (a, b, col) ->
            val f0 = fractionOf(a)
            val f1 = fractionOf(b)
            if (f1 <= f0) return@forEach
            segPaint.color = col
            canvas.drawArc(
                rect,
                START_ANGLE + TOTAL_SWEEP * f0,
                TOTAL_SWEEP * (f1 - f0),
                false,
                segPaint,
            )
        }

        val pointerFraction = fractionOf(payload.latestMgDl.toFloat())
        drawPointer(canvas, size, rect, stroke, pointerFraction)
        drawCenter(canvas, payload, low, high, size)
    }

    private fun drawPointer(
        canvas: Canvas,
        size: Float,
        rect: RectF,
        stroke: Float,
        fraction: Float,
    ) {
        val angleDeg = START_ANGLE + TOTAL_SWEEP * fraction
        val angleRad = Math.toRadians(angleDeg.toDouble())
        val cx = size / 2f
        val cy = size / 2f
        val radius = (rect.width() / 2f) + stroke / 2f + size * 0.007f
        val tipX = cx + radius * cos(angleRad).toFloat()
        val tipY = cy + radius * sin(angleRad).toFloat()
        val baseRadius = radius + size * 0.047f
        val spread = 0.12f
        val b1x = cx + baseRadius * cos(angleRad + spread).toFloat()
        val b1y = cy + baseRadius * sin(angleRad + spread).toFloat()
        val b2x = cx + baseRadius * cos(angleRad - spread).toFloat()
        val b2y = cy + baseRadius * sin(angleRad - spread).toFloat()

        val path = Path().apply {
            moveTo(tipX, tipY)
            lineTo(b1x, b1y)
            lineTo(b2x, b2y)
            close()
        }
        val pointerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = TEXT_COLOR
        }
        canvas.drawPath(path, pointerPaint)
    }

    private fun drawCenter(
        canvas: Canvas,
        payload: GlucosePayload,
        low: Double,
        high: Double,
        size: Float,
    ) {
        val cx = size / 2f
        val unitPaint = textPaint(size * 0.063f, TEXT_DIM, bold = false)
        val valuePaint = textPaint(size * 0.22f, TEXT_COLOR, bold = true)
        val bandPaint = textPaint(size * 0.073f, bandColor(payload.latestMgDl, low, high), bold = true)

        val unitText = "Current (${unitLabel(payload.unit)})"
        val valueText = formatValue(payload.latestMgDl, payload.unit)
        val bandText = bandLabel(payload.latestMgDl, low, high)

        val unitFm = unitPaint.fontMetrics
        val valueFm = valuePaint.fontMetrics
        val bandFm = bandPaint.fontMetrics
        val unitH = unitFm.descent - unitFm.ascent
        val valueH = valueFm.descent - valueFm.ascent
        val bandH = bandFm.descent - bandFm.ascent
        val gap = size * 0.008f
        val totalH = unitH + valueH + bandH + gap * 2f

        var top = (size - totalH) / 2f
        canvas.drawText(unitText, cx, top - unitFm.ascent, unitPaint)
        top += unitH + gap
        canvas.drawText(valueText, cx, top - valueFm.ascent, valuePaint)
        top += valueH + gap
        canvas.drawText(bandText, cx, top - bandFm.ascent, bandPaint)
    }

    private fun textPaint(sizePx: Float, color: Int, bold: Boolean): Paint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            this.textSize = sizePx
            this.textAlign = Paint.Align.CENTER
            this.typeface = Typeface.create(
                Typeface.DEFAULT,
                if (bold) Typeface.BOLD else Typeface.NORMAL,
            )
        }

    private fun fractionOf(v: Float): Float =
        ((v - GAUGE_MIN) / (GAUGE_MAX - GAUGE_MIN)).coerceIn(0f, 1f)

    private fun bandColor(mgDl: Double, low: Double, high: Double): Int = when {
        mgDl <= 0 -> Color.GRAY
        mgDl < low -> LOW_COLOR
        mgDl <= high -> IN_RANGE_COLOR
        mgDl < 180 -> ELEVATED_COLOR
        else -> HIGH_COLOR
    }

    private fun bandLabel(mgDl: Double, low: Double, high: Double): String = when {
        mgDl <= 0 -> "—"
        mgDl < low -> "Low"
        mgDl <= high -> "In range"
        mgDl < 180 -> "Elevated"
        else -> "High"
    }

    private fun formatValue(mgDl: Double, unit: GlucoseUnit): String = when (unit) {
        GlucoseUnit.MG_PER_DL -> "%.0f".format(mgDl)
        GlucoseUnit.MMOL_PER_L -> "%.1f".format(mgDl / 18.0)
    }

    private fun unitLabel(unit: GlucoseUnit): String = when (unit) {
        GlucoseUnit.MG_PER_DL -> "mg/dL"
        GlucoseUnit.MMOL_PER_L -> "mmol/L"
    }
}
