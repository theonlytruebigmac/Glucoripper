package com.syschimp.glucoripper.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.health.connect.client.records.BloodGlucoseRecord
import com.syschimp.glucoripper.data.Feeling
import com.syschimp.glucoripper.data.GlucoseUnit
import com.syschimp.glucoripper.data.ReadingAnnotation
import com.syschimp.glucoripper.data.StagedReading
import com.syschimp.glucoripper.data.targetRangeFor
import com.syschimp.glucoripper.ui.components.RdHairline
import com.syschimp.glucoripper.ui.components.RdOverlineText
import com.syschimp.glucoripper.ui.components.classifyBand
import com.syschimp.glucoripper.ui.components.rdSubtle
import com.syschimp.glucoripper.ui.format.formatGlucose
import com.syschimp.glucoripper.ui.format.unitLabel
import com.syschimp.glucoripper.ui.theme.RdMono
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val detailTimeFormatter = DateTimeFormatter.ofPattern("EEE, MMM d yyyy · h:mm a")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReadingDetailSheet(
    reading: BloodGlucoseRecord,
    unit: GlucoseUnit,
    annotation: ReadingAnnotation?,
    prefs: com.syschimp.glucoripper.data.UserPreferences,
    onDismiss: () -> Unit,
    onSaveMeal: (Int) -> Unit,
    onSaveFeeling: (Feeling?) -> Unit,
    onSaveNote: (String?) -> Unit,
) {
    val effectiveMeal = annotation?.mealOverride ?: reading.relationToMeal
    var noteText by remember(reading.metadata.id) {
        mutableStateOf(annotation?.note.orEmpty())
    }

    val mgDl = reading.level.inMilligramsPerDeciliter
    val range = prefs.targetRangeFor(effectiveMeal)
    val band = classifyBand(mgDl, range.first, range.second)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
        ) {
            RdOverlineText("Reading")
            Spacer(Modifier.height(6.dp))
            HeroNumericRow(
                valueText = formatGlucose(mgDl, unit),
                unitText = unitLabel(unit),
                dateText = detailTimeFormatter.format(reading.time.atZone(ZoneId.systemDefault())),
            )

            Spacer(Modifier.height(22.dp))
            RdOverlineText("Meal")
            Spacer(Modifier.height(8.dp))
            MealChips(selected = effectiveMeal, onSelect = onSaveMeal)

            Spacer(Modifier.height(18.dp))
            RdOverlineText("Feeling")
            Spacer(Modifier.height(8.dp))
            FeelingChips(selected = annotation?.feeling, onSelect = onSaveFeeling)

            Spacer(Modifier.height(18.dp))
            RdOverlineText("Note")
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = noteText,
                onValueChange = { noteText = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Optional: carbs, insulin, context…") },
                minLines = 2,
                maxLines = 4,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                shape = RoundedCornerShape(8.dp),
            )
            Row(
                Modifier.fillMaxWidth().padding(top = 6.dp),
                horizontalArrangement = Arrangement.End,
            ) {
                SheetPrimaryButton(
                    label = "Save note",
                    onClick = { onSaveNote(noteText) },
                )
            }

            Spacer(Modifier.height(18.dp))
            RdOverlineText("Source")
            Spacer(Modifier.height(8.dp))
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
    var noteText by remember(staged.id) { mutableStateOf(staged.note.orEmpty()) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
        ) {
            RdOverlineText("Pending · seq #${staged.sequenceNumber}")
            Spacer(Modifier.height(6.dp))
            HeroNumericRow(
                valueText = formatGlucose(staged.mgPerDl, unit),
                unitText = unitLabel(unit),
                dateText = detailTimeFormatter.format(
                    staged.time.atZone(ZoneId.systemDefault())
                ),
            )

            Spacer(Modifier.height(22.dp))
            RdOverlineText("Meal")
            Spacer(Modifier.height(8.dp))
            MealChips(
                selected = staged.effectiveMeal,
                onSelect = { meal ->
                    onUpdate { it.copy(userMeal = if (meal == staged.meterMeal) null else meal) }
                },
            )
            if (staged.userMeal != null && staged.userMeal != staged.meterMeal) {
                Spacer(Modifier.height(6.dp))
                Text(
                    "Meter reported: ${mealLabel(staged.meterMeal)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = rdSubtle(),
                )
            }

            Spacer(Modifier.height(18.dp))
            RdOverlineText("Feeling")
            Spacer(Modifier.height(8.dp))
            FeelingChips(
                selected = staged.feeling,
                onSelect = { f -> onUpdate { it.copy(feeling = f) } },
            )

            Spacer(Modifier.height(18.dp))
            RdOverlineText("Note")
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = noteText,
                onValueChange = { noteText = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Optional: carbs, insulin, context…") },
                minLines = 2,
                maxLines = 4,
                shape = RoundedCornerShape(8.dp),
            )
            Row(
                Modifier.fillMaxWidth().padding(top = 6.dp),
                horizontalArrangement = Arrangement.End,
            ) {
                SheetPrimaryButton(
                    label = "Save note",
                    onClick = {
                        val cleaned = noteText.trim().ifEmpty { null }
                        onUpdate { it.copy(note = cleaned) }
                    },
                )
            }

            Spacer(Modifier.height(20.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                SheetGhostButton(
                    label = "Discard",
                    onClick = {
                        onDiscard(); onDismiss()
                    },
                    modifier = Modifier.weight(1f),
                )
                SheetPrimaryButton(
                    label = "Send to HC",
                    onClick = {
                        onPush(); onDismiss()
                    },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

// ─────────── Hero numeric row ───────────

@Composable
private fun HeroNumericRow(
    valueText: String,
    unitText: String,
    dateText: String,
) {
    Column {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                valueText,
                style = RdMono.Display,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                unitText,
                style = MaterialTheme.typography.bodyMedium,
                color = rdSubtle(),
                modifier = Modifier.padding(start = 8.dp, bottom = 8.dp),
            )
        }
        Text(
            dateText,
            style = RdMono.Caption,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

// ─────────── Meta row ───────────

@Composable
private fun MetaRow(label: String, value: String) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp),
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = rdSubtle(),
            modifier = Modifier.width(90.dp),
        )
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
    }
}

// ─────────── Outlined chip selector (meal / feeling) ───────────

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun MealChips(selected: Int, onSelect: (Int) -> Unit) {
    val options = listOf(
        BloodGlucoseRecord.RELATION_TO_MEAL_UNKNOWN to "None",
        BloodGlucoseRecord.RELATION_TO_MEAL_BEFORE_MEAL to "Before meal",
        BloodGlucoseRecord.RELATION_TO_MEAL_AFTER_MEAL to "After meal",
        BloodGlucoseRecord.RELATION_TO_MEAL_FASTING to "Fasting",
        BloodGlucoseRecord.RELATION_TO_MEAL_GENERAL to "General",
    )
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        options.forEach { (code, label) ->
            OutlinedChip(
                selected = selected == code,
                label = label,
                onClick = { onSelect(code) },
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun FeelingChips(selected: Feeling?, onSelect: (Feeling?) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Feeling.entries.forEach { f ->
            FeelingTile(
                selected = selected == f,
                emoji = f.emoji,
                label = f.label,
                onClick = { onSelect(if (selected == f) null else f) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun OutlinedChip(
    selected: Boolean,
    label: String,
    onClick: () -> Unit,
) {
    val border = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline
    val bg = if (selected) MaterialTheme.colorScheme.onSurface else Color.Transparent
    val fg = if (selected) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.onSurface
    Box(
        Modifier
            .border(1.dp, border, RoundedCornerShape(8.dp))
            .background(bg, RoundedCornerShape(8.dp))
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
            color = fg,
        )
    }
}

@Composable
private fun FeelingTile(
    selected: Boolean,
    emoji: String,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val border = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline
    val bg = if (selected) MaterialTheme.colorScheme.surfaceContainerHigh else Color.Transparent
    Column(
        modifier = modifier
            .border(1.dp, border, RoundedCornerShape(8.dp))
            .background(bg, RoundedCornerShape(8.dp))
            .clickable { onClick() }
            .padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(emoji, style = MaterialTheme.typography.bodyLarge)
        Text(
            label,
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

// ─────────── Buttons (primary / ghost) — match redesign btn styles ───────────

@Composable
internal fun SheetPrimaryButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .clickable { onClick() }
            .background(MaterialTheme.colorScheme.primary, CircleShape)
            .padding(horizontal = 18.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onPrimary,
        )
    }
}

@Composable
internal fun SheetGhostButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
            .clickable { onClick() }
            .padding(horizontal = 18.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

// ─────────── Helpers ───────────

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
