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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bloodtype
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Science
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import android.content.IntentSender
import com.syschimp.glucoripper.data.AutoPushMode
import com.syschimp.glucoripper.data.GlucoseUnit
import com.syschimp.glucoripper.data.SyncHistoryEntry
import com.syschimp.glucoripper.data.ThemeMode
import com.syschimp.glucoripper.shared.mgDlToMmol
import com.syschimp.glucoripper.shared.mmolToMgDl
import com.syschimp.glucoripper.ui.PairedMeter
import com.syschimp.glucoripper.ui.UiState
import com.syschimp.glucoripper.ui.components.RdHairline
import com.syschimp.glucoripper.ui.components.RdOverlineText
import com.syschimp.glucoripper.ui.components.RdSegmented
import com.syschimp.glucoripper.ui.components.RdSegmentedOption
import com.syschimp.glucoripper.ui.components.rdSubtle
import com.syschimp.glucoripper.ui.format.unitLabel
import com.syschimp.glucoripper.ui.theme.RdMono
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

private val historyFormatter = DateTimeFormatter.ofPattern("MMM d · h:mm a")

@Composable
fun SettingsScreen(
    state: UiState,
    onPairMeter: suspend () -> IntentSender,
    onPairFinished: () -> Unit,
    onSyncMeter: (PairedMeter) -> Unit,
    onForceResyncMeter: (PairedMeter) -> Unit,
    onUnpairMeter: (PairedMeter) -> Unit,
    onRequestHealthPermissions: () -> Unit,
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
                top = 14.dp,
                bottom = 32.dp,
            ),
        ) {
            // Devices block (kept — pairing flow is non-trivial)
            item {
                DevicesSection(
                    state = state,
                    onPairMeter = onPairMeter,
                    onPairFinished = onPairFinished,
                    onSync = onSyncMeter,
                    onForceResync = onForceResyncMeter,
                    onUnpair = onUnpairMeter,
                    onRequestHealthPermissions = onRequestHealthPermissions,
                )
            }

            // Display units
            item {
                Section("Display units") {
                    RdSegmented(
                        options = listOf(
                            RdSegmentedOption("mg/dL", "mg/dL", Icons.Outlined.Bloodtype),
                            RdSegmentedOption("mmol/L", "mmol/L", Icons.Outlined.Science),
                        ),
                        selected = if (state.prefs.unit == GlucoseUnit.MG_PER_DL) "mg/dL" else "mmol/L",
                        onSelect = {
                            onSaveUnit(if (it == "mg/dL") GlucoseUnit.MG_PER_DL else GlucoseUnit.MMOL_PER_L)
                        },
                    )
                }
            }

            // Targets
            item {
                Section("Targets · ${unitLabel(state.prefs.unit)}") {}
            }
            item {
                TargetRow(
                    label = "General",
                    sub = "No meal context",
                    low = state.prefs.targetLowMgDl,
                    high = state.prefs.targetHighMgDl,
                    unit = state.prefs.unit,
                    minMgDl = 40.0,
                    maxMgDl = 250.0,
                    onSave = onSaveRange,
                )
            }
            item {
                TargetRow(
                    label = "Fasting",
                    sub = "First reading of day",
                    low = state.prefs.fastingLowMgDl,
                    high = state.prefs.fastingHighMgDl,
                    unit = state.prefs.unit,
                    minMgDl = 40.0,
                    maxMgDl = 250.0,
                    onSave = onSaveFastingRange,
                )
            }
            item {
                TargetRow(
                    label = "Pre-meal",
                    sub = "Before eating",
                    low = state.prefs.preMealLowMgDl,
                    high = state.prefs.preMealHighMgDl,
                    unit = state.prefs.unit,
                    minMgDl = 40.0,
                    maxMgDl = 250.0,
                    onSave = onSavePreMealRange,
                )
            }
            item {
                TargetRow(
                    label = "Post-meal",
                    sub = "1–2h after eating",
                    low = state.prefs.postMealLowMgDl,
                    high = state.prefs.postMealHighMgDl,
                    unit = state.prefs.unit,
                    minMgDl = 40.0,
                    maxMgDl = 300.0,
                    onSave = onSavePostMealRange,
                )
            }
            item {
                TargetRow(
                    label = "Chart range",
                    sub = "Y-axis bounds",
                    low = state.prefs.chartMinMgDl,
                    high = state.prefs.chartMaxMgDl,
                    unit = state.prefs.unit,
                    minMgDl = 0.0,
                    maxMgDl = 400.0,
                    onSave = onSaveChartRange,
                )
            }
            item {
                BufferRow(
                    value = state.prefs.warningBufferMgDl,
                    unit = state.prefs.unit,
                    onSave = onSaveWarningBuffer,
                )
            }

            // Theme
            item {
                Section("Theme") {
                    RdSegmented(
                        options = ThemeMode.entries.map { RdSegmentedOption(it.name, it.label) },
                        selected = state.prefs.themeMode.name,
                        onSelect = { onSaveThemeMode(ThemeMode.valueOf(it)) },
                    )
                }
            }

            // Auto-push
            item {
                Section("Auto-push to Health Connect") {
                    Text(
                        when (state.prefs.autoPushMode) {
                            AutoPushMode.OFF -> "Staged readings wait until you tap Send."
                            AutoPushMode.AFTER_SYNC -> "Readings push immediately after each BLE sync."
                            else -> "Readings push on a schedule even when the app is closed."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 4.dp),
                    )
                }
            }
            items_(AutoPushMode.entries) { mode ->
                AutoPushOptionRow(
                    label = mode.label,
                    selected = state.prefs.autoPushMode == mode,
                    onClick = { onSaveAutoPushMode(mode) },
                    isLast = mode == AutoPushMode.entries.last(),
                )
            }

            item { Spacer(Modifier.height(24.dp)) }

            // Footer link rows
            item {
                LinkRow(
                    icon = Icons.Outlined.History,
                    title = "Sync history",
                    subtitle = "${state.syncHistory.size} recorded event${if (state.syncHistory.size == 1) "" else "s"}",
                    onClick = { showSyncHistory = true },
                )
            }
            item { Spacer(Modifier.height(8.dp)) }
            item {
                LinkRow(
                    icon = Icons.Outlined.FileDownload,
                    title = "Export to CSV",
                    subtitle = "Up to 1,000 most recent readings",
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

private fun <T> androidx.compose.foundation.lazy.LazyListScope.items_(
    list: List<T>,
    content: @Composable (T) -> Unit,
) = items(count = list.size) { content(list[it]) }

// ─────────── Section header ───────────

@Composable
private fun Section(overline: String, content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 28.dp, bottom = 10.dp),
    ) {
        RdOverlineText(overline)
        Spacer(Modifier.height(10.dp))
        content()
    }
}

// ─────────── Target/range/chart row ───────────

@Composable
private fun TargetRow(
    label: String,
    sub: String,
    low: Double,
    high: Double,
    unit: GlucoseUnit,
    minMgDl: Double,
    maxMgDl: Double,
    onSave: (Double, Double) -> Unit,
) {
    var lowState by remember(low, high) { mutableStateOf(low) }
    var highState by remember(low, high) { mutableStateOf(high) }
    Column(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 14.dp),
    ) {
        Row(
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    label,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.Medium,
                    ),
                )
                Text(
                    sub,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = androidx.compose.ui.unit.TextUnit(10f, androidx.compose.ui.unit.TextUnitType.Sp),
                    ),
                    color = rdSubtle(),
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            Text(
                "${displayInUnit(lowState, unit)}–${displayInUnit(highState, unit)}",
                style = RdMono.RowSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        Spacer(Modifier.height(10.dp))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            GlucoseNumberField(
                label = "Low",
                valueMgDl = lowState,
                unit = unit,
                minMgDl = minMgDl,
                maxMgDl = maxMgDl,
                onCommitMgDl = { newLow ->
                    if (newLow < highState) {
                        lowState = newLow
                        onSave(newLow, highState)
                    }
                },
                modifier = Modifier.weight(1f),
            )
            GlucoseNumberField(
                label = "High",
                valueMgDl = highState,
                unit = unit,
                minMgDl = minMgDl,
                maxMgDl = maxMgDl,
                onCommitMgDl = { newHigh ->
                    if (newHigh > lowState) {
                        highState = newHigh
                        onSave(lowState, newHigh)
                    }
                },
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(Modifier.height(10.dp))
        RdHairline()
    }
}

@Composable
private fun BufferRow(
    value: Double,
    unit: GlucoseUnit,
    onSave: (Double) -> Unit,
) {
    var v by remember(value) { mutableStateOf(value) }
    Column(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 14.dp),
    ) {
        Row(
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    "Warning buffer",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.Medium,
                    ),
                )
                Text(
                    "Amber cushion ± ${unitLabel(unit)}",
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = androidx.compose.ui.unit.TextUnit(10f, androidx.compose.ui.unit.TextUnitType.Sp),
                    ),
                    color = rdSubtle(),
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            Text(
                "± ${displayInUnit(v, unit)}",
                style = RdMono.RowSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        Spacer(Modifier.height(10.dp))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            GlucoseNumberField(
                label = "± Buffer",
                valueMgDl = v,
                unit = unit,
                minMgDl = 0.0,
                maxMgDl = 50.0,
                onCommitMgDl = {
                    v = it
                    onSave(it)
                },
                modifier = Modifier.weight(1f),
            )
            Text(
                if (v.roundToInt() == 0) "Off" else "Both sides of range",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(Modifier.height(10.dp))
        RdHairline()
    }
}

private fun displayInUnit(mgDl: Double, unit: GlucoseUnit): String = when (unit) {
    GlucoseUnit.MG_PER_DL -> mgDl.roundToInt().toString()
    GlucoseUnit.MMOL_PER_L -> "%.1f".format(Locale.US, mgDl.mgDlToMmol())
}

// ─────────── Auto-push option row ───────────

@Composable
private fun AutoPushOptionRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    isLast: Boolean,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier
                .size(14.dp)
                .background(
                    color = androidx.compose.ui.graphics.Color.Transparent,
                    shape = CircleShape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier
                    .size(14.dp)
                    .border(
                        width = 1.5.dp,
                        color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline,
                        shape = CircleShape,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                if (selected) {
                    Box(
                        Modifier
                            .size(6.dp)
                            .background(MaterialTheme.colorScheme.onSurface, CircleShape),
                    )
                }
            }
        }
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
    }
    if (!isLast) RdHairline()
}

