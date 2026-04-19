package com.syschimp.glucoripper.wear.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import com.syschimp.glucoripper.wear.data.GlucosePayload
import kotlin.math.cos
import kotlin.math.sin

/**
 * Watch "Now" page — mirrors the phone's RingGauge: a 270° arc with zone-colored
 * segments, pointer triangle, and center readout. Sized down for Wear displays.
 */
@Composable
fun NowScreen(payload: GlucosePayload) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        if (payload.latestTimeMillis == 0L) {
            EmptyNow()
        } else {
            WearRingGauge(payload)
            Spacer(Modifier.size(2.dp))
            ContextLine(payload)
        }
    }
}

@Composable
private fun EmptyNow() {
    Text(
        "Glucoripper",
        style = MaterialTheme.typography.title3,
        color = MaterialTheme.colors.onBackground,
    )
    Spacer(Modifier.size(4.dp))
    Text(
        "Waiting for a reading from your phone…",
        style = MaterialTheme.typography.caption2,
        color = MaterialTheme.colors.onBackground.copy(alpha = 0.7f),
    )
}

@Composable
private fun WearRingGauge(payload: GlucosePayload) {
    val (low, high) = payload.targetRangeFor(payload.latestMealRelation)
    val band = classify(payload.latestMgDl, low, high)

    val gaugeMin = 40f
    val gaugeMax = 300f
    fun fractionOf(v: Float): Float =
        ((v - gaugeMin) / (gaugeMax - gaugeMin)).coerceIn(0f, 1f)

    val pointerTarget = payload.latestMgDl.toFloat().let(::fractionOf)
    val animated by animateFloatAsState(
        targetValue = pointerTarget,
        animationSpec = tween(durationMillis = 600),
        label = "gaugePointer",
    )

    val trackColor = Color(0xFF2A3034)
    val pointerColor = MaterialTheme.colors.onBackground

    Box(
        modifier = Modifier.size(150.dp),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val stroke = 10.dp.toPx()
            val inset = stroke / 2f + 4.dp.toPx()
            val topLeft = Offset(inset, inset)
            val arcSize = Size(size.width - inset * 2, size.height - inset * 2)
            val startAngle = 135f
            val totalSweep = 270f

            drawArc(
                color = trackColor,
                startAngle = startAngle,
                sweepAngle = totalSweep,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )

            val segments = listOf(
                Triple(gaugeMin, low.toFloat(), GlucoseLow),
                Triple(low.toFloat(), high.toFloat(), GlucoseInRange),
                Triple(high.toFloat(), 180f.coerceAtMost(gaugeMax), GlucoseElevated),
                Triple(180f.coerceAtMost(gaugeMax), gaugeMax, GlucoseHigh),
            )
            segments.forEach { (a, b, col) ->
                val f0 = fractionOf(a)
                val f1 = fractionOf(b)
                if (f1 <= f0) return@forEach
                drawArc(
                    color = col,
                    startAngle = startAngle + totalSweep * f0,
                    sweepAngle = totalSweep * (f1 - f0),
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = stroke, cap = StrokeCap.Butt),
                )
            }

            val angleDeg = startAngle + totalSweep * animated
            val angleRad = Math.toRadians(angleDeg.toDouble())
            val cx = size.width / 2f
            val cy = size.height / 2f
            val radius = (arcSize.width / 2f) + stroke / 2f + 1.dp.toPx()
            val tipX = cx + radius * cos(angleRad).toFloat()
            val tipY = cy + radius * sin(angleRad).toFloat()
            val baseRadius = radius + 7.dp.toPx()
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
            drawPath(path, color = pointerColor)
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                "Current (${unitLabel(payload.unit)})",
                style = MaterialTheme.typography.caption3,
                color = MaterialTheme.colors.onBackground.copy(alpha = 0.7f),
            )
            Spacer(Modifier.size(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    formatGlucose(payload.latestMgDl, payload.unit),
                    fontSize = 34.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colors.onBackground,
                )
                val arrow = trendArrow(payload)
                if (arrow != null) {
                    Spacer(Modifier.width(3.dp))
                    Text(
                        arrow,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                        color = band.color,
                    )
                }
            }
            Text(
                band.label,
                style = MaterialTheme.typography.caption2,
                color = band.color,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun ContextLine(payload: GlucosePayload) {
    val parts = buildList {
        val meal = mealLabel(payload.latestMealRelation)
        if (meal.isNotEmpty()) add(meal)
        add(relativeTime(payload.latestInstant))
    }
    Text(
        parts.joinToString(" · "),
        style = MaterialTheme.typography.caption2,
        color = MaterialTheme.colors.onBackground.copy(alpha = 0.75f),
    )
}

private fun trendArrow(payload: GlucosePayload): String? {
    val delta = trendDelta(payload) ?: return null
    return when {
        delta > 15f -> "↗"
        delta < -15f -> "↘"
        else -> "→"
    }
}
