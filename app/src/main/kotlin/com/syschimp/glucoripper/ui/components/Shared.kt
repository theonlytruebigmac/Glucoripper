package com.syschimp.glucoripper.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.health.connect.client.records.BloodGlucoseRecord
import com.syschimp.glucoripper.data.GlucoseUnit
import com.syschimp.glucoripper.data.ReadingAnnotation
import com.syschimp.glucoripper.ui.format.formatGlucose
import com.syschimp.glucoripper.ui.format.unitLabel
import com.syschimp.glucoripper.ui.theme.GlucoseElevated
import com.syschimp.glucoripper.ui.theme.GlucoseHigh
import com.syschimp.glucoripper.ui.theme.GlucoseInRange
import com.syschimp.glucoripper.ui.theme.GlucoseLow
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val rowTimeFormatter = DateTimeFormatter.ofPattern("MMM d, h:mm a")
private val timeOnlyFormatter = DateTimeFormatter.ofPattern("h:mm a")

// ────────── Color classification ──────────

data class GlucoseBand(val label: String, val color: Color)

fun classifyMgDl(mgDl: Double?): GlucoseBand {
    if (mgDl == null) return GlucoseBand("—", Color.Gray)
    return when {
        mgDl < 70 -> GlucoseBand("Low", GlucoseLow)
        mgDl < 140 -> GlucoseBand("In range", GlucoseInRange)
        mgDl < 180 -> GlucoseBand("Elevated", GlucoseElevated)
        else -> GlucoseBand("High", GlucoseHigh)
    }
}

fun bandColor(mgDl: Double, lowMgDl: Double, highMgDl: Double): Color = when {
    mgDl < lowMgDl -> GlucoseLow
    mgDl > highMgDl -> GlucoseHigh
    else -> GlucoseInRange
}

// ────────── Formatting helpers ──────────

fun relationShort(code: Int): String? = when (code) {
    BloodGlucoseRecord.RELATION_TO_MEAL_BEFORE_MEAL -> "Before meal"
    BloodGlucoseRecord.RELATION_TO_MEAL_AFTER_MEAL -> "After meal"
    BloodGlucoseRecord.RELATION_TO_MEAL_FASTING -> "Fasting"
    BloodGlucoseRecord.RELATION_TO_MEAL_GENERAL -> "General"
    else -> null
}

fun formatRowTime(t: Instant): String =
    rowTimeFormatter.format(t.atZone(ZoneId.systemDefault()))

fun formatTimeOnly(t: Instant): String =
    timeOnlyFormatter.format(t.atZone(ZoneId.systemDefault()))

fun relativeTime(t: Instant): String {
    val d = Duration.between(t, Instant.now())
    return when {
        d.isNegative -> "just now"
        d.toMinutes() < 1 -> "just now"
        d.toMinutes() < 60 -> "${d.toMinutes()} min ago"
        d.toHours() < 24 -> "${d.toHours()} h ago"
        d.toDays() < 7 -> "${d.toDays()} d ago"
        else -> formatRowTime(t)
    }
}

// ────────── Reusable composables ──────────

@Composable
fun Dot(color: Color, size: Dp = 12.dp) {
    Box(
        Modifier
            .size(size)
            .background(color, shape = CircleShape),
    )
}

@Composable
fun SectionHeader(
    text: String,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 4.dp, start = 4.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text.uppercase(),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f),
        )
        action?.invoke()
    }
}

@Composable
fun ReadingRow(
    reading: BloodGlucoseRecord,
    annotation: ReadingAnnotation?,
    unit: GlucoseUnit,
    lowMgDl: Double,
    highMgDl: Double,
    showDate: Boolean = true,
    onClick: () -> Unit,
) {
    val mgDl = reading.level.inMilligramsPerDeciliter
    val color = bandColor(mgDl, lowMgDl, highMgDl)
    val effectiveMeal = annotation?.mealOverride ?: reading.relationToMeal
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 4.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Dot(color = color, size = 10.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                if (showDate) formatRowTime(reading.time) else formatTimeOnly(reading.time),
                style = MaterialTheme.typography.bodyMedium,
            )
            val subtitle = buildList {
                relationShort(effectiveMeal)?.let(::add)
                annotation?.feeling?.let { add("${it.emoji} ${it.label}") }
                annotation?.note?.takeIf { it.isNotBlank() }?.let {
                    val preview = if (it.length > 30) it.take(30) + "…" else it
                    add("“$preview”")
                }
            }.joinToString(" · ")
            if (subtitle.isNotEmpty()) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Text(
            formatGlucose(mgDl, unit),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            " " + unitLabel(unit),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
