package com.syschimp.glucoripper.wear.tile

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import com.syschimp.glucoripper.shared.MGDL_PER_MMOL
import com.syschimp.glucoripper.shared.glucoseHighAlarmCutoff
import com.syschimp.glucoripper.wear.data.GlucosePayload
import com.syschimp.glucoripper.wear.data.GlucoseUnit
import java.nio.ByteBuffer

/**
 * Re-Diary Tile renderer. Chart-forward layout — sparkline at the top, big mono
 * value in the middle, status pill at the bottom. Drawn into a [Bitmap] so the
 * protolayout tile can surface it as an inline image.
 *
 * Renders raw ARGB_8888 pixel bytes — protolayout's InlineImageResource does not
 * decode PNG/JPEG.
 */
object TileGaugeRenderer {

    // Re-Diary tokens (dark only — watch is OLED).
    private const val BG = 0xFF0B0D0F.toInt()
    private const val FG = 0xFFECEEF0.toInt()
    private const val FG_MUTED = 0xFFA0A6AB.toInt()
    private const val FG_SUBTLE = 0xFF6B7177.toInt()
    private const val FG_FAINT = 0xFF3F454A.toInt()
    private const val ELEV_2 = 0xFF1B1F22.toInt()

    private const val LOW_COLOR = 0xFFE5484D.toInt()
    private const val IN_RANGE_COLOR = 0xFF30A46C.toInt()
    private const val ELEVATED_COLOR = 0xFFF5A524.toInt()
    private const val HIGH_COLOR = 0xFFE5484D.toInt()

    fun renderArgb8888(payload: GlucosePayload, sizePx: Int): ByteArray {
        val bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        draw(canvas, payload, sizePx.toFloat())
        val buf = ByteBuffer.allocate(bmp.byteCount)
        bmp.copyPixelsToBuffer(buf)
        bmp.recycle()
        return buf.array()
    }

