package com.syschimp.glucoripper.ui

import android.content.IntentSender
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material.icons.filled.BluetoothConnected
import androidx.compose.material.icons.filled.BluetoothDisabled
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.Bloodtype
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.HealthAndSafety
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.health.connect.client.records.BloodGlucoseRecord
import com.syschimp.glucoripper.data.GlucoseUnit
import com.syschimp.glucoripper.data.ReadingAnnotation
import com.syschimp.glucoripper.ui.format.formatGlucose
import com.syschimp.glucoripper.ui.format.unitLabel
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val rowTimeFormatter = DateTimeFormatter.ofPattern("MMM d, h:mm a")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    viewModel: MainViewModel,
    onPairMeter: suspend () -> IntentSender,
    onRequestHealthPermissions: () -> Unit,
    onExportCsv: () -> Unit,
) {
    val state by viewModel.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var selectedReading by remember { mutableStateOf<BloodGlucoseRecord?>(null) }
    var showSettings by remember { mutableStateOf(false) }
    var showHistory by remember { mutableStateOf(false) }
    var overflowOpen by remember { mutableStateOf(false) }

    LaunchedEffect(state.lastMessage) {
        state.lastMessage?.let {
            scope.launch { snackbarHostState.showSnackbar(it) }
        }
    }

    val pullState = rememberPullToRefreshState()

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Outlined.Bloodtype,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("Glucoripper", fontWeight = FontWeight.SemiBold)
                    }
                },
                actions = {
                    Box {
                        IconButton(onClick = { overflowOpen = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "More")
                        }
                        DropdownMenu(expanded = overflowOpen, onDismissRequest = { overflowOpen = false }) {
                            DropdownMenuItem(
                                text = { Text("Settings") },
                                leadingIcon = { Icon(Icons.Outlined.Settings, null) },
                                onClick = { overflowOpen = false; showSettings = true },
                            )
                            DropdownMenuItem(
                                text = { Text("Sync history") },
                                onClick = { overflowOpen = false; showHistory = true },
                            )
                            DropdownMenuItem(
                                text = { Text("Export CSV") },
                                onClick = { overflowOpen = false; onExportCsv() },
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) { Snackbar(it) } },
        containerColor = MaterialTheme.colorScheme.surface,
    ) { inner ->
        Column(Modifier.fillMaxSize().padding(inner)) {
            AnimatedVisibility(visible = state.syncing) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
            }
            PullToRefreshBox(
                isRefreshing = state.syncing,
                onRefresh = { state.meters.firstOrNull()?.let(viewModel::syncNow) ?: viewModel.refresh() },
                state = pullState,
            ) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    item { HeroCard(state.recentReadings.firstOrNull(), state.prefs.unit) }
                    item {
                        StatusChips(
                            bluetoothEnabled = state.bluetoothEnabled,
                            healthConnectState = state.healthConnectState,
                            lowBattery = state.lowBatteryFlag,
                            onGrantHealthPermissions = onRequestHealthPermissions,
                        )
                    }
                    item {
                        val tir = computeTimeInRange(
                            state.recentReadings,
                            state.prefs.targetLowMgDl,
                            state.prefs.targetHighMgDl,
                        )
                        TimeInRangeCard(tir, state.prefs.unit, state.prefs.targetLowMgDl, state.prefs.targetHighMgDl)
                    }
                    item { SectionHeader("Meters") }
                    if (state.meters.isEmpty()) {
                        item { EmptyMetersCard(onPairMeter) }
                    } else {
                        items(state.meters, key = { it.associationId }) { meter ->
                            MeterCard(
                                meter = meter,
                                syncing = state.syncing,
                                onSync = viewModel::syncNow,
                                onUnpair = viewModel::unpair,
                                onForceResync = viewModel::forceFullResync,
                            )
                        }
                        item { PairMeterButton(onPairMeter) }
                    }

                    if (state.recentReadings.isNotEmpty()) {
                        item { SectionHeader("Recent readings") }
                        items(state.recentReadings.drop(1).take(20)) { r ->
                            val ann = r.metadata.clientRecordId?.let(state.annotations::get)
                            ReadingRow(r, ann, state.prefs.unit, state.prefs.targetLowMgDl,
                                state.prefs.targetHighMgDl) { selectedReading = r }
                        }
                    }
                }
            }
        }
    }

    selectedReading?.let { r ->
        val ann = r.metadata.clientRecordId?.let(state.annotations::get)
        ReadingDetailSheet(
            reading = r,
            unit = state.prefs.unit,
            annotation = ann,
            onDismiss = { selectedReading = null },
            onSaveMeal = { viewModel.setMealRelation(r, it) },
            onSaveFeeling = { viewModel.setFeeling(r.metadata.clientRecordId, it) },
            onSaveNote = { viewModel.setNote(r.metadata.clientRecordId, it) },
        )
    }
    if (showSettings) {
        SettingsSheet(
            prefs = state.prefs,
            onDismiss = { showSettings = false },
            onSaveUnit = viewModel::setUnit,
            onSaveRange = viewModel::setTargetRange,
        )
    }
    if (showHistory) {
        SyncHistorySheet(
            entries = state.syncHistory,
            onDismiss = { showHistory = false },
            onClear = viewModel::clearSyncHistory,
        )
    }
}

