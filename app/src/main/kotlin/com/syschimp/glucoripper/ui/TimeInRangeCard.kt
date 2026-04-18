package com.syschimp.glucoripper.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.health.connect.client.records.BloodGlucoseRecord
import com.syschimp.glucoripper.data.GlucoseUnit
import com.syschimp.glucoripper.ui.format.formatGlucose
import com.syschimp.glucoripper.ui.format.unitLabel
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

data class TimeInRangeStats(
    val windowDays: Int,
    val count: Int,
    val inRangePct: Int,
    val lowPct: Int,
    val highPct: Int,
    val averageMgDl: Double?,
    val dailyAveragesMgDl: List<Double>,
)

fun computeTimeInRange(
    readings: List<BloodGlucoseRecord>,
    lowMgDl: Double,
    highMgDl: Double,
    windowDays: Int = 14,
): TimeInRangeStats {
    val cutoff = Instant.now().minus(Duration.ofDays(windowDays.toLong()))
    val recent = readings.filter { it.time.isAfter(cutoff) }
    if (recent.isEmpty()) return TimeInRangeStats(
        windowDays, 0, 0, 0, 0, null, emptyList()
    )

    val low = recent.count { it.level.inMilligramsPerDeciliter < lowMgDl }
    val high = recent.count { it.level.inMilligramsPerDeciliter > highMgDl }
    val inRange = recent.size - low - high
    val avg = recent.map { it.level.inMilligramsPerDeciliter }.average()

    val zone = ZoneId.systemDefault()
    val dailies = recent
        .groupBy { it.time.atZone(zone).toLocalDate() }
        .toSortedMap()
        .values
        .map { it.map { r -> r.level.inMilligramsPerDeciliter }.average() }

    return TimeInRangeStats(
        windowDays = windowDays,
        count = recent.size,
        inRangePct = (inRange * 100 / recent.size),
        lowPct = (low * 100 / recent.size),
        highPct = (high * 100 / recent.size),
        averageMgDl = avg,
        dailyAveragesMgDl = dailies,
    )
}

private val lowColor = Color(0xFFE53935)
private val rangeColor = Color(0xFF43A047)
private val highColor = Color(0xFFD81B60)

@Composable
fun TimeInRangeCard(
    stats: TimeInRangeStats,
    unit: GlucoseUnit,
    lowMgDl: Double,
    highMgDl: Double,
) {
    val surfaceVariant = MaterialTheme.colorScheme.surfaceVariant
    val primary = MaterialTheme.colorScheme.primary
    val bandFill = primary.copy(alpha = 0.10f)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                "Last ${stats.windowDays} days",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (stats.count == 0) {
                Text(
                    "No readings yet in this window",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                return@Column
            }
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    "${stats.inRangePct}%",
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    "in range",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 6.dp),
                )
            }
            StackedBar(
                low = stats.lowPct,
                inRange = stats.inRangePct,
                high = stats.highPct,
                trackColor = surfaceVariant,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Metric("Low", "${stats.lowPct}%", lowColor)
                Metric(
                    "Average",
                    stats.averageMgDl?.let { formatGlucose(it, unit) + " " + unitLabel(unit) } ?: "—",
                    MaterialTheme.colorScheme.onSurface,
                )
                Metric("High", "${stats.highPct}%", highColor)
            }
            if (stats.dailyAveragesMgDl.size >= 2) {
                Sparkline(
                    data = stats.dailyAveragesMgDl,
                    lowMgDl = lowMgDl,
                    highMgDl = highMgDl,
                    lineColor = primary,
                    bandColor = bandFill,
                )
            }
            Text(
                "${stats.count} readings · target ${formatGlucose(lowMgDl, unit)}–${formatGlucose(highMgDl, unit)} ${unitLabel(unit)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun Metric(label: String, value: String, color: Color) {
    Column {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = color,
        )
    }
}

@Composable
private fun StackedBar(low: Int, inRange: Int, high: Int, trackColor: Color) {
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(10.dp),
    ) {
        val total = (low + inRange + high).coerceAtLeast(1).toFloat()
        val w = size.width
        val lowW = w * low / total
        val rangeW = w * inRange / total
        val highW = w * high / total
        val radius = CornerRadius(size.height / 2f, size.height / 2f)
        drawRoundRect(color = trackColor, cornerRadius = radius)
        drawRect(color = lowColor, topLeft = Offset(0f, 0f), size = Size(lowW, size.height))
        drawRect(color = rangeColor, topLeft = Offset(lowW, 0f), size = Size(rangeW, size.height))
        drawRect(color = highColor, topLeft = Offset(lowW + rangeW, 0f), size = Size(highW, size.height))
    }
}

@Composable
private fun Sparkline(
    data: List<Double>,
    lowMgDl: Double,
    highMgDl: Double,
    lineColor: Color,
    bandColor: Color,
) {
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .padding(top = 4.dp),
    ) {
        if (data.size < 2) return@Canvas
        val min = (data.min()).coerceAtMost(lowMgDl - 10).toFloat()
        val max = (data.max()).coerceAtLeast(highMgDl + 10).toFloat()
        val span = (max - min).coerceAtLeast(1f)
        fun yFor(v: Float): Float = size.height - ((v - min) / span) * size.height

        drawRect(
            color = bandColor,
            topLeft = Offset(0f, yFor(highMgDl.toFloat())),
            size = Size(
                width = size.width,
                height = yFor(lowMgDl.toFloat()) - yFor(highMgDl.toFloat()),
            ),
        )

        val path = Path()
        data.forEachIndexed { i, v ->
            val x = size.width * i / (data.size - 1)
            val y = yFor(v.toFloat())
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path = path, color = lineColor, style = Stroke(width = 3f))
    }
}