    private fun draw(canvas: Canvas, payload: GlucosePayload, size: Float) {
        val (low, high) = payload.targetRangeFor(payload.latestMealRelation)
        val highAlarm = glucoseHighAlarmCutoff(high)
        val band = bandColor(payload.latestMgDl, low, high, highAlarm)
        val bandLabel = bandLabel(payload.latestMgDl, low, high, highAlarm)

        // Overline: GLUCOSE · 4M AGO
        val overlineY = size * 0.18f
        val overlinePaint = textPaint(size * 0.038f, FG_MUTED, bold = true).apply {
            letterSpacing = 0.16f
        }
        canvas.drawText(
            "GLUCOSE",
            size / 2f,
            overlineY,
            overlinePaint,
        )

        // Sparkline area
        val chartTop = size * 0.24f
        val chartBottom = size * 0.50f
        val chartPadX = size * 0.12f
        drawSparkline(canvas, payload, low, high, band,
            left = chartPadX, right = size - chartPadX,
            top = chartTop, bottom = chartBottom,
            density = size,
        )

        // Big mono numeric + unit
        val valueText = formatValue(payload.latestMgDl, payload.unit)
        val valuePaint = monoPaint(size * 0.20f, FG, bold = true).apply {
            letterSpacing = -0.02f
        }
        val unitPaint = textPaint(size * 0.045f, FG_SUBTLE, bold = false)
        val valueY = size * 0.71f
        val valueWidth = valuePaint.measureText(valueText)
        val unitText = unitLabel(payload.unit)
        val unitWidth = unitPaint.measureText(unitText)
        val totalW = valueWidth + unitWidth + size * 0.025f
        val valueX = (size - totalW) / 2f + valueWidth / 2f
        valuePaint.textAlign = Paint.Align.CENTER
        canvas.drawText(valueText, valueX, valueY, valuePaint)
        unitPaint.textAlign = Paint.Align.LEFT
        canvas.drawText(
            unitText,
            valueX + valueWidth / 2f + size * 0.018f,
            valueY,
            unitPaint,
        )

        // Status pill
        val pillH = size * 0.067f
        val pillPaddingX = size * 0.044f
        val pillPaint = textPaint(size * 0.038f, band, bold = true).apply {
            letterSpacing = 0.12f
        }
        val pillLabel = bandLabel.uppercase()
        val pillW = pillPaint.measureText(pillLabel) + pillPaddingX * 2
        val pillX = (size - pillW) / 2f
        val pillY = size * 0.82f
        val pillBg = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = mixWith(band, BG, 0.16f)
        }
        canvas.drawRoundRect(
            RectF(pillX, pillY, pillX + pillW, pillY + pillH),
            pillH / 2f, pillH / 2f, pillBg,
        )
        canvas.drawText(
            pillLabel,
            size / 2f,
            pillY + pillH / 2f - (pillPaint.fontMetrics.ascent + pillPaint.fontMetrics.descent) / 2f,
            pillPaint,
        )
    }

    private fun drawSparkline(
        canvas: Canvas,
        payload: GlucosePayload,
        low: Double,
        high: Double,
        band: Int,
        left: Float,
        right: Float,
        top: Float,
        bottom: Float,
        density: Float,
    ) {
        val n = minOf(payload.windowTimesMillis.size, payload.windowMgDls.size)
        if (n < 2) return
        // Take last 4h
        val lastT = payload.windowTimesMillis[n - 1]
        val cutoff = lastT - 4L * 60L * 60L * 1000L
        val idxs = (0 until n).filter { payload.windowTimesMillis[it] >= cutoff }
        if (idxs.size < 2) return
        val ts = idxs.map { payload.windowTimesMillis[it].toFloat() }
        val mgs = idxs.map { payload.windowMgDls[it] }
        val mn = (mgs.min().toDouble().coerceAtMost(low - 8.0)).toFloat()
        val mx = (mgs.max().toDouble().coerceAtLeast(high + 8.0)).toFloat()
        val ySpan = (mx - mn).coerceAtLeast(1f)
        val xSpan = (ts.last() - ts.first()).coerceAtLeast(1f)
        fun xOf(t: Float): Float = left + ((t - ts.first()) / xSpan) * (right - left)
        fun yOf(v: Float): Float = top + (1f - (v - mn) / ySpan) * (bottom - top)
        val xs = ts.map(::xOf)
        val ys = mgs.map(::yOf)

        // Target band tint
        val bandPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = (FG and 0x00FFFFFF) or 0x0F000000
        }
        canvas.drawRect(
            left, yOf(high.toFloat()),
            right, yOf(low.toFloat()),
            bandPaint,
        )
        // Dashed bounds
        val dashPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = FG_FAINT
            style = Paint.Style.STROKE
            strokeWidth = 0.6f * density / 200f
            pathEffect = DashPathEffect(floatArrayOf(2f, 3f), 0f)
        }
        canvas.drawLine(left, yOf(low.toFloat()), right, yOf(low.toFloat()), dashPaint)
        canvas.drawLine(left, yOf(high.toFloat()), right, yOf(high.toFloat()), dashPaint)

        // Smooth bezier
        val path = Path().apply {
            moveTo(xs[0], ys[0])
            for (i in 1 until xs.size) {
                val cx = (xs[i - 1] + xs[i]) / 2f
                cubicTo(cx, ys[i - 1], cx, ys[i], xs[i], ys[i])
            }
        }
        val curvePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = FG
            style = Paint.Style.STROKE
            strokeWidth = density * 0.005f
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        canvas.drawPath(path, curvePaint)

        // Last sample dot
        val dotR = density * 0.011f
        val cx = xs.last()
        val cy = ys.last()
        val dotBg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = BG }
        val dotStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = band
            style = Paint.Style.STROKE
            strokeWidth = density * 0.005f
        }
        canvas.drawCircle(cx, cy, dotR, dotBg)
        canvas.drawCircle(cx, cy, dotR, dotStroke)
    }

    private fun textPaint(sizePx: Float, color: Int, bold: Boolean): Paint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            textSize = sizePx
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(
                Typeface.DEFAULT,
                if (bold) Typeface.BOLD else Typeface.NORMAL,
            )
        }

    private fun monoPaint(sizePx: Float, color: Int, bold: Boolean): Paint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            textSize = sizePx
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(
                Typeface.MONOSPACE,
                if (bold) Typeface.BOLD else Typeface.NORMAL,
            )
        }

    private fun bandColor(mgDl: Double, low: Double, high: Double, highAlarm: Double): Int = when {
        mgDl <= 0 -> FG_FAINT
        mgDl < low -> LOW_COLOR
        mgDl <= high -> IN_RANGE_COLOR
        mgDl < highAlarm -> ELEVATED_COLOR
        else -> HIGH_COLOR
    }

    private fun bandLabel(mgDl: Double, low: Double, high: Double, highAlarm: Double): String = when {
        mgDl <= 0 -> "—"
        mgDl < low -> "Low"
        mgDl <= high -> "In range"
        mgDl < highAlarm -> "Elevated"
        else -> "High"
    }

    private fun formatValue(mgDl: Double, unit: GlucoseUnit): String = when (unit) {
        GlucoseUnit.MG_PER_DL -> "%.0f".format(mgDl)
        GlucoseUnit.MMOL_PER_L -> "%.1f".format(mgDl / MGDL_PER_MMOL)
    }

    private fun unitLabel(unit: GlucoseUnit): String = when (unit) {
        GlucoseUnit.MG_PER_DL -> "mg/dL"
        GlucoseUnit.MMOL_PER_L -> "mmol/L"
    }

    /** Mix [a] and [b] in sRGB at fraction [f] (0..1 of a). */
    private fun mixWith(a: Int, b: Int, f: Float): Int {
        val ar = (a shr 16) and 0xFF
        val ag = (a shr 8) and 0xFF
        val ab = a and 0xFF
        val br = (b shr 16) and 0xFF
        val bg = (b shr 8) and 0xFF
        val bb = b and 0xFF
        val r = (ar * f + br * (1 - f)).toInt().coerceIn(0, 255)
        val g = (ag * f + bg * (1 - f)).toInt().coerceIn(0, 255)
        val bl = (ab * f + bb * (1 - f)).toInt().coerceIn(0, 255)
        return (0xFF shl 24) or (r shl 16) or (g shl 8) or bl
    }
}
