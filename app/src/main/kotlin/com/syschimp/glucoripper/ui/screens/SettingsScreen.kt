package com.syschimp.glucoripper.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.syschimp.glucoripper.data.AutoPushMode
import com.syschimp.glucoripper.data.GlucoseUnit
import com.syschimp.glucoripper.data.SyncHistoryEntry
import com.syschimp.glucoripper.ui.UiState
import com.syschimp.glucoripper.ui.components.SectionHeader
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val historyFormatter = DateTimeFormatter.ofPattern("MMM d · h:mm a")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    state: UiState,
    onSaveUnit: (GlucoseUnit) -> Unit,
    onSaveRange: (Double, Double) -> Unit,
    onSaveAutoPushMode: (AutoPushMode) -> Unit,
    onExportCsv: () -> Unit,
    onClearHistory: () -> Unit,
) {
    var showSyncHistory by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Settings", fontWeight = FontWeight.SemiBold) },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
        containerColor = MaterialTheme.colorScheme.surface,
    ) { inner ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner)
                .padding(horizontal = 16.dp)
                .padding(top = 8.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SectionHeader("Readings")
            UnitCard(
                currentUnit = state.prefs.unit,
                onSaveUnit = onSaveUnit,
            )
            TargetRangeCard(
                initialLow = state.prefs.targetLowMgDl,
                initialHigh = state.prefs.targetHighMgDl,
                onSaveRange = onSaveRange,
            )

            SectionHeader("Sync")
            AutoPushCard(
                current = state.prefs.autoPushMode,
                onSelect = onSaveAutoPushMode,
            )
            ActionRowCard(
                icon = Icons.Outlined.ChevronRight,
                label = "Sync history",
                subtitle = "${state.syncHistory.size} recorded event${if (state.syncHistory.size == 1) "" else "s"}",
                onClick = { showSyncHistory = true },
            )

            SectionHeader("Data")
            ActionRowCard(
                icon = Icons.Outlined.FileDownload,
                label = "Export readings to CSV",
                subtitle = "Up to the 1,000 most recent readings",
                onClick = onExportCsv,
            )
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

@Composable
private fun UnitCard(
    currentUnit: GlucoseUnit,
    onSaveUnit: (GlucoseUnit) -> Unit,
) {
    SettingsCard {
        Text(
            "Display units",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            val options = listOf(
                GlucoseUnit.MG_PER_DL to "mg/dL",
                GlucoseUnit.MMOL_PER_L to "mmol/L",
            )
            options.forEachIndexed { index, (u, label) ->
                SegmentedButton(
                    selected = currentUnit == u,
                    onClick = { onSaveUnit(u) },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                ) { Text(label) }
            }
        }
    }
}

@Composable
private fun TargetRangeCard(
    initialLow: Double,
    initialHigh: Double,
    onSaveRange: (Double, Double) -> Unit,
) {
    var lowText by remember(initialLow) { mutableStateOf("%.0f".format(initialLow)) }
    var highText by remember(initialHigh) { mutableStateOf("%.0f".format(initialHigh)) }

    SettingsCard {
        Text(
            "Target range (mg/dL)",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            "Readings outside this band are flagged as low or high.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(
                value = lowText,
                onValueChange = { lowText = it.filter { c -> c.isDigit() } },
                label = { Text("Low") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth().weight(1f),
                singleLine = true,
            )
            OutlinedTextField(
                value = highText,
                onValueChange = { highText = it.filter { c -> c.isDigit() } },
                label = { Text("High") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth().weight(1f),
                singleLine = true,
            )
        }
        Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
            Button(onClick = {
                val low = lowText.toDoubleOrNull() ?: initialLow
                val high = highText.toDoubleOrNull() ?: initialHigh
                if (high > low) onSaveRange(low, high)
            }) { Text("Save") }
        }
    }
}

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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SyncHistorySheet(
    entries: List<SyncHistoryEntry>,
    onDismiss: () -> Unit,
    onClear: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 20.dp),
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
            Spacer(Modifier.height(8.dp))
            if (entries.isEmpty()) {
                Text(
                    "Nothing synced yet.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                entries.forEach { e ->
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            if (e.success) Icons.Default.CheckCircle else Icons.Default.Error,
                            contentDescription = null,
                            tint = if (e.success) Color(0xFF30A46C) else Color(0xFFE5484D),
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                historyFormatter.format(
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
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                    )
                }
            }
        }
    }
}
