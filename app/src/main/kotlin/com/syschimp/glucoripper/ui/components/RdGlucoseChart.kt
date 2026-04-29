package com.syschimp.glucoripper.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.syschimp.glucoripper.ui.theme.GlucoseInRange
import com.syschimp.glucoripper.ui.theme.GlucoseLow
import com.syschimp.glucoripper.ui.theme.JetBrainsMono

/**
 * One sample point on the glucose curve.
 *
 * @param hour 0..rangeHours position along the x-axis (0 = oldest, rangeHours = now)
 * @param mgDl glucose value in mg/dL
 */
data class GlucoseSample(val hour: Float, val mgDl: Float)

/**
 * Big SVG-style glucose chart from the Re-Diary redesign.
 *
 * Features matched from the design:
 *  - horizontal gridlines at 50/100/150/200 with mono labels
 *  - target band between [low, high] tinted green, with dashed bounding lines
 *  - smooth bezier curve through the samples
 *  - filled area beneath the curve
 *  - line-draw animation on first composition (1.1s ease-out)
 *  - min/max markers with mono labels
 *  - pulsing dot at current sample (current = last)
 *  - tap/drag to scrub: dashed crosshair + dot + callout box with mg/dL and time
 */
@Composable
fun RdGlucoseChart(
    samples: List<GlucoseSample>,
    lowMgDl: Float,
    highMgDl: Float,
    rangeHours: Float,
    yMin: Float = 50f,
    yMax: Float = 200f,
    unitLabel: String = "mg/dL",
    height: Dp = 220.dp,
    modifier: Modifier = Modifier,
) {
    if (samples.size < 2) return
    val density = LocalDensity.current
    val tm = rememberTextMeasurer()

    val fg = MaterialTheme.colorScheme.onSurface
    val bg = MaterialTheme.colorScheme.surface
    val subtle = MaterialTheme.colorScheme.onSurfaceVariant
    val faint = MaterialTheme.colorScheme.outlineVariant
    val accent = MaterialTheme.colorScheme.onSurface
    val accentOn = MaterialTheme.colorScheme.surface
    val targetBand = GlucoseInRange.copy(alpha = 0.07f)
    val targetEdge = GlucoseInRange.copy(alpha = 0.35f)

    // Scrub state: { x, y, mg, h } | null
    var scrub by remember { mutableStateOf<ScrubState?>(null) }

    // Line-draw animation 0..1
    var animTarget by remember { mutableStateOf(0f) }
    LaunchedEffect(Unit) { animTarget = 1f }
    val drawProgress by animateFloatAsState(
        targetValue = animTarget,
        animationSpec = tween(durationMillis = 1100, easing = LinearEasing),
        label = "rdChartDraw",
    )

    // Pulse animation for current dot — radius oscillates 10..18
    val pulse = rememberInfiniteTransition(label = "rdChartPulse")
    val pulseR by pulse.animateFloat(
        initialValue = 10f,
        targetValue = 18f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "rdChartPulseR",
    )
    val pulseAlpha by pulse.animateFloat(
        initialValue = 0.22f,
        targetValue = 0.04f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "rdChartPulseA",
    )

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .pointerInput(samples) {
                detectTapGestures(
                    onTap = { offset ->
                        scrub = nearestSample(offset.x, size.width.toFloat(), size.height.toFloat(),
                            samples, rangeHours, yMin, yMax)
                    },
                    onPress = { /* noop — release will be handled by the drag */ },
                )
            }
            .pointerInput(samples) {
                detectDragGestures(
                    onDragStart = { offset ->
                        scrub = nearestSample(offset.x, size.width.toFloat(), size.height.toFloat(),
                            samples, rangeHours, yMin, yMax)
                    },
                    onDrag = { change, _ ->
                        scrub = nearestSample(change.position.x, size.width.toFloat(), size.height.toFloat(),
                            samples, rangeHours, yMin, yMax)
                        change.consume()
                    },
                    onDragEnd = { scrub = null },
                    onDragCancel = { scrub = null },
                )
            },
    ) {
        val w = size.width
        val h = size.height
        val padX = 0f
        val padY = with(density) { 24.dp.toPx() }

        // Project samples to screen coords
        val xs = FloatArray(samples.size) {
            padX + (samples[it].hour / rangeHours) * (w - padX * 2)
        }
        val ys = FloatArray(samples.size) {
            padY + ((yMax - samples[it].mgDl) / (yMax - yMin)) * (h - padY * 2)
        }

        // Gridlines + y-axis labels
        listOf(50, 100, 150, 200).forEach { v ->
            val gy = padY + ((yMax - v) / (yMax - yMin)) * (h - padY * 2)
            drawLine(
                color = faint,
                start = Offset(0f, gy),
                end = Offset(w, gy),
                strokeWidth = 1f,
            )
            drawText(
                textMeasurer = tm,
                text = v.toString(),
                topLeft = Offset(6f, gy - with(density) { 12.dp.toPx() }),
                style = TextStyle(
                    color = subtle,
                    fontFamily = JetBrainsMono,
                    fontSize = 9.sp,
                ),
            )
        }

        // Target band (green tint between low and high)
        val yLow = padY + ((yMax - lowMgDl) / (yMax - yMin)) * (h - padY * 2)
        val yHigh = padY + ((yMax - highMgDl) / (yMax - yMin)) * (h - padY * 2)
        drawRect(
            color = targetBand,
            topLeft = Offset(0f, yHigh),
            size = androidx.compose.ui.geometry.Size(w, yLow - yHigh),
        )
        // Dashed bounds at low and high
        val dashEffect = PathEffect.dashPathEffect(floatArrayOf(2f, 4f), 0f)
        drawLine(
            color = targetEdge,
            start = Offset(0f, yHigh),
            end = Offset(w, yHigh),
            strokeWidth = 1f,
            pathEffect = dashEffect,
        )
        drawLine(
            color = targetEdge,
            start = Offset(0f, yLow),
            end = Offset(w, yLow),
            strokeWidth = 1f,
            pathEffect = dashEffect,
        )

        // Build the smooth path
        val linePath = buildSmoothPath(xs, ys, closeAtBottom = false, h)
        val areaPath = buildSmoothPath(xs, ys, closeAtBottom = true, h)

        // Filled area (fades in with the line)
        val areaAlpha = (drawProgress - 0.6f).coerceAtLeast(0f) / 0.4f
        if (areaAlpha > 0f) {
            drawPath(
                path = areaPath,
                color = fg.copy(alpha = 0.08f * areaAlpha),
            )
        }

        // Animated stroke — clip to drawProgress portion of total length
        val measuredPath = Path()
        val pm = PathMeasure().apply { setPath(linePath, false) }
        val totalLen = pm.length
        if (totalLen > 0f) {
            pm.getSegment(0f, totalLen * drawProgress, measuredPath, true)
            drawPath(
                path = measuredPath,
                color = fg,
                style = Stroke(
                    width = 1.5f * density.density,
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round,
                ),
            )
        }

        // After draw: min/max markers (skip if scrubbing)
        if (drawProgress >= 1f && scrub == null && samples.size >= 2) {
            val minIdx = samples.indices.minBy { samples[it].mgDl }
            val maxIdx = samples.indices.maxBy { samples[it].mgDl }
            drawMarker(
                tm = tm, density = density,
                cx = xs[minIdx], cy = ys[minIdx],
                label = "min ${samples[minIdx].mgDl.toInt()}",
                anchor = MarkerAnchor.Top,
                fillColor = bg, strokeColor = subtle, textColor = subtle,
            )
            drawMarker(
                tm = tm, density = density,
                cx = xs[maxIdx], cy = ys[maxIdx],
                label = "max ${samples[maxIdx].mgDl.toInt()}",
                anchor = MarkerAnchor.Bottom,
                fillColor = bg, strokeColor = subtle, textColor = subtle,
            )
        }

        // Current point — pulsing dot at the last sample
        if (drawProgress >= 1f && scrub == null) {
            val cx = xs.last()
            val cy = ys.last()
            drawCircle(
                color = GlucoseLow.copy(alpha = pulseAlpha),
                radius = pulseR,
                center = Offset(cx, cy),
            )
            drawCircle(
                color = bg,
                radius = 5f,
                center = Offset(cx, cy),
            )
            drawCircle(
                color = GlucoseLow,
                radius = 5f,
                center = Offset(cx, cy),
                style = Stroke(width = 2f),
            )
        }

        // Scrub overlay
        scrub?.let { s ->
            drawLine(
                color = fg.copy(alpha = 0.4f),
                start = Offset(s.x, padY),
                end = Offset(s.x, h - padY),
                strokeWidth = 1f,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(2f, 3f), 0f),
            )
            drawCircle(
                color = bg,
                radius = 5f,
                center = Offset(s.x, s.y),
            )
            drawCircle(
                color = fg,
                radius = 5f,
                center = Offset(s.x, s.y),
                style = Stroke(width = 2f),
            )
            // Callout box
            val cw = with(density) { 86.dp.toPx() }
            val ch = with(density) { 38.dp.toPx() }
            val cxClamped = (s.x).coerceIn(cw / 2 + 4, w - cw / 2 - 4)
            val cyClamped = (s.y - with(density) { 14.dp.toPx() }).coerceAtLeast(padY + ch + 8)
            val boxLeft = cxClamped - cw / 2
            val boxTop = cyClamped - ch - 4
            drawRoundRect(
                color = accent,
                topLeft = Offset(boxLeft, boxTop),
                size = androidx.compose.ui.geometry.Size(cw, ch),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(6f, 6f),
            )
            // Big mg/dL value
            val mgText = "${s.mgDl.toInt()}"
            val mgLayout = tm.measure(
                text = mgText,
                style = TextStyle(
                    color = accentOn,
                    fontFamily = JetBrainsMono,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                ),
            )
            val unitLayout = tm.measure(
                text = unitLabel,
                style = TextStyle(
                    color = accentOn.copy(alpha = 0.7f),
                    fontFamily = JetBrainsMono,
                    fontSize = 9.sp,
                ),
            )
            val totalW = mgLayout.size.width + unitLayout.size.width + 4
            val mgTopLeft = Offset(boxLeft + (cw - totalW) / 2f, boxTop + 4f)
            drawText(mgLayout, topLeft = mgTopLeft)
            drawText(
                unitLayout,
                topLeft = Offset(mgTopLeft.x + mgLayout.size.width + 4, mgTopLeft.y + 5),
            )
            // Time label
            val timeText = formatScrubTime(s.hour, rangeHours)
            val timeLayout = tm.measure(
                text = timeText,
                style = TextStyle(
                    color = accentOn.copy(alpha = 0.6f),
                    fontFamily = JetBrainsMono,
                    fontSize = 9.sp,
                ),
            )
            drawText(
                timeLayout,
                topLeft = Offset(boxLeft + (cw - timeLayout.size.width) / 2f, boxTop + ch - 14),
            )
        }
    }
}

