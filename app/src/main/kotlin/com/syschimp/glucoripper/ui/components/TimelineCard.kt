package com.syschimp.glucoripper.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.health.connect.client.records.BloodGlucoseRecord
import com.syschimp.glucoripper.data.GlucoseUnit
import com.syschimp.glucoripper.data.ReadingAnnotation
import com.syschimp.glucoripper.ui.format.formatGlucose
import com.syschimp.glucoripper.ui.format.unitLabel

/** Bubble card used on both the Dashboard's "Recent" list and History. */
@Composable
fun TimelineCard(
    reading: BloodGlucoseRecord,
    annotation: ReadingAnnotation?,
    unit: GlucoseUnit,
    lowMgDl: Double,
    highMgDl: Double,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val mgDl = reading.level.inMilligramsPerDeciliter
    val color = bandColor(mgDl, lowMgDl, highMgDl)
    val effectiveMeal = annotation?.mealOverride ?: reading.relationToMeal
    val subtitle = listOfNotNull(
        relationShort(effectiveMeal),
        annotation?.feeling?.emoji,
        annotation?.note?.takeIf { it.isNotBlank() }?.let {
            if (it.length > 22) it.take(22) + "…" else it
        },
    ).joinToString(" · ")

    Card(
        modifier = modifier.fillMaxWidth().clickable { onClick() },
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                formatTimeOnly(reading.time),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.width(72.dp),
            )
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(
                        MaterialTheme.colorScheme.surfaceContainerHigh,
                        RoundedCornerShape(12.dp),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(mealEmojiFor(effectiveMeal), style = MaterialTheme.typography.titleMedium)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    formatGlucose(mgDl, unit) + " " + unitLabel(unit),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                if (subtitle.isNotBlank()) {
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Dot(color = color, size = 10.dp)
        }
    }
}

fun mealEmojiFor(code: Int): String = when (code) {
    BloodGlucoseRecord.RELATION_TO_MEAL_BEFORE_MEAL -> "🥗"
    BloodGlucoseRecord.RELATION_TO_MEAL_AFTER_MEAL -> "🍽"
    BloodGlucoseRecord.RELATION_TO_MEAL_FASTING -> "💧"
    BloodGlucoseRecord.RELATION_TO_MEAL_GENERAL -> "🩸"
    else -> "🩸"
}