// ──────────────── Hero ────────────────

@Composable
private fun HeroCard(latest: BloodGlucoseRecord?, unit: GlucoseUnit) {
    val mgDl = latest?.level?.inMilligramsPerDeciliter
    val (band, bandColor) = classify(mgDl)
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
        ),
    ) {
        Column(Modifier.padding(24.dp)) {
            Text(
                "Latest reading",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
            )
            Spacer(Modifier.height(8.dp))
            if (latest == null || mgDl == null) {
                Text(
                    "No readings yet",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "Pair your meter and tap Sync now to pull your history.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                )
            } else {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = formatGlucose(mgDl, unit),
                        style = MaterialTheme.typography.displayLarge,
                        fontSize = 72.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        unitLabel(unit),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                        modifier = Modifier.padding(bottom = 16.dp),
                    )
                }
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Dot(color = bandColor)
                    Spacer(Modifier.width(6.dp))
                    Text(
                        band,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                    Text(
                        " · " + relativeTime(latest.time),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                    )
                    relationShort(latest.relationToMeal)?.let {
                        Text(
                            " · $it",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                        )
                    }
                }
            }
        }
    }
}

// ──────────────── Status chips ────────────────

@Composable
private fun StatusChips(
    bluetoothEnabled: Boolean,
    healthConnectState: HealthConnectState,
    lowBattery: Boolean,
    onGrantHealthPermissions: () -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        AssistChip(
            onClick = {},
            label = { Text(if (bluetoothEnabled) "Bluetooth on" else "Bluetooth off") },
            leadingIcon = {
                Icon(
                    if (bluetoothEnabled) Icons.Default.BluetoothConnected
                    else Icons.Default.BluetoothDisabled,
                    contentDescription = null,
                    modifier = Modifier.size(AssistChipDefaults.IconSize),
                )
            },
            colors = AssistChipDefaults.assistChipColors(
                containerColor = if (bluetoothEnabled)
                    MaterialTheme.colorScheme.secondaryContainer
                else MaterialTheme.colorScheme.errorContainer,
            ),
        )
        AssistChip(
            onClick = {
                if (healthConnectState == HealthConnectState.NEEDS_PERMISSIONS) {
                    onGrantHealthPermissions()
                }
            },
            label = {
                Text(
                    when (healthConnectState) {
                        HealthConnectState.UNAVAILABLE -> "Health Connect missing"
                        HealthConnectState.NEEDS_PERMISSIONS -> "Grant HC access"
                        HealthConnectState.READY -> "Health Connect ready"
                    }
                )
            },
            leadingIcon = {
                Icon(
                    Icons.Outlined.HealthAndSafety,
                    contentDescription = null,
                    modifier = Modifier.size(AssistChipDefaults.IconSize),
                )
            },
            colors = AssistChipDefaults.assistChipColors(
                containerColor = when (healthConnectState) {
                    HealthConnectState.READY -> MaterialTheme.colorScheme.secondaryContainer
                    HealthConnectState.NEEDS_PERMISSIONS -> MaterialTheme.colorScheme.tertiaryContainer
                    HealthConnectState.UNAVAILABLE -> MaterialTheme.colorScheme.errorContainer
                },
            ),
        )
        if (lowBattery) {
            AssistChip(
                onClick = {},
                label = { Text("Meter battery low") },
                leadingIcon = {
                    Icon(
                        Icons.Default.BatteryAlert,
                        contentDescription = null,
                        modifier = Modifier.size(AssistChipDefaults.IconSize),
                    )
                },
                colors = AssistChipDefaults.assistChipColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                ),
            )
        }
    }
}

// ──────────────── Meters ────────────────

@Composable
private fun EmptyMetersCard(onPairMeter: suspend () -> IntentSender) {
    val scope = rememberCoroutineScope()
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Column(
            Modifier.fillMaxWidth().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                Icons.Outlined.FavoriteBorder,
                contentDescription = null,
                modifier = Modifier.size(40.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(8.dp))
            Text("No meter paired", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            Text(
                "Put your Contour Next One into pairing mode and tap Pair meter.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))
            Button(onClick = {
                scope.launch {
                    val sender = onPairMeter()
                    launcher.launch(IntentSenderRequest.Builder(sender).build())
                }
            }) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text("Pair meter")
            }
        }
    }
}

