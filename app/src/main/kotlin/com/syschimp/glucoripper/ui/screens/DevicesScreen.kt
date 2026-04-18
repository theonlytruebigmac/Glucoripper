package com.syschimp.glucoripper.ui.screens

import android.content.IntentSender
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.filled.BluetoothDisabled
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.Bluetooth
import androidx.compose.material.icons.outlined.Devices
import androidx.compose.material.icons.outlined.HealthAndSafety
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.syschimp.glucoripper.ui.HealthConnectState
import com.syschimp.glucoripper.ui.PairedMeter
import com.syschimp.glucoripper.ui.UiState
import com.syschimp.glucoripper.ui.components.SectionHeader
import com.syschimp.glucoripper.ui.components.relativeTime
import kotlinx.coroutines.launch
import java.time.Instant

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DevicesScreen(
    state: UiState,
    onPairMeter: suspend () -> IntentSender,
    onSync: (PairedMeter) -> Unit,
    onForceResync: (PairedMeter) -> Unit,
    onUnpair: (PairedMeter) -> Unit,
    onRequestHealthPermissions: () -> Unit,
) {
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Devices", fontWeight = FontWeight.SemiBold) },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
        containerColor = MaterialTheme.colorScheme.surface,
    ) { inner ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(inner),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                ConnectivityCard(
                    bluetoothEnabled = state.bluetoothEnabled,
                    healthConnectState = state.healthConnectState,
                    lowBattery = state.lowBatteryFlag,
                    onRequestHealthPermissions = onRequestHealthPermissions,
                )
            }

            item { SectionHeader("Paired meters") }

            if (state.meters.isEmpty()) {
                item { EmptyMetersCard(onPairMeter) }
            } else {
                items(state.meters, key = { it.associationId }) { meter ->
                    MeterCard(
                        meter = meter,
                        syncing = state.syncing,
                        onSync = onSync,
                        onUnpair = onUnpair,
                        onForceResync = onForceResync,
                    )
                }
                item { PairMeterButton(onPairMeter) }
            }
        }
    }
}

@Composable
private fun ConnectivityCard(
    bluetoothEnabled: Boolean,
    healthConnectState: HealthConnectState,
    lowBattery: Boolean,
    onRequestHealthPermissions: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Column(
            Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                "Connectivity",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            StatusRow(
                icon = { status ->
                    Icon(
                        if (status) Icons.Outlined.Bluetooth else Icons.Default.BluetoothDisabled,
                        contentDescription = null,
                        tint = if (status) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.error,
                    )
                },
                ok = bluetoothEnabled,
                label = "Bluetooth",
                value = if (bluetoothEnabled) "On" else "Off — enable to sync",
            )
            StatusRow(
                icon = {
                    Icon(
                        Icons.Outlined.HealthAndSafety,
                        contentDescription = null,
                        tint = when (healthConnectState) {
                            HealthConnectState.READY -> MaterialTheme.colorScheme.primary
                            HealthConnectState.NEEDS_PERMISSIONS -> MaterialTheme.colorScheme.tertiary
                            HealthConnectState.UNAVAILABLE -> MaterialTheme.colorScheme.error
                        },
                    )
                },
                ok = healthConnectState == HealthConnectState.READY,
                label = "Health Connect",
                value = when (healthConnectState) {
                    HealthConnectState.READY -> "Connected"
                    HealthConnectState.NEEDS_PERMISSIONS -> "Tap to grant access"
                    HealthConnectState.UNAVAILABLE -> "Not installed on this device"
                },
                onClick = if (healthConnectState == HealthConnectState.NEEDS_PERMISSIONS)
                    onRequestHealthPermissions else null,
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
                        labelColor = MaterialTheme.colorScheme.onErrorContainer,
                    ),
                    border = null,
                )
            }
        }
    }
}

@Composable
private fun StatusRow(
    icon: @Composable (Boolean) -> Unit,
    ok: Boolean,
    label: String,
    value: String,
    onClick: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .let { if (onClick != null) it.padding(vertical = 4.dp) else it },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(36.dp),
            contentAlignment = Alignment.Center,
        ) { icon(ok) }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.labelLarge)
            Text(
                value,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (onClick != null) {
            FilledTonalButton(onClick = onClick) { Text("Grant") }
        }
    }
}

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
                Icons.Outlined.Devices,
                contentDescription = null,
                modifier = Modifier.size(40.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(10.dp))
            Text("No meter paired", style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            Text(
                "Put your Contour Next One into pairing mode, then tap Pair meter.",
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
                modifier = Modifier.size(48.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Outlined.Bluetooth,
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
                    style = MaterialTheme.typography.labelSmall,
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
