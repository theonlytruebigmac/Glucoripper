package com.syschimp.glucoripper.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.health.connect.client.records.BloodGlucoseRecord
import com.syschimp.glucoripper.data.Feeling
import com.syschimp.glucoripper.data.GlucoseUnit
import com.syschimp.glucoripper.data.ReadingAnnotation
import com.syschimp.glucoripper.data.StagedReading
import com.syschimp.glucoripper.ui.format.formatGlucose
import com.syschimp.glucoripper.ui.format.unitLabel
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val detailTimeFormatter = DateTimeFormatter.ofPattern("EEE, MMM d yyyy · h:mm a")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReadingDetailSheet(
    reading: BloodGlucoseRecord,
    unit: GlucoseUnit,
    annotation: ReadingAnnotation?,
    onDismiss: () -> Unit,
    onSaveMeal: (Int) -> Unit,
    onSaveFeeling: (Feeling?) -> Unit,
    onSaveNote: (String?) -> Unit,
) {
    val effectiveMeal = annotation?.mealOverride ?: reading.relationToMeal
    var noteText by remember(annotation?.note) { mutableStateOf(annotation?.note.orEmpty()) }

    val mgDl = reading.level.inMilligramsPerDeciliter
    val bandColor = com.syschimp.glucoripper.ui.components.bandColor(
        mgDl, 70.0, 140.0,
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SheetHeroCard(
                valueText = formatGlucose(mgDl, unit),
                unitText = unitLabel(unit),
                dateText = detailTimeFormatter.format(reading.time.atZone(ZoneId.systemDefault())),
                accent = bandColor,
            )
            SheetSectionCard(title = "Meal") {
                MealChips(selected = effectiveMeal, onSelect = onSaveMeal)
            }
            SheetSectionCard(title = "How are you feeling?") {
                FeelingChips(selected = annotation?.feeling, onSelect = onSaveFeeling)
            }
            SheetSectionCard(title = "Note") {
                OutlinedTextField(
                    value = noteText,
                    onValueChange = { noteText = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Optional: carbs, insulin, context…") },
                    minLines = 2,
                    maxLines = 4,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                    shape = RoundedCornerShape(14.dp),
                )
                Row(
                    Modifier.fillMaxWidth().padding(top = 4.dp),
                    horizontalArrangement = Arrangement.End,
                ) {
                    Button(
                        onClick = { onSaveNote(noteText) },
                        shape = RoundedCornerShape(14.dp),
                    ) { Text("Save") }
                }
            }
            SheetSectionCard(title = "Source") {
                MetaRow("Specimen", specimenLabel(reading.specimenSource))
                MetaRow(
                    "Device",
                    listOfNotNull(
                        reading.metadata.device?.manufacturer,
                        reading.metadata.device?.model,
                    ).joinToString(" ").ifBlank { "—" },
                )
                MetaRow(
                    "Source ID",
                    reading.metadata.clientRecordId ?: reading.metadata.id,
                )
            }
        }
    }
}

@Composable
private fun SheetHeroCard(
    valueText: String,
    unitText: String,
    dateText: String,
    accent: androidx.compose.ui.graphics.Color,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    "Reading",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        valueText,
                        style = MaterialTheme.typography.displayMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        unitText,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                }
                Text(
                    dateText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Box(
                modifier = Modifier
                    .size(14.dp)
                    .background(accent, CircleShape),
            )
        }
    }
}

@Composable
private fun SheetSectionCard(
    title: String,
    content: @Composable () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Column(
            Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            content()
        }
    }
}

@Composable
private fun MetaRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth()) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(90.dp),
        )
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
    }
}