@Composable
private fun PairMeterButton(onPairMeter: suspend () -> IntentSender) {
    val scope = rememberCoroutineScope()
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { }
    FilledTonalButton(
        onClick = {
            scope.launch {
                val sender = onPairMeter()
                launcher.launch(IntentSenderRequest.Builder(sender).build())
            }
        },
        modifier = Modifier.fillMaxWidth(),
    ) {
        Icon(Icons.Default.Add, contentDescription = null)
        Spacer(Modifier.width(6.dp))
        Text("Pair another meter")
    }
}

@Composable
private fun MeterCard(
    meter: PairedMeter,
    syncing: Boolean,
    onSync: (PairedMeter) -> Unit,
    onUnpair: (PairedMeter) -> Unit,
    onForceResync: (PairedMeter) -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(44.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Default.BluetoothConnected,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    meter.displayName ?: "Glucose meter",
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    meter.lastSyncMillis?.let {
                        "Last sync " + relativeTime(Instant.ofEpochMilli(it))
                    } ?: "Never synced",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    meter.address,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                )
            }
            FilledIconButton(onClick = { onSync(meter) }, enabled = !syncing) {
                if (syncing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                    )
                } else {
                    Icon(Icons.Default.Refresh, contentDescription = "Sync now")
                }
            }
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = "More")
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text("Full resync") },
                        onClick = {
                            menuOpen = false
                            onForceResync(meter)
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Unpair") },
                        onClick = {
                            menuOpen = false
                            onUnpair(meter)
                        },
                    )
                }
            }
        }
    }
}

// ──────────────── Readings list ────────────────

@Composable
private fun ReadingRow(
    r: BloodGlucoseRecord,
    annotation: ReadingAnnotation?,
    unit: GlucoseUnit,
    lowMgDl: Double,
    highMgDl: Double,
    onClick: () -> Unit,
) {
    val mgDl = r.level.inMilligramsPerDeciliter
    val color = bandColor(mgDl, lowMgDl, highMgDl)
    val effectiveMeal = annotation?.mealOverride ?: r.relationToMeal
    Row(
        Modifier.fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Dot(color = color, size = 10.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                formatTime(r.time),
                style = MaterialTheme.typography.bodyMedium,
            )
            val subtitle = buildList {
                relationShort(effectiveMeal)?.let(::add)
                annotation?.feeling?.let { add("${it.emoji} ${it.label}") }
                annotation?.note?.takeIf { it.isNotBlank() }?.let {
                    add("“" + it.take(30) + if (it.length > 30) "…" else "" + "”")
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

// ──────────────── Small bits ────────────────

@Composable
private fun SectionHeader(text: String) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(top = 8.dp, start = 4.dp),
    )
}

@Composable
private fun Dot(color: Color, size: androidx.compose.ui.unit.Dp = 12.dp) {
    Box(
        Modifier
            .size(size)
            .background(color, shape = CircleShape),
    )
}

private fun classify(mgDl: Double?): Pair<String, Color> {
    if (mgDl == null) return "—" to Color.Gray
    return when {
        mgDl < 70 -> "Low" to Color(0xFFE53935)
        mgDl < 140 -> "In range" to Color(0xFF43A047)
        mgDl < 180 -> "Elevated" to Color(0xFFFB8C00)
        else -> "High" to Color(0xFFD81B60)
    }
}

private fun bandColor(mgDl: Double, lowMgDl: Double, highMgDl: Double): Color = when {
    mgDl < lowMgDl -> Color(0xFFE53935)
    mgDl > highMgDl -> Color(0xFFD81B60)
    else -> Color(0xFF43A047)
}

private fun relationShort(code: Int): String? = when (code) {
    BloodGlucoseRecord.RELATION_TO_MEAL_BEFORE_MEAL -> "Before meal"
    BloodGlucoseRecord.RELATION_TO_MEAL_AFTER_MEAL -> "After meal"
    BloodGlucoseRecord.RELATION_TO_MEAL_FASTING -> "Fasting"
    BloodGlucoseRecord.RELATION_TO_MEAL_GENERAL -> "General"
    else -> null
}

private fun formatTime(t: Instant): String =
    rowTimeFormatter.format(t.atZone(ZoneId.systemDefault()))

private fun relativeTime(t: Instant): String {
    val d = Duration.between(t, Instant.now())
    return when {
        d.isNegative -> "just now"
        d.toMinutes() < 1 -> "just now"
        d.toMinutes() < 60 -> "${d.toMinutes()} min ago"
        d.toHours() < 24 -> "${d.toHours()} h ago"
        d.toDays() < 7 -> "${d.toDays()} d ago"
        else -> formatTime(t)
    }
}
