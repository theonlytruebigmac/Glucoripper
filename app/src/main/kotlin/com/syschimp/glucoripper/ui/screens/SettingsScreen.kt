package com.syschimp.glucoripper.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.outlined.Bloodtype
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.Science
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.syschimp.glucoripper.data.AutoPushMode
import com.syschimp.glucoripper.data.GlucoseUnit
import com.syschimp.glucoripper.data.SyncHistoryEntry
import com.syschimp.glucoripper.data.ThemeMode
import com.syschimp.glucoripper.ui.UiState
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val historyFormatter = DateTimeFormatter.ofPattern("MMM d · h:mm a")

@Composable
fun SettingsScreen(
    state: UiState,
    onSaveUnit: (GlucoseUnit) -> Unit,
    onSaveRange: (Double, Double) -> Unit,
    onSaveFastingRange: (Double, Double) -> Unit,
    onSavePreMealRange: (Double, Double) -> Unit,
    onSavePostMealRange: (Double, Double) -> Unit,
    onSaveWarningBuffer: (Double) -> Unit,
    onSaveChartRange: (Double, Double) -> Unit,
    onSaveThemeMode: (ThemeMode) -> Unit,
    onSaveAutoPushMode: (AutoPushMode) -> Unit,
    onExportCsv: () -> Unit,
    onClearHistory: () -> Unit,
) {
    var showSyncHistory by remember { mutableStateOf(false) }

    Scaffold(
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0),
        containerColor = MaterialTheme.colorScheme.surface,
    ) { inner ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(inner),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = 4.dp,
                bottom = 24.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                Box(Modifier.fillMaxWidth()) {
                    Text(
                        "Settings",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.align(Alignment.Center),
                    )
                }
            }

            item {
                UnitCard(
                    currentUnit = state.prefs.unit,
                    onSaveUnit = onSaveUnit,
                )
            }
            item {
                TargetRangeCard(
                    title = "General Target",
                    subtitle = "Used when no meal context is recorded.",
                    initialLow = state.prefs.targetLowMgDl,
                    initialHigh = state.prefs.targetHighMgDl,
                    onSaveRange = onSaveRange,
                )
            }
            item {
                TargetRangeCard(
                    title = "Fasting Target",
                    subtitle = "First reading of the day, before eating.",
                    initialLow = state.prefs.fastingLowMgDl,
                    initialHigh = state.prefs.fastingHighMgDl,
                    onSaveRange = onSaveFastingRange,
                )
            }
            item {
                TargetRangeCard(
                    title = "Before Meal Target",
                    subtitle = "Pre-prandial checks.",
                    initialLow = state.prefs.preMealLowMgDl,
                    initialHigh = state.prefs.preMealHighMgDl,
                    onSaveRange = onSavePreMealRange,
                )
            }
            item {
                TargetRangeCard(
                    title = "After Meal Target",
                    subtitle = "1–2 hours after eating.",
                    initialLow = state.prefs.postMealLowMgDl,
                    initialHigh = state.prefs.postMealHighMgDl,
                    onSaveRange = onSavePostMealRange,
                    sliderMax = 300f,
                )
            }
            item {
                WarningBufferCard(
                    initial = state.prefs.warningBufferMgDl,
                    onSave = onSaveWarningBuffer,
                )
            }
            item {
                ChartRangeCard(
                    initialMin = state.prefs.chartMinMgDl,
                    initialMax = state.prefs.chartMaxMgDl,
                    onSaveRange = onSaveChartRange,
                )
            }
            item {
                ThemeCard(
                    current = state.prefs.themeMode,
                    onSelect = onSaveThemeMode,
                )
            }
            item {
                AutoPushCard(
                    current = state.prefs.autoPushMode,
                    onSelect = onSaveAutoPushMode,
                )
            }
            item {
                ActionRowCard(
                    icon = Icons.Outlined.ChevronRight,
                    label = "Sync history",
                    subtitle = "${state.syncHistory.size} recorded event${if (state.syncHistory.size == 1) "" else "s"}",
                    onClick = { showSyncHistory = true },
                )
            }
            item {
                ActionRowCard(
                    icon = Icons.Outlined.FileDownload,
                    label = "Export readings to CSV",
                    subtitle = "Up to the 1,000 most recent readings",
                    onClick = onExportCsv,
                )
            }
        }
    }

    if (showSyncHistory) {
        SyncHistorySheet(
            entries = state.syncHistory,
            onDismiss = { showSyncHistory = false },
            onClear = onClearHistory,
        )
    }
}

