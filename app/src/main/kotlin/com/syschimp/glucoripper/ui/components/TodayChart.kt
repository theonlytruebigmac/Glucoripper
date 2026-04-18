package com.syschimp.glucoripper.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingFlat
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.health.connect.client.records.BloodGlucoseRecord
import com.syschimp.glucoripper.data.GlucoseUnit
import com.syschimp.glucoripper.ui.format.formatGlucose
import com.syschimp.glucoripper.ui.format.unitLabel
import com.syschimp.glucoripper.ui.theme.GlucoseHigh
import com.syschimp.glucoripper.ui.theme.GlucoseInRange
import com.syschimp.glucoripper.ui.theme.GlucoseLow
import java.time.Duration
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId

@Composable
fun TodayChartCard(
    readings: List<BloodGlucoseRecord>,
    unit: GlucoseUnit,
    lowMgDl: Double,
    highMgDl: Double,
    hasMeter: Boolean,
    onLatestClick: () -> Unit,
    onPairClick: () -> Unit,
) {
    val now = Instant.now()
    val windowStart = now.minus(Duration.ofHours(24))
    val inWindow = readings
        .filter { it.time.isAfter(windowStart) }
        .sortedBy { it.time }
    val latest = readings.firstOrNull()
    val previous = readings.getOrNull(1)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Column(
            Modifier.padding(vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Column {
                    Text(
                        "Last 24 hours",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Medium,
                    )
                    Text(
                        if (inWindow.isEmpty()) "No readings yet today"
                        else "${inWindow.size} reading${if (inWindow.size == 1) "" else "s"}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                if (latest != null) {
                    LatestOverlay(
                        latest = latest,
                        previous = previous,
                        unit = unit,
                        lowMgDl = lowMgDl,
                        highMgDl = highMgDl,
                        onClick = onLatestClick,
                    )
                }
            }

            if (latest == null) {
                EmptyChartState(hasMeter = hasMeter, onPairClick = onPairClick)
            } else {
                TwentyFourHourChart(
                    points = inWindow,
                    lowMgDl = lowMgDl,
                    highMgDl = highMgDl,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(150.dp)
                        .padding(horizontal = 16.dp),
                )
                TimeAxisLabels(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                )
            }
        }
    }
}