@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MealChips(selected: Int, onSelect: (Int) -> Unit) {
    val options = listOf(
        BloodGlucoseRecord.RELATION_TO_MEAL_UNKNOWN to "None",
        BloodGlucoseRecord.RELATION_TO_MEAL_BEFORE_MEAL to "Before meal",
        BloodGlucoseRecord.RELATION_TO_MEAL_AFTER_MEAL to "After meal",
        BloodGlucoseRecord.RELATION_TO_MEAL_FASTING to "Fasting",
        BloodGlucoseRecord.RELATION_TO_MEAL_GENERAL to "General",
    )
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        options.forEach { (code, label) ->
            FilterChip(
                selected = selected == code,
                onClick = { onSelect(code) },
                label = { Text(label) },
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FeelingChips(selected: Feeling?, onSelect: (Feeling?) -> Unit) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Feeling.entries.forEach { f ->
            FilterChip(
                selected = selected == f,
                onClick = { onSelect(if (selected == f) null else f) },
                label = { Text("${f.emoji} ${f.label}") },
            )
        }
    }
}

@Composable
private fun Field(label: String, value: String) {
    Column {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(value, style = MaterialTheme.typography.bodyLarge)
    }
}

private fun mealLabel(code: Int): String = when (code) {
    BloodGlucoseRecord.RELATION_TO_MEAL_BEFORE_MEAL -> "Before meal"
    BloodGlucoseRecord.RELATION_TO_MEAL_AFTER_MEAL -> "After meal"
    BloodGlucoseRecord.RELATION_TO_MEAL_FASTING -> "Fasting"
    BloodGlucoseRecord.RELATION_TO_MEAL_GENERAL -> "General"
    else -> "Not specified"
}

private fun specimenLabel(code: Int): String = when (code) {
    BloodGlucoseRecord.SPECIMEN_SOURCE_WHOLE_BLOOD -> "Whole blood"
    BloodGlucoseRecord.SPECIMEN_SOURCE_PLASMA -> "Plasma"
    BloodGlucoseRecord.SPECIMEN_SOURCE_INTERSTITIAL_FLUID -> "Interstitial fluid"
    BloodGlucoseRecord.SPECIMEN_SOURCE_CAPILLARY_BLOOD -> "Capillary blood"
    BloodGlucoseRecord.SPECIMEN_SOURCE_SERUM -> "Serum"
    BloodGlucoseRecord.SPECIMEN_SOURCE_TEARS -> "Tears"
    else -> "Unknown"
}

// ────────────────────────── Staged reading detail ──────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StagedReadingDetailSheet(
    staged: StagedReading,
    unit: GlucoseUnit,
    onDismiss: () -> Unit,
    onUpdate: ((StagedReading) -> StagedReading) -> Unit,
    onPush: () -> Unit,
    onDiscard: () -> Unit,
) {
    var noteText by remember(staged.note) { mutableStateOf(staged.note.orEmpty()) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    "Pending reading",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    "Seq #${staged.sequenceNumber}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    formatGlucose(staged.mgPerDl, unit),
                    style = MaterialTheme.typography.displayMedium,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    unitLabel(unit),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
            }
            Text(
                detailTimeFormatter.format(staged.time.atZone(ZoneId.systemDefault())),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            HorizontalDivider()

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Meal", style = MaterialTheme.typography.labelLarge)
                MealChips(
                    selected = staged.effectiveMeal,
                    onSelect = { meal ->
                        onUpdate { it.copy(userMeal = if (meal == staged.meterMeal) null else meal) }
                    },
                )
                if (staged.userMeal != null && staged.userMeal != staged.meterMeal) {
                    Text(
                        "Meter reported: ${mealLabel(staged.meterMeal)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("How are you feeling?", style = MaterialTheme.typography.labelLarge)
                FeelingChips(
                    selected = staged.feeling,
                    onSelect = { f -> onUpdate { it.copy(feeling = f) } },
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Note", style = MaterialTheme.typography.labelLarge)
                OutlinedTextField(
                    value = noteText,
                    onValueChange = { noteText = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Optional: carbs, insulin, context…") },
                    minLines = 2,
                    maxLines = 4,
                )
                TextButton(
                    onClick = {
                        val cleaned = noteText.trim().ifEmpty { null }
                        onUpdate { it.copy(note = cleaned) }
                    },
                    modifier = Modifier.align(Alignment.End),
                ) { Text("Save note") }
            }

            HorizontalDivider()
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                OutlinedButton(
                    onClick = {
                        onDiscard()
                        onDismiss()
                    },
                    modifier = Modifier.weight(1f),
                ) { Text("Discard") }
                Button(
                    onClick = {
                        onPush()
                        onDismiss()
                    },
                    modifier = Modifier.weight(1f),
                ) { Text("Send to HC") }
            }
        }
    }
}