// ─────────── Display Units ───────────

@Composable
private fun UnitCard(
    currentUnit: GlucoseUnit,
    onSaveUnit: (GlucoseUnit) -> Unit,
) {
    SettingsCard {
        Text(
            "Display Units",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            "(mg/dL  |  mmol/L)",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            UnitTile(
                selected = currentUnit == GlucoseUnit.MG_PER_DL,
                icon = Icons.Outlined.Bloodtype,
                label = "mg/dL",
                onClick = { onSaveUnit(GlucoseUnit.MG_PER_DL) },
                modifier = Modifier.weight(1f),
            )
            UnitTile(
                selected = currentUnit == GlucoseUnit.MMOL_PER_L,
                icon = Icons.Outlined.Science,
                label = "mmol/L",
                onClick = { onSaveUnit(GlucoseUnit.MMOL_PER_L) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun UnitTile(
    selected: Boolean,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val border = if (selected) MaterialTheme.colorScheme.primary
    else MaterialTheme.colorScheme.outlineVariant
    val container = if (selected) MaterialTheme.colorScheme.primaryContainer
    else MaterialTheme.colorScheme.surfaceContainer
    val onContainer = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
    else MaterialTheme.colorScheme.onSurface
    Column(
        modifier = modifier
            .height(80.dp)
            .background(container, RoundedCornerShape(16.dp))
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = border,
                shape = RoundedCornerShape(16.dp),
            )
            .clickable { onClick() }
            .padding(12.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Icon(icon, contentDescription = null, tint = onContainer, modifier = Modifier.size(28.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = onContainer,
        )
    }
}

// ─────────── Target Range ───────────

@Composable
private fun TargetRangeCard(
    title: String,
    subtitle: String,
    initialLow: Double,
    initialHigh: Double,
    onSaveRange: (Double, Double) -> Unit,
    sliderMin: Float = 40f,
    sliderMax: Float = 250f,
) {
    var range by remember(initialLow, initialHigh) {
        mutableStateOf(initialLow.toFloat()..initialHigh.toFloat())
    }

    SettingsCard {
        Text(
            "$title (mg/dL)",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            ValuePill(label = "Low", value = range.start.toInt())
            ValuePill(label = "High", value = range.endInclusive.toInt())
        }
        RangeSlider(
            value = range,
            onValueChange = { range = it },
            onValueChangeFinished = {
                onSaveRange(range.start.toDouble(), range.endInclusive.toDouble())
            },
            valueRange = sliderMin..sliderMax,
            steps = ((sliderMax - sliderMin) / 5).toInt() - 1,
        )
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(sliderMin.toInt().toString(), style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(sliderMax.toInt().toString(), style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun WarningBufferCard(
    initial: Double,
    onSave: (Double) -> Unit,
) {
    var value by remember(initial) { mutableStateOf(initial.toFloat()) }
    SettingsCard {
        Text(
            "Warning Buffer (mg/dL)",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            "Amber cushion around each target. Readings inside the buffer show as 'warning' instead of high/low. Set to 0 for strict red/green.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            ValuePill(label = "±", value = value.toInt())
            Text(
                if (value.toInt() == 0) "Off" else "Applied both sides of range",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Slider(
            value = value,
            onValueChange = { value = it },
            onValueChangeFinished = { onSave(value.toDouble()) },
            valueRange = 0f..50f,
            steps = 9,
        )
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("0", style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("50", style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ChartRangeCard(
    initialMin: Double,
    initialMax: Double,
    onSaveRange: (Double, Double) -> Unit,
) {
    var range by remember(initialMin, initialMax) {
        mutableStateOf(initialMin.toFloat()..initialMax.toFloat())
    }
    SettingsCard {
        Text(
            "Chart Range (mg/dL)",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            "Y-axis bounds for the dashboard chart.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            ValuePill(label = "Min", value = range.start.toInt())
            ValuePill(label = "Max", value = range.endInclusive.toInt())
        }
        RangeSlider(
            value = range,
            onValueChange = { range = it },
            onValueChangeFinished = {
                onSaveRange(range.start.toDouble(), range.endInclusive.toDouble())
            },
            valueRange = 0f..400f,
            steps = 400 / 10 - 1,
        )
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("0", style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("400", style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ValuePill(label: String, value: Int) {
    Row(
        modifier = Modifier
            .background(
                MaterialTheme.colorScheme.surfaceContainer,
                RoundedCornerShape(20.dp),
            )
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(6.dp))
        Text(
            value.toString(),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

// ─────────── Theme ───────────

@Composable
private fun ThemeCard(
    current: ThemeMode,
    onSelect: (ThemeMode) -> Unit,
) {
    SettingsCard {
        Text(
            "Theme",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        ThemeMode.entries.forEach { mode ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .selectable(
                        selected = current == mode,
                        onClick = { onSelect(mode) },
                        role = Role.RadioButton,
                    )
                    .padding(vertical = 6.dp),
            ) {
                RadioButton(
                    selected = current == mode,
                    onClick = { onSelect(mode) },
                )
                Spacer(Modifier.width(8.dp))
                Text(mode.label, style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}

// ─────────── Auto-push ───────────

@Composable
private fun AutoPushCard(
    current: AutoPushMode,
    onSelect: (AutoPushMode) -> Unit,
) {
    SettingsCard {
        Text(
            "Auto-push to Health Connect",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            when (current) {
                AutoPushMode.OFF -> "Staged readings wait until you tap Send."
                AutoPushMode.AFTER_SYNC -> "Readings push immediately after each BLE sync."
                else -> "Readings push on a schedule even when the app is closed."
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(4.dp))
        AutoPushMode.entries.forEach { mode ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .selectable(
                        selected = current == mode,
                        onClick = { onSelect(mode) },
                        role = Role.RadioButton,
                    )
                    .padding(vertical = 6.dp),
            ) {
                RadioButton(
                    selected = current == mode,
                    onClick = { onSelect(mode) },
                )
                Spacer(Modifier.width(8.dp))
                Text(mode.label, style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}

// ─────────── Action row ───────────

@Composable
private fun ActionRowCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    subtitle: String?,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable { onClick() },
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium)
                if (subtitle != null) {
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SettingsCard(content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Column(
            Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) { content() }
    }
}

// ─────────── Sync history sheet ───────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SyncHistorySheet(
    entries: List<SyncHistoryEntry>,
    onDismiss: () -> Unit,
    onClear: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    "Sync history",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                TextButton(onClick = onClear) { Text("Clear") }
            }
            if (entries.isEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    ),
                ) {
                    Text(
                        "Nothing synced yet.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            } else {
                entries.forEach { e -> SyncHistoryRow(e) }
            }
        }
    }
}

@Composable
private fun SyncHistoryRow(entry: SyncHistoryEntry) {
    val success = entry.success
    val color = if (success) Color(0xFF30A46C) else Color(0xFFE5484D)
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(color.copy(alpha = 0.15f), RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    if (success) Icons.Default.CheckCircle else Icons.Default.Error,
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(20.dp),
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    historyFormatter.format(
                        Instant.ofEpochMilli(entry.timestampMillis)
                            .atZone(ZoneId.systemDefault())
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    if (success) {
                        "Pulled ${entry.pulled}, wrote ${entry.written}" +
                                if (entry.skippedControl > 0) ", skipped ${entry.skippedControl}" else ""
                    } else (entry.message ?: "Failed"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
