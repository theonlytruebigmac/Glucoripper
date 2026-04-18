package com.syschimp.glucoripper.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.material3.FilterChip
import androidx.health.connect.client.records.BloodGlucoseRecord
import com.syschimp.glucoripper.data.Feeling
import com.syschimp.glucoripper.data.GlucoseUnit
import com.syschimp.glucoripper.data.ReadingAnnotation
import com.syschimp.glucoripper.data.SyncHistoryEntry
import com.syschimp.glucoripper.data.UserPreferences
import com.syschimp.glucoripper.ui.format.formatGlucose
import com.syschimp.glucoripper.ui.format.unitLabel
import kotlinx.coroutines.launch
import java.time.Instant
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

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("Reading", style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    formatGlucose(reading.level.inMilligramsPerDeciliter, unit),
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
                detailTimeFormatter.format(reading.time.atZone(ZoneId.systemDefault())),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            HorizontalDivider()

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Meal", style = MaterialTheme.typography.labelLarge)
                MealChips(selected = effectiveMeal, onSelect = onSaveMeal)
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("How are you feeling?", style = MaterialTheme.typography.labelLarge)
                FeelingChips(selected = annotation?.feeling, onSelect = onSaveFeeling)
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
                Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                    TextButton(onClick = { onSaveNote(noteText) }) { Text("Save note") }
                }
            }

            HorizontalDivider()
            Field("Specimen", specimenLabel(reading.specimenSource))
            Field("Device", listOfNotNull(
                reading.metadata.device?.manufacturer,
                reading.metadata.device?.model,
            ).joinToString(" ").ifBlank { "—" })
            Field("Source ID", reading.metadata.clientRecordId ?: reading.metadata.id)
        }
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
        Text(label, style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
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

// ────────────────────────── Settings ──────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsSheet(
    prefs: UserPreferences,
    onDismiss: () -> Unit,
    onSaveUnit: (GlucoseUnit) -> Unit,
    onSaveRange: (lowMgDl: Double, highMgDl: Double) -> Unit,
) {
    var lowText by remember(prefs) { mutableStateOf("%.0f".format(prefs.targetLowMgDl)) }
    var highText by remember(prefs) { mutableStateOf("%.0f".format(prefs.targetHighMgDl)) }
    val scope = rememberCoroutineScope()

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("Settings", style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold)

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Display units", style = MaterialTheme.typography.labelLarge)
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    val options = listOf(GlucoseUnit.MG_PER_DL to "mg/dL", GlucoseUnit.MMOL_PER_L to "mmol/L")
                    options.forEachIndexed { index, (u, label) ->
                        SegmentedButton(
                            selected = prefs.unit == u,
                            onClick = { onSaveUnit(u) },
                            shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                        ) { Text(label) }
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Target range (mg/dL)", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = lowText, onValueChange = { lowText = it.filter { c -> c.isDigit() } },
                        label = { Text("Low") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth().weight(1f),
                    )
                    OutlinedTextField(
                        value = highText, onValueChange = { highText = it.filter { c -> c.isDigit() } },
                        label = { Text("High") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth().weight(1f),
                    )
                }
                TextButton(onClick = {
                    val low = lowText.toDoubleOrNull() ?: prefs.targetLowMgDl
                    val high = highText.toDoubleOrNull() ?: prefs.targetHighMgDl
                    if (high > low) onSaveRange(low, high)
                }) { Text("Save target range") }
            }
        }
    }
}

// ────────────────────────── Sync history ──────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SyncHistorySheet(
    entries: List<SyncHistoryEntry>,
    onDismiss: () -> Unit,
    onClear: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 16.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("Sync history", style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold)
                TextButton(onClick = onClear) { Text("Clear") }
            }
            Spacer(Modifier.height(8.dp))
            if (entries.isEmpty()) {
                Text(
                    "Nothing synced yet.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(entries) { e ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                if (e.success) Icons.Default.CheckCircle else Icons.Default.Error,
                                contentDescription = null,
                                tint = if (e.success) Color(0xFF43A047) else Color(0xFFE53935),
                            )
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    detailTimeFormatter.format(
                                        Instant.ofEpochMilli(e.timestampMillis)
                                            .atZone(ZoneId.systemDefault())
                                    ),
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                                Text(
                                    if (e.success) {
                                        "Pulled ${e.pulled}, wrote ${e.written}" +
                                                if (e.skippedControl > 0) ", skipped ${e.skippedControl}" else ""
                                    } else (e.message ?: "Failed"),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}
