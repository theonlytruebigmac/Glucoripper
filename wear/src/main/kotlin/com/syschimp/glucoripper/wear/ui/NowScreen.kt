package com.syschimp.glucoripper.wear.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import com.syschimp.glucoripper.wear.data.GlucosePayload

/**
 * Watch "Now" page — chart-as-hero (Re-Diary redesign). The 4h glucose ribbon
 * fills the lower half of the round display; the current value floats centered
 * above it. Breathing dot at the latest sample.
 */
@Composable
fun NowScreen(payload: GlucosePayload) {
    if (payload.latestTimeMillis == 0L) {
        WearEmpty()
        return
    }
    Box(Modifier.fillMaxSize()) {
        WearRibbonChart(payload, modifier = Modifier.fillMaxSize())
        WearHero(payload, modifier = Modifier.align(Alignment.TopCenter))
    }
}

@Composable
private fun WearHero(payload: GlucosePayload, modifier: Modifier = Modifier) {
    val (low, high) = payload.targetRangeFor(payload.latestMealRelation)
    val band = classify(payload.latestMgDl, low, high)
    val arrow = trendArrowSymbol(payload)

    Column(
        modifier = modifier
            .padding(top = 70.dp)
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                formatGlucose(payload.latestMgDl, payload.unit),
                style = WearMono.Hero,
                color = MaterialTheme.colors.onBackground,
            )
            if (arrow != null) {
                Text(
                    arrow,
                    fontSize = 24.sp,
                    color = band.color,
                )
            }
        }
        Text(
            "${band.label.uppercase()} · ${relativeTime(payload.latestInstant)}",
            style = WearOverline.copy(color = MaterialTheme.colors.onBackground.copy(alpha = 0.7f)),
        )
    }
}

@Composable
private fun WearRibbonChart(payload: GlucosePayload, modifier: Modifier = Modifier) {
    val nowMs = payload.windowTimesMillis.lastOrNull() ?: return
    val cutoffMs = nowMs - 4L * 60L * 60L * 1000L
    val (idxs) = collectWindow(payload.windowTimesMillis, cutoffMs)
    if (idxs.size < 2) return

    val (low, high) = payload.targetRangeFor(payload.latestMealRelation)
    val mgs = idxs.map { payload.windowMgDls[it].toDouble() }
    val ts = idxs.map { payload.windowTimesMillis[it] }

    val yMin = (mgs.min().coerceAtMost(low - 8.0))
    val yMax = (mgs.max().coerceAtLeast(high + 12.0))
    val band = classify(mgs.last(), low, high)

    val pulse = rememberInfiniteTransition(label = "wearPulse")
    val pulseR by pulse.animateFloat(
        initialValue = 6f,
        targetValue = 13f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "wearPulseR",
    )
    val pulseAlpha by pulse.animateFloat(
        initialValue = 0.25f,
        targetValue = 0.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "wearPulseA",
    )

    val fg = MaterialTheme.colors.onBackground
    val bg = MaterialTheme.colors.background
    val faint = WearFgFaint
    val targetEdge = fg.copy(alpha = 0.18f)
    val targetBand = fg.copy(alpha = 0.05f)
    val accent = band.color

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val padX = 24.dp.toPx()
        val chartTop = 130.dp.toPx().coerceAtMost(h * 0.45f)
        val chartBottom = (h - 36.dp.toPx()).coerceAtLeast(chartTop + 80.dp.toPx())

        val tMin = ts.first()
        val tMax = ts.last()
        val tSpan = (tMax - tMin).coerceAtLeast(1L).toFloat()
        val ySpan = (yMax - yMin).coerceAtLeast(1.0).toFloat()

        fun xOf(tMs: Long): Float = padX + ((tMs - tMin).toFloat() / tSpan) * (w - padX * 2)
        fun yOf(mg: Double): Float = chartTop + (1f - ((mg - yMin) / ySpan).toFloat()) * (chartBottom - chartTop)

        val xs = ts.map(::xOf)
        val ys = mgs.map(::yOf)

        // Target band fill
        val yLow = yOf(low)
        val yHigh = yOf(high)
        drawRect(
            color = targetBand,
            topLeft = Offset(0f, yHigh),
            size = Size(w, yLow - yHigh),
        )
        val dash = PathEffect.dashPathEffect(floatArrayOf(2f, 4f), 0f)
        drawLine(
            color = targetEdge,
            start = Offset(0f, yLow),
            end = Offset(w, yLow),
            strokeWidth = 0.7f,
            pathEffect = dash,
        )
        drawLine(
            color = targetEdge,
            start = Offset(0f, yHigh),
            end = Offset(w, yHigh),
            strokeWidth = 0.7f,
            pathEffect = dash,
        )

        // Smooth bezier path through the points
        val linePath = buildSmoothPath(xs, ys)
        val areaPath = Path().apply {
            addPath(linePath)
            lineTo(xs.last(), chartBottom)
            lineTo(xs.first(), chartBottom)
            close()
        }

        drawPath(areaPath, color = fg.copy(alpha = 0.06f))
        drawPath(
            linePath,
            color = fg,
            style = Stroke(
                width = 2f * density,
                cap = StrokeCap.Round,
                join = StrokeJoin.Round,
            ),
        )

        // Breathing dot at last sample
        val cx = xs.last()
        val cy = ys.last()
        drawCircle(
            color = accent.copy(alpha = pulseAlpha),
            radius = pulseR * density,
            center = Offset(cx, cy),
        )
        drawCircle(
            color = bg,
            radius = 4.5f * density,
            center = Offset(cx, cy),
        )
        drawCircle(
            color = accent,
            radius = 4.5f * density,
            center = Offset(cx, cy),
            style = Stroke(width = 2f * density),
        )

        // Suppress unused warnings
        if (faint.alpha < 0f) drawCircle(color = faint, radius = 0f, center = Offset.Zero)
    }
}

