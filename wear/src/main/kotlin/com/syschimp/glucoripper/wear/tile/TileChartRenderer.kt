package com.syschimp.glucoripper.wear.tile

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import com.syschimp.glucoripper.wear.data.GlucosePayload
import java.io.ByteArrayOutputStream

/**
 * Renders the 24-hour glucose line chart as a PNG byte array suitable for
 * inline inclusion in a ProtoLayout tile resource.
 *
 * The tile can't use Compose or Canvas composables — everything must be
 * pre-rasterised on the service side and shipped as bytes. We intentionally
 * keep the drawing primitive (no gradients, no text) so the PNG compresses
 * tightly and the tile request stays well under the inline-resource budget.
 */
internal object TileChartRenderer {

    private const val BG_COLOR = 0xFF1A1F22.toInt()
    private const val BAND_COLOR = 0xFF30A46C.toInt()
    private const val LINE_COLOR = 0xFF4FD8EB.toInt()
    private const val DOT_COLOR = 0xFFE0F7FA.toInt()
    private const val AXIS_COLOR = 0x30FFFFFF.toInt()

    fun renderPng(payload: GlucosePayload, widthPx: Int, heightPx: Int): ByteArray {
        val bmp = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)

        // Rounded background
        val bg = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = BG_COLOR
            style = Paint.Style.FILL
        }
        val radius = 14f
        canvas.drawRoundRect(
            RectF(0f, 0f, widthPx.toFloat(), heightPx.toFloat()),
            radius, radius, bg,
        )

        val values = payload.windowMgDls
        val times = payload.windowTimesMillis
        if (values.size < 2) {
            return bmp.toPng()
        }

        val low = payload.targetLowMgDl.toFloat()
        val high = payload.targetHighMgDl.toFloat()
        val yMin = minOf(values.min(), low - 20f).coerceAtLeast(40f)
        val yMax = maxOf(values.max(), high + 20f).coerceAtMost(400f)
        val yRange = (yMax - yMin).coerceAtLeast(1f)

        val pad = 10f
        val left = pad
        val right = widthPx - pad
        val top = pad
        val bottom = heightPx - pad
        val w = right - left
        val h = bottom - top

        fun yFor(v: Float) = bottom - ((v - yMin) / yRange) * h
        val minT = times.first()
        val maxT = times.last()
        val tSpan = (maxT - minT).coerceAtLeast(1L).toFloat()
        fun xFor(t: Long) = left + ((t - minT).toFloat() / tSpan) * w

        // Target band
        val bandPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = BAND_COLOR
            alpha = (255 * 0.18f).toInt()
            style = Paint.Style.FILL
        }
        canvas.drawRect(left, yFor(high), right, yFor(low), bandPaint)

        // Target band edges
        val edgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = BAND_COLOR
            alpha = (255 * 0.45f).toInt()
            style = Paint.Style.STROKE
            strokeWidth = 1f
        }
        canvas.drawLine(left, yFor(high), right, yFor(high), edgePaint)
        canvas.drawLine(left, yFor(low), right, yFor(low), edgePaint)

        // Hour gridlines every 6 hours (three interior lines)
        val axisPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = AXIS_COLOR
            style = Paint.Style.STROKE
            strokeWidth = 1f
        }
        for (i in 1..3) {
            val x = left + w * i / 4f
            canvas.drawLine(x, top, x, bottom, axisPaint)
        }

        // Line path
        val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = LINE_COLOR
            style = Paint.Style.STROKE
            strokeWidth = 3f
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        val path = Path()
        values.forEachIndexed { i, v ->
            val x = xFor(times[i])
            val y = yFor(v)
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        canvas.drawPath(path, linePaint)

        // Terminal dot with halo so the "latest point" pops
        val lx = xFor(times.last())
        val ly = yFor(values.last())
        val haloPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = LINE_COLOR
            alpha = (255 * 0.35f).toInt()
            style = Paint.Style.FILL
        }
        canvas.drawCircle(lx, ly, 7f, haloPaint)
        val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = DOT_COLOR
            style = Paint.Style.FILL
        }
        canvas.drawCircle(lx, ly, 4f, dotPaint)

        return bmp.toPng()
    }

    private fun Bitmap.toPng(): ByteArray {
        val out = ByteArrayOutputStream()
        compress(Bitmap.CompressFormat.PNG, 100, out)
        return out.toByteArray()
    }
}