private data class ScrubState(val x: Float, val y: Float, val mgDl: Float, val hour: Float)

private fun nearestSample(
    pointerX: Float,
    canvasWidth: Float,
    canvasHeight: Float,
    samples: List<GlucoseSample>,
    rangeHours: Float,
    yMin: Float,
    yMax: Float,
): ScrubState? {
    if (samples.isEmpty()) return null
    val padY = 24f * (canvasHeight / 220f).coerceAtLeast(1f)
    var bestIdx = 0
    var bestDx = Float.MAX_VALUE
    samples.forEachIndexed { i, s ->
        val sx = (s.hour / rangeHours) * canvasWidth
        val dx = kotlin.math.abs(sx - pointerX)
        if (dx < bestDx) { bestDx = dx; bestIdx = i }
    }
    val s = samples[bestIdx]
    val sx = (s.hour / rangeHours) * canvasWidth
    val sy = padY + ((yMax - s.mgDl) / (yMax - yMin)) * (canvasHeight - padY * 2)
    return ScrubState(sx, sy, s.mgDl, s.hour)
}

private fun formatScrubTime(hour: Float, rangeHours: Float): String = when {
    hour >= rangeHours - 0.01f -> "now"
    hour <= 0.01f -> "−${rangeHours.toInt()}h"
    else -> "−${(rangeHours - hour).toInt()}h"
}