private fun buildSmoothPath(xs: List<Float>, ys: List<Float>): Path {
    val path = Path()
    val n = xs.size
    if (n < 2) return path
    path.moveTo(xs[0], ys[0])
    for (i in 1 until n) {
        val px = xs[i - 1]
        val py = ys[i - 1]
        val cx = (px + xs[i]) / 2f
        path.cubicTo(cx, py, cx, ys[i], xs[i], ys[i])
    }
    return path
}

/** Returns the indices into the window arrays that sit at or after [cutoffMs]. */
private fun collectWindow(times: LongArray, cutoffMs: Long): Pair<List<Int>, Int> {
    val idx = mutableListOf<Int>()
    for (i in times.indices) {
        if (times[i] >= cutoffMs) idx += i
    }
    return idx to idx.size
}

private fun trendArrowSymbol(payload: GlucosePayload): String? {
    val delta = trendDelta(payload) ?: return null
    return when {
        delta > 15f -> "↗"
        delta < -15f -> "↘"
        else -> "→"
    }
}

// ─────────── Empty state ───────────

@Composable
fun WearEmpty() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 36.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            Modifier
                .size(48.dp)
                .background(Color.Transparent, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Canvas(Modifier.fillMaxSize()) {
                drawCircle(
                    color = WearFgFaint,
                    radius = size.minDimension / 2 - 1.dp.toPx(),
                    style = Stroke(
                        width = 1.5f * density,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(3f, 4f), 0f),
                    ),
                )
            }
        }
        Spacer(Modifier.height(14.dp))
        Text(
            "Waiting for a reading",
            fontSize = 14.sp,
            color = MaterialTheme.colors.onBackground,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "Open Glucoripper on your phone and sync your meter.",
            fontSize = 11.sp,
            color = MaterialTheme.colors.onBackground.copy(alpha = 0.6f),
        )
    }
}
