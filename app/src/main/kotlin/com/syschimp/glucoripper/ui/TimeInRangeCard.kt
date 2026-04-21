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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.health.connect.client.records.BloodGlucoseRecord
import com.syschimp.glucoripper.data.GlucoseUnit
import com.syschimp.glucoripper.ui.format.formatGlucose
import com.syschimp.glucoripper.ui.format.unitLabel
import com.syschimp.glucoripper.ui.theme.GlucoseHigh
import com.syschimp.glucoripper.ui.theme.GlucoseInRange
import com.syschimp.glucoripper.ui.theme.GlucoseLow
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import kotlin.math.roundToInt

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

    // Round the two smaller buckets and derive the third so the three always
    // sum to exactly 100 — otherwise the stacked bar caption can show 99% / 101%.
    val total = recent.size.toDouble()
    val lowPct = (low * 100.0 / total).roundToInt()
    val highPct = (high * 100.0 / total).roundToInt()
    val inRangePct = (100 - lowPct - highPct).coerceIn(0, 100)

    return TimeInRangeStats(
        windowDays = windowDays,
        count = recent.size,
        inRangePct = inRangePct,
        lowPct = lowPct,
        highPct = highPct,
        averageMgDl = avg,
        dailyAveragesMgDl = dailies,
    )
}

@Composable
fun TimeInRangeCard(
    stats: TimeInRangeStats,
    unit: GlucoseUnit,
    lowMgDl: Double,
    highMgDl: Double,
) {
    val trackColor = MaterialTheme.colorScheme.surfaceVariant

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Column(
            Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Time in range",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    "Last ${stats.windowDays} days",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (stats.count == 0) {
                Text(
                    "No readings in this window yet",
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
                Spacer(Modifier.width(8.dp))
                Text(
                    "in range",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
            }
            StackedBar(
                low = stats.lowPct,
                inRange = stats.inRangePct,
                high = stats.highPct,
                trackColor = trackColor,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Metric("Low", "${stats.lowPct}%", GlucoseLow)
                Metric(
                    "Average",
                    stats.averageMgDl?.let { formatGlucose(it, unit) + " " + unitLabel(unit) } ?: "—",
                    MaterialTheme.colorScheme.onSurface,
                )
                Metric("High", "${stats.highPct}%", GlucoseHigh)
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
            .height(12.dp),
    ) {
        val total = (low + inRange + high).coerceAtLeast(1).toFloat()
        val w = size.width
        val lowW = w * low / total
        val rangeW = w * inRange / total
        val highW = w * high / total
        val radius = CornerRadius(size.height / 2f, size.height / 2f)
        drawRoundRect(color = trackColor, cornerRadius = radius)
        drawRect(color = GlucoseLow, topLeft = Offset(0f, 0f), size = Size(lowW, size.height))
        drawRect(color = GlucoseInRange, topLeft = Offset(lowW, 0f), size = Size(rangeW, size.height))
        drawRect(color = GlucoseHigh, topLeft = Offset(lowW + rangeW, 0f), size = Size(highW, size.height))
    }
}