@Composable
private fun LatestOverlay(
    latest: BloodGlucoseRecord,
    previous: BloodGlucoseRecord?,
    unit: GlucoseUnit,
    lowMgDl: Double,
    highMgDl: Double,
    onClick: () -> Unit,
) {
    val mgDl = latest.level.inMilligramsPerDeciliter
    val color = bandColor(mgDl, lowMgDl, highMgDl)
    val bandLabel = classifyMgDl(mgDl).label

    val delta = previous?.let { mgDl - it.level.inMilligramsPerDeciliter }
    val withinTrendWindow = previous?.let {
        Duration.between(it.time, latest.time).toMinutes() in 0..360
    } == true
    val trendIcon: ImageVector? = if (delta != null && withinTrendWindow) {
        when {
            delta > 15 -> Icons.AutoMirrored.Filled.TrendingUp
            delta < -15 -> Icons.AutoMirrored.Filled.TrendingDown
            else -> Icons.AutoMirrored.Filled.TrendingFlat
        }
    } else null

    Column(
        horizontalAlignment = Alignment.End,
        modifier = Modifier.clickable { onClick() },
    ) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                formatGlucose(mgDl, unit),
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                fontSize = 34.sp,
            )
            Spacer(Modifier.width(4.dp))
            Text(
                unitLabel(unit),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 6.dp),
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(
                color = color.copy(alpha = 0.15f),
                shape = RoundedCornerShape(50),
            ) {
                Row(
                    Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(6.dp).background(color, CircleShape))
                    Spacer(Modifier.width(5.dp))
                    Text(
                        bandLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = color,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
            if (trendIcon != null) {
                Spacer(Modifier.width(4.dp))
                Icon(
                    trendIcon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
        Spacer(Modifier.height(2.dp))
        Text(
            relativeTime(latest.time),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun TwentyFourHourChart(
    points: List<BloodGlucoseRecord>,
    lowMgDl: Double,
    highMgDl: Double,
    modifier: Modifier = Modifier,
) {
    val primary = MaterialTheme.colorScheme.primary
    val bandFill = GlucoseInRange.copy(alpha = 0.10f)
    val bandEdge = GlucoseInRange.copy(alpha = 0.35f)
    val axisColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
    val emptyHintColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)

    Canvas(modifier = modifier) {
        val now = Instant.now()
        val windowStart = now.minus(Duration.ofHours(24))
        val totalSecs = 24 * 3600f

        val values = points.map { it.level.inMilligramsPerDeciliter.toFloat() }
        val yMin = (minOf(values.minOrNull() ?: lowMgDl.toFloat(), (lowMgDl - 20).toFloat()))
            .coerceAtLeast(40f)
        val yMax = (maxOf(values.maxOrNull() ?: highMgDl.toFloat(), (highMgDl + 20).toFloat()))
            .coerceAtMost(400f)
        val yRange = (yMax - yMin).coerceAtLeast(1f)

        fun yFor(v: Float): Float = size.height - ((v - yMin) / yRange) * size.height
        fun xFor(t: Instant): Float {
            val secs = Duration.between(windowStart, t).seconds.toFloat()
                .coerceIn(0f, totalSecs)
            return (secs / totalSecs) * size.width
        }

        // Target band
        val topY = yFor(highMgDl.toFloat())
        val botY = yFor(lowMgDl.toFloat())
        drawRect(
            color = bandFill,
            topLeft = Offset(0f, topY),
            size = Size(size.width, botY - topY),
        )
        val dashed = PathEffect.dashPathEffect(floatArrayOf(6f, 6f))
        drawLine(bandEdge, Offset(0f, topY), Offset(size.width, topY),
            strokeWidth = 1.dp.toPx(), pathEffect = dashed)
        drawLine(bandEdge, Offset(0f, botY), Offset(size.width, botY),
            strokeWidth = 1.dp.toPx(), pathEffect = dashed)

        // Hour gridlines — every 6 hours
        for (h in 1..3) {
            val x = size.width * h / 4f
            drawLine(
                color = axisColor,
                start = Offset(x, 0f),
                end = Offset(x, size.height),
                strokeWidth = 0.5.dp.toPx(),
            )
        }

        if (points.isEmpty()) {
            // Faint hint line in the middle of the band
            val midY = (topY + botY) / 2
            drawLine(
                color = emptyHintColor,
                start = Offset(0f, midY),
                end = Offset(size.width, midY),
                strokeWidth = 1.dp.toPx(),
                pathEffect = dashed,
            )
            return@Canvas
        }

        // Data line
        if (points.size >= 2) {
            val path = Path()
            points.forEachIndexed { i, p ->
                val x = xFor(p.time)
                val y = yFor(p.level.inMilligramsPerDeciliter.toFloat())
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            drawPath(
                path = path,
                color = primary,
                style = Stroke(width = 2.5.dp.toPx()),
            )
        }

        // Dots per reading
        points.forEach { p ->
            val x = xFor(p.time)
            val y = yFor(p.level.inMilligramsPerDeciliter.toFloat())
            val mgDl = p.level.inMilligramsPerDeciliter
            val c = when {
                mgDl < lowMgDl -> GlucoseLow
                mgDl > highMgDl -> GlucoseHigh
                else -> GlucoseInRange
            }
            drawCircle(color = c.copy(alpha = 0.25f), radius = 6.dp.toPx(), center = Offset(x, y))
            drawCircle(color = c, radius = 3.5.dp.toPx(), center = Offset(x, y))
        }
    }
}

@Composable
private fun TimeAxisLabels(modifier: Modifier = Modifier) {
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant
    val zone = ZoneId.systemDefault()
    val now = Instant.now().atZone(zone)
    // Hours at the chart ticks: 24h ago, -18h, -12h, -6h, now
    val ticks = (0..4).map { i ->
        val hoursBack = 24 - i * 6
        now.minusHours(hoursBack.toLong()).toLocalTime()
    }
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        ticks.forEachIndexed { index, lt ->
            Text(
                text = formatAxisTime(lt, index == ticks.lastIndex),
                style = MaterialTheme.typography.labelSmall,
                color = onSurfaceVariant,
            )
        }
    }
}

private fun formatAxisTime(lt: LocalTime, isNow: Boolean): String {
    if (isNow) return "Now"
    val hour12 = ((lt.hour + 11) % 12) + 1
    val suffix = if (lt.hour < 12) "a" else "p"
    return "$hour12$suffix"
}

@Composable
private fun EmptyChartState(hasMeter: Boolean, onPairClick: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.Start,
    ) {
        Text(
            if (hasMeter) "Waiting for first sync" else "No meter paired",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            if (hasMeter) "Pull down to refresh, or trigger a sync from Devices."
            else "Pair your Contour Next One to start pulling readings.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (!hasMeter) {
            Spacer(Modifier.height(12.dp))
            Button(onClick = onPairClick) { Text("Pair a meter") }
        }
    }
}
