package com.syschimp.glucoripper.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingFlat
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.health.connect.client.records.BloodGlucoseRecord
import com.syschimp.glucoripper.data.GlucoseUnit
import com.syschimp.glucoripper.ui.format.formatGlucose
import com.syschimp.glucoripper.ui.format.unitLabel
import com.syschimp.glucoripper.ui.theme.GlucoseElevated
import com.syschimp.glucoripper.ui.theme.GlucoseHigh
import com.syschimp.glucoripper.ui.theme.GlucoseInRange
import com.syschimp.glucoripper.ui.theme.GlucoseLow
import java.time.Duration
import kotlin.math.cos
import kotlin.math.sin

/**
 * Large circular gauge showing the current reading — fills an arc proportional
 * to how the value sits within [lowMgDl]..[highMgDl], with soft gradient stroke
 * matching the reading's band color. A small trend arrow sits on the right.
 */
@Composable
fun RingGauge(
    latest: BloodGlucoseRecord?,
    previous: BloodGlucoseRecord?,
    unit: GlucoseUnit,
    lowMgDl: Double,
    highMgDl: Double,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {},
) {
    val mgDl = latest?.level?.inMilligramsPerDeciliter
    val color = mgDl?.let { bandColor(it, lowMgDl, highMgDl) } ?: MaterialTheme.colorScheme.outline

    // Gauge maps mg/dL values to arc position. Fixed visual range keeps the
    // target band visually centered regardless of current reading.
    val gaugeMin = 40f
    val gaugeMax = 300f
    fun fractionOf(v: Float): Float =
        ((v - gaugeMin) / (gaugeMax - gaugeMin)).coerceIn(0f, 1f)

    val pointerTarget = mgDl?.toFloat()?.let(::fractionOf) ?: 0f
    val animated by animateFloatAsState(
        targetValue = pointerTarget,
        animationSpec = tween(durationMillis = 600),
        label = "gaugePointer",
    )

    val trackColor = MaterialTheme.colorScheme.surfaceContainerHigh
    val pointerColor = MaterialTheme.colorScheme.onSurface

    val delta = if (latest != null && previous != null) {
        val d = Duration.between(previous.time, latest.time).toMinutes()
        if (d in 0..360) latest.level.inMilligramsPerDeciliter -
            previous.level.inMilligramsPerDeciliter else null
    } else null
    val trendIcon: ImageVector? = delta?.let {
        when {
            it > 15.0 -> Icons.AutoMirrored.Filled.TrendingUp
            it < -15.0 -> Icons.AutoMirrored.Filled.TrendingDown
            else -> Icons.AutoMirrored.Filled.TrendingFlat
        }
    }

    Box(
        modifier = modifier
            .size(240.dp)
            .clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val stroke = 14.dp.toPx()
            val inset = stroke / 2f + 6.dp.toPx()
            val topLeft = Offset(inset, inset)
            val arcSize = Size(size.width - inset * 2, size.height - inset * 2)
            val startAngle = 135f
            val totalSweep = 270f

            // Background track
            drawArc(
                color = trackColor,
                startAngle = startAngle,
                sweepAngle = totalSweep,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )

            // Colored zone segments. Bounds are defined in mg/dL, then converted
            // to arc fractions so the band widths scale with the user's target.
            val segments = listOf(
                Triple(gaugeMin, (lowMgDl - 10).toFloat(), GlucoseLow),
                Triple((lowMgDl - 10).toFloat(), lowMgDl.toFloat(), GlucoseElevated),
                Triple(lowMgDl.toFloat(), highMgDl.toFloat(), GlucoseInRange),
                Triple(highMgDl.toFloat(), (highMgDl + 40).toFloat(), GlucoseElevated),
                Triple((highMgDl + 40).toFloat(), gaugeMax, GlucoseHigh),
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

            // Pointer — small filled triangle sitting just outside the ring
            if (mgDl != null) {
                val angleDeg = startAngle + totalSweep * animated
                val angleRad = Math.toRadians(angleDeg.toDouble())
                val cx = size.width / 2f
                val cy = size.height / 2f
                val radius = (arcSize.width / 2f) + stroke / 2f + 2.dp.toPx()
                val tipX = cx + radius * cos(angleRad).toFloat()
                val tipY = cy + radius * sin(angleRad).toFloat()
                val baseRadius = radius + 10.dp.toPx()
                val spread = 0.10f // radians
                val b1x = cx + baseRadius * cos(angleRad + spread).toFloat()
                val b1y = cy + baseRadius * sin(angleRad + spread).toFloat()
                val b2x = cx + baseRadius * cos(angleRad - spread).toFloat()
                val b2y = cy + baseRadius * sin(angleRad - spread).toFloat()
                val path = androidx.compose.ui.graphics.Path().apply {
                    moveTo(tipX, tipY)
                    lineTo(b1x, b1y)
                    lineTo(b2x, b2y)
                    close()
                }
                drawPath(path, color = pointerColor)
            }
        }
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                "Current (${unitLabel(unit)})",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.size(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    mgDl?.let { formatGlucose(it, unit) } ?: "—",
                    fontSize = 60.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (trendIcon != null) {
                    Spacer(Modifier.width(6.dp))
                    Icon(
                        trendIcon,
                        contentDescription = null,
                        tint = color,
                        modifier = Modifier
                            .padding(top = 18.dp)
                            .size(22.dp),
                    )
                }
            }
            Spacer(Modifier.size(2.dp))
            Text(
                latest?.time?.let(::relativeTime) ?: "No readings yet",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