private fun buildSmoothPath(
    xs: FloatArray,
    ys: FloatArray,
    closeAtBottom: Boolean,
    canvasHeight: Float,
): Path {
    val path = Path()
    val n = xs.size
    if (n < 2) return path
    path.moveTo(xs[0], ys[0])
    for (i in 0 until n - 1) {
        val x0 = if (i - 1 >= 0) xs[i - 1] else xs[i]
        val y0 = if (i - 1 >= 0) ys[i - 1] else ys[i]
        val x1 = xs[i]; val y1 = ys[i]
        val x2 = xs[i + 1]; val y2 = ys[i + 1]
        val x3 = if (i + 2 < n) xs[i + 2] else x2
        val y3 = if (i + 2 < n) ys[i + 2] else y2
        val c1x = x1 + (x2 - x0) / 6f
        val c1y = y1 + (y2 - y0) / 6f
        val c2x = x2 - (x3 - x1) / 6f
        val c2y = y2 - (y3 - y1) / 6f
        path.cubicTo(c1x, c1y, c2x, c2y, x2, y2)
    }
    if (closeAtBottom) {
        path.lineTo(xs[n - 1], canvasHeight)
        path.lineTo(xs[0], canvasHeight)
        path.close()
    }
    return path
}

private enum class MarkerAnchor { Top, Bottom }

private fun DrawScope.drawMarker(
    tm: TextMeasurer,
    density: androidx.compose.ui.unit.Density,
    cx: Float,
    cy: Float,
    label: String,
    anchor: MarkerAnchor,
    fillColor: Color,
    strokeColor: Color,
    textColor: Color,
) {
    drawCircle(color = fillColor, radius = 3f, center = Offset(cx, cy))
    drawCircle(color = strokeColor, radius = 3f, center = Offset(cx, cy), style = Stroke(width = 1.2f))
    val layout: TextLayoutResult = tm.measure(
        text = label,
        style = TextStyle(
            color = textColor,
            fontFamily = JetBrainsMono,
            fontSize = 9.sp,
        ),
    )
    val labelY = if (anchor == MarkerAnchor.Top) cy - layout.size.height - 6f
                 else cy + 8f
    drawText(
        textLayoutResult = layout,
        topLeft = Offset(cx - layout.size.width / 2f, labelY),
    )
}

