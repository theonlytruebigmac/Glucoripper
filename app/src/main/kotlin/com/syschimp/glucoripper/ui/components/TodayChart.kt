package com.syschimp.glucoripper.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.health.connect.client.records.BloodGlucoseRecord
import com.syschimp.glucoripper.data.EventKind
import com.syschimp.glucoripper.data.GlucoseUnit
import com.syschimp.glucoripper.data.HealthEvent
import com.syschimp.glucoripper.ui.theme.GlucoseElevated
import com.syschimp.glucoripper.ui.theme.GlucoseHigh
import com.syschimp.glucoripper.ui.theme.GlucoseInRange
import com.syschimp.glucoripper.ui.theme.GlucoseLow
import java.time.Duration
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId

/**
 * Day-range area chart. Target band is filled as a soft green strip; the line
 * traces readings with a gradient-tinted area fill beneath it; user-logged
 * [events] are rendered as small emoji pills pinned to the timeline at the
 * readings' interpolated Y.
 */
@Composable
fun TodayChart(
    readings: List<BloodGlucoseRecord>,
    events: List<HealthEvent>,
    unit: GlucoseUnit,
    lowMgDl: Double,
    highMgDl: Double,
    hasMeter: Boolean,
    onEventClick: (HealthEvent) -> Unit,
    onPairClick: () -> Unit,
    modifier: Modifier = Modifier,
    windowHours: Long = 24,
    yMinMgDl: Double = 40.0,
    yMaxMgDl: Double = 200.0,
    warningBuffer: Double = 0.0,
    /** Per-reading dot color — lets callers pass context-aware colors. */
    colorForReading: (BloodGlucoseRecord) -> androidx.compose.ui.graphics.Color = { r ->
        bandColor(r.level.inMilligramsPerDeciliter, lowMgDl, highMgDl, warningBuffer)
    },
) {
    val now = Instant.now()
    val windowStart = now.minus(Duration.ofHours(windowHours))
    val inWindow = readings
        .filter { it.time.isAfter(windowStart) }
        .sortedBy { it.time }

    if (inWindow.isEmpty()) {
        EmptyChartState(hasMeter = hasMeter, onPairClick = onPairClick, modifier = modifier)
        return
    }

    val eventsInWindow = events.filter { it.time.isAfter(windowStart) && !it.time.isAfter(now) }

    // Y domain comes from user prefs so ranges match the individual's condition.
    val yMin = yMinMgDl.toFloat()
    val yMax = yMaxMgDl.toFloat().coerceAtLeast(yMin + 1f)
    val yRange = yMax - yMin
    // Ticks: pick ~3 round-number gridlines inside the range (e.g. 100/200/300,
    // or 50/100/150 for a tighter view).
    val yTicks = computeTicks(yMin, yMax)
    val totalSecs = windowHours * 3600f

    fun xFracFor(t: Instant): Float {
        val secs = Duration.between(windowStart, t).seconds.toFloat().coerceIn(0f, totalSecs)
        return secs / totalSecs
    }
    fun yFracFor(v: Float): Float = 1f - ((v - yMin) / yRange)

    val primary = MaterialTheme.colorScheme.onSurface
    val axisColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant

    // Horizontal bands mirror the user's target range, with amber cushions
    // when the warning buffer is non-zero.
    val bandAlpha = 0.18f
    val buf = warningBuffer.toFloat()
    val lowEdge = (lowMgDl.toFloat() - buf).coerceAtLeast(yMin)
    val highEdge = (highMgDl.toFloat() + buf).coerceAtMost(yMax)
    val bands = buildList {
        add(Triple(yMin, lowEdge, GlucoseLow.copy(alpha = bandAlpha)))
        if (buf > 0f) add(Triple(lowEdge, lowMgDl.toFloat(), GlucoseElevated.copy(alpha = bandAlpha)))
        add(Triple(lowMgDl.toFloat(), highMgDl.toFloat(), GlucoseInRange.copy(alpha = bandAlpha)))
        if (buf > 0f) add(Triple(highMgDl.toFloat(), highEdge, GlucoseElevated.copy(alpha = bandAlpha)))
        add(Triple(highEdge, yMax, GlucoseHigh.copy(alpha = bandAlpha)))
    }
    val bandEdgeColor = GlucoseInRange.copy(alpha = 0.45f)

    // Top strip is reserved for event pills so they never overlap the line.
    val eventStripHeight = 36.dp
    val chartHeight = 150.dp
    val yLabelWidth = 28.dp

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.fillMaxWidth().height(chartHeight + eventStripHeight)) {
            // Left gutter: Y-axis labels (300 / 200 / 100)
            Column(
                modifier = Modifier
                    .width(yLabelWidth)
                    .height(chartHeight + eventStripHeight)
                    .padding(top = eventStripHeight),
                verticalArrangement = Arrangement.SpaceBetween,
                horizontalAlignment = Alignment.End,
            ) {
                yTicks.reversed().forEach { v ->
                    Text(
                        v.toInt().toString(),
                        style = MaterialTheme.typography.labelSmall,
                        color = labelColor,
                    )
                }
            }
            Spacer(Modifier.width(4.dp))

            BoxWithConstraints(
                Modifier.fillMaxWidth().height(chartHeight + eventStripHeight),
            ) {
                val plotWidth = maxWidth
                val density = LocalDensity.current

                Canvas(modifier = Modifier.fillMaxSize()) {
                    val stripPx = eventStripHeight.toPx()
                    val plotHeight = size.height - stripPx
                    fun yFor(v: Float) = stripPx + yFracFor(v) * plotHeight
                    fun xFor(t: Instant) = xFracFor(t) * size.width

                    // Colored zone bands — red/yellow/green/yellow/red
                    bands.forEach { (a, b, col) ->
                        val a2 = a.coerceIn(yMin, yMax)
                        val b2 = b.coerceIn(yMin, yMax)
                        if (b2 <= a2) return@forEach
                        val yTop = yFor(b2)
                        val yBot = yFor(a2)
                        drawRect(
                            color = col,
                            topLeft = Offset(0f, yTop),
                            size = Size(size.width, yBot - yTop),
                        )
                    }

                    // Horizontal gridlines at each Y tick (drawn over bands)
                    yTicks.forEach { v ->
                        val y = yFor(v)
                        drawLine(
                            color = axisColor,
                            start = Offset(0f, y),
                            end = Offset(size.width, y),
                            strokeWidth = 0.5.dp.toPx(),
                        )
                    }

                    // Dashed markers at the target edges
                    val topY = yFor(highMgDl.toFloat())
                    val botY = yFor(lowMgDl.toFloat())
                    val dashed = PathEffect.dashPathEffect(floatArrayOf(6f, 6f))
                    drawLine(bandEdgeColor, Offset(0f, topY), Offset(size.width, topY),
                        strokeWidth = 1.dp.toPx(), pathEffect = dashed)
                    drawLine(bandEdgeColor, Offset(0f, botY), Offset(size.width, botY),
                        strokeWidth = 1.dp.toPx(), pathEffect = dashed)

                    // Line path
                    if (inWindow.size >= 2) {
                        val linePath = Path()
                        inWindow.forEachIndexed { i, p ->
                            val x = xFor(p.time)
                            val y = yFor(p.level.inMilligramsPerDeciliter.toFloat())
                            if (i == 0) linePath.moveTo(x, y) else linePath.lineTo(x, y)
                        }
                        drawPath(
                            path = linePath,
                            color = primary,
                            style = Stroke(width = 2.dp.toPx()),
                        )
                    }

                    // Reading dots — colored per reading's contextual range
                    inWindow.forEach { p ->
                        val x = xFor(p.time)
                        val y = yFor(p.level.inMilligramsPerDeciliter.toFloat())
                        val c = colorForReading(p)
                        drawCircle(color = c.copy(alpha = 0.25f), radius = 6.dp.toPx(),
                            center = Offset(x, y))
                        drawCircle(color = c, radius = 3.5.dp.toPx(), center = Offset(x, y))
                    }
                }

                // Event pills overlay — sit in the top strip above the line
                val pillSize = 28.dp
                eventsInWindow.forEach { event ->
                    val xFrac = xFracFor(event.time)
                    val xPx = with(density) { plotWidth.toPx() } * xFrac
                    Surface(
                        shape = CircleShape,
                        color = Color(0xFFFFE8B3),
                        modifier = Modifier
                            .offset(
                                x = with(density) { xPx.toDp() } - pillSize / 2,
                                y = 4.dp,
                            )
                            .size(pillSize)
                            .clickable { onEventClick(event) },
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                event.kind.emoji,
                                style = MaterialTheme.typography.titleMedium,
                            )
                        }
                    }
                }
            }
        }
        // Time axis aligned under the plot area (skip the Y-label gutter)
        Row(Modifier.fillMaxWidth()) {
            Spacer(Modifier.width(yLabelWidth + 4.dp))
            TimeAxisLabels(
                windowHours = windowHours,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

private fun computeTicks(min: Float, max: Float): List<Float> {
    val span = max - min
    val step = when {
        span >= 400f -> 100f
        span >= 200f -> 50f
        span >= 100f -> 25f
        else -> 20f
    }
    val first = (Math.ceil((min / step).toDouble()) * step).toFloat()
    return generateSequence(first) { it + step }
        .takeWhile { it <= max }
        .filter { it > min }
        .toList()
}

private fun interpolateReadingAt(points: List<BloodGlucoseRecord>, t: Instant): Float? {
    if (points.isEmpty()) return null
    val first = points.first()
    val last = points.last()
    if (t.isBefore(first.time)) return first.level.inMilligramsPerDeciliter.toFloat()
    if (t.isAfter(last.time)) return last.level.inMilligramsPerDeciliter.toFloat()
    for (i in 1 until points.size) {
        val a = points[i - 1]
        val b = points[i]
        if (!t.isBefore(a.time) && !t.isAfter(b.time)) {
            val span = Duration.between(a.time, b.time).seconds.toFloat().coerceAtLeast(1f)
            val frac = Duration.between(a.time, t).seconds.toFloat() / span
            val av = a.level.inMilligramsPerDeciliter.toFloat()
            val bv = b.level.inMilligramsPerDeciliter.toFloat()
            return av + (bv - av) * frac
        }
    }
    return last.level.inMilligramsPerDeciliter.toFloat()
}

@Composable
private fun TimeAxisLabels(windowHours: Long, modifier: Modifier = Modifier) {
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant
    val zone = ZoneId.systemDefault()
    val now = Instant.now().atZone(zone)
    val step = windowHours / 4
    val ticks = (0..4).map { i ->
        val hoursBack = windowHours - i * step
        now.minusHours(hoursBack).toLocalTime()
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
    return "%02d:%02d".format(lt.hour, lt.minute - lt.minute % 15)
}

@Composable
private fun EmptyChartState(
    hasMeter: Boolean,
    onPairClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Column(
            Modifier.fillMaxWidth().padding(20.dp),
        ) {
            Text(
                if (hasMeter) "Waiting for first sync" else "No meter paired",
                style = MaterialTheme.typography.titleMedium,
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
}