// ─────────── Link row (footer) ───────────

@Composable
private fun LinkRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String?,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                MaterialTheme.colorScheme.surfaceContainerLow,
                RoundedCornerShape(12.dp),
            )
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp),
        )
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.Medium,
                ),
            )
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = androidx.compose.ui.unit.TextUnit(10f, androidx.compose.ui.unit.TextUnitType.Sp),
                    ),
                    color = rdSubtle(),
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
        Icon(
            Icons.Outlined.ChevronRight,
            contentDescription = null,
            tint = rdSubtle(),
            modifier = Modifier.size(14.dp),
        )
    }
}

// ─────────── GlucoseNumberField (numeric input for targets) ───────────

@Composable
private fun GlucoseNumberField(
    label: String,
    valueMgDl: Double,
    unit: GlucoseUnit,
    minMgDl: Double,
    maxMgDl: Double,
    onCommitMgDl: (Double) -> Unit,
    modifier: Modifier = Modifier,
) {
    val canonical = formatForUnit(valueMgDl, unit)
    var draft by remember(valueMgDl, unit) { mutableStateOf(canonical) }
    var hadFocus by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current

    fun commit() {
        val parsed = draft.replace(',', '.').toDoubleOrNull()
        if (parsed == null) {
            draft = canonical
            return
        }
        val mgDl = when (unit) {
            GlucoseUnit.MG_PER_DL -> parsed
            GlucoseUnit.MMOL_PER_L -> parsed.mmolToMgDl()
        }.coerceIn(minMgDl, maxMgDl)
        onCommitMgDl(mgDl)
        draft = canonical
    }

    OutlinedTextField(
        value = draft,
        onValueChange = { new ->
            if (new.isEmpty() || new.matches(allowedCharsRegex(unit))) draft = new
        },
        label = { Text(label) },
        suffix = {
            Text(
                unitLabel(unit),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        singleLine = true,
        keyboardOptions = KeyboardOptions(
            keyboardType = when (unit) {
                GlucoseUnit.MG_PER_DL -> KeyboardType.Number
                GlucoseUnit.MMOL_PER_L -> KeyboardType.Decimal
            },
            imeAction = ImeAction.Done,
        ),
        keyboardActions = KeyboardActions(onDone = {
            commit()
            focusManager.clearFocus()
        }),
        modifier = modifier.onFocusChanged { state ->
            if (hadFocus && !state.isFocused) commit()
            hadFocus = state.isFocused
        },
    )
}

private fun formatForUnit(mgDl: Double, unit: GlucoseUnit): String = when (unit) {
    GlucoseUnit.MG_PER_DL -> mgDl.roundToInt().toString()
    GlucoseUnit.MMOL_PER_L -> "%.1f".format(Locale.US, mgDl.mgDlToMmol())
}

private fun allowedCharsRegex(unit: GlucoseUnit): Regex = when (unit) {
    GlucoseUnit.MG_PER_DL -> Regex("""\d{1,3}""")
    GlucoseUnit.MMOL_PER_L -> Regex("""\d{1,2}([.,]\d{0,1})?""")
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
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    ),
                ) {
                    Text(
                        "No sync attempts recorded yet.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            } else {
                entries.forEach { entry ->
                    SyncHistoryRow(entry)
                }
            }
        }
    }
}

@Composable
private fun SyncHistoryRow(entry: SyncHistoryEntry) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            val summary = if (entry.success) {
                "Synced · ${entry.pulled} pulled · ${entry.written} written"
            } else {
                "Failed · ${entry.message ?: "no detail"}"
            }
            Text(
                summary,
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                historyFormatter.format(
                    Instant.ofEpochMilli(entry.timestampMillis).atZone(ZoneId.systemDefault())
                ),
                style = RdMono.Caption,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
