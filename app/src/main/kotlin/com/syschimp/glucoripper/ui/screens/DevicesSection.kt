package com.syschimp.glucoripper.ui.screens

import android.content.IntentSender
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material.icons.filled.BluetoothDisabled
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.Bloodtype
import androidx.compose.material.icons.outlined.Devices
import androidx.compose.material.icons.outlined.HealthAndSafety
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.syschimp.glucoripper.ui.HealthConnectState
import com.syschimp.glucoripper.ui.PairedMeter
import com.syschimp.glucoripper.ui.UiState
import com.syschimp.glucoripper.ui.components.relativeTime
import kotlinx.coroutines.launch
import java.time.Instant

/**
 * Renders paired meters, pairing affordances, and the Health Connect hub as a
 * stacked column — designed to be embedded inside another scrolling container
 * (currently the Settings screen).
 */
@Composable
fun DevicesSection(
    state: UiState,
    onPairMeter: suspend () -> IntentSender,
    onSync: (PairedMeter) -> Unit,
    onForceResync: (PairedMeter) -> Unit,
    onUnpair: (PairedMeter) -> Unit,
    onRequestHealthPermissions: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(
            "Devices",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(start = 4.dp, top = 4.dp),
        )
        if (!state.bluetoothEnabled) BluetoothOffCard()
        if (state.meters.isEmpty()) {
            EmptyMetersCard(onPairMeter)
        } else {
            state.meters.forEach { meter ->
                MeterCard(
                    meter = meter,
                    syncing = state.syncing,
                    lowBattery = state.lowBatteryFlag,
                    onSync = onSync,
                    onUnpair = onUnpair,
                    onForceResync = onForceResync,
                )
            }
            PairAnotherButton(onPairMeter)
        }
        HealthConnectCard(
            state = state.healthConnectState,
            onRequestPermissions = onRequestHealthPermissions,
        )
    }
}

@Composable
private fun MeterCard(
    meter: PairedMeter,
    syncing: Boolean,
    lowBattery: Boolean,
    onSync: (PairedMeter) -> Unit,
    onUnpair: (PairedMeter) -> Unit,
    onForceResync: (PairedMeter) -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Column(
            Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                MeterIllustration()
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        meter.displayName ?: "Contour Meter",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    ConnectedBadge()
                    Spacer(Modifier.height(2.dp))
                    Text(
                        meter.lastSyncMillis?.let {
                            "Last sync " + relativeTime(Instant.ofEpochMilli(it))
                        } ?: "Never synced",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                BatteryChip(lowBattery = lowBattery)
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Button(
                    onClick = { onSync(meter) },
                    enabled = !syncing,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(14.dp),
                ) {
                    if (syncing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                        )
                    } else {
                        Icon(Icons.Default.Refresh, contentDescription = null,
                            modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Sync Now")
                    }
                }
                Box(modifier = Modifier.weight(1f)) {
                    OutlinedButton(
                        onClick = { menuOpen = true },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                    ) { Text("Unpair") }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text("Full resync") },
                            onClick = {
                                menuOpen = false
                                onForceResync(meter)
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Unpair meter") },
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
}

@Composable
private fun MeterIllustration() {
    Box(
        modifier = Modifier
            .size(64.dp)
            .background(
                MaterialTheme.colorScheme.surfaceContainerHigh,
                RoundedCornerShape(16.dp),
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.Outlined.Bloodtype,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(36.dp),
        )
    }
}

@Composable
private fun ConnectedBadge() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(8.dp)
                .background(Color(0xFF30A46C), CircleShape),
        )
        Spacer(Modifier.width(6.dp))
        Text(
            "Connected",
            style = MaterialTheme.typography.labelMedium,
            color = Color(0xFF30A46C),
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun BatteryChip(lowBattery: Boolean) {
    val color = if (lowBattery) Color(0xFFE5484D) else Color(0xFF30A46C)
    val label = if (lowBattery) "Low" else "OK"
    Row(
        modifier = Modifier
            .background(color.copy(alpha = 0.12f), RoundedCornerShape(20.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Default.BatteryAlert,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(14.dp),
        )
        Spacer(Modifier.width(4.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = color,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun HealthConnectCard(
    state: HealthConnectState,
    onRequestPermissions: () -> Unit,
) {
    val container = MaterialTheme.colorScheme.secondaryContainer
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = container),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .background(
                        MaterialTheme.colorScheme.surface,
                        RoundedCornerShape(16.dp),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Outlined.HealthAndSafety,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(30.dp),
                )
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    "Health Connect",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
                HealthConnectStatusLine(state)
            }
            when (state) {
                HealthConnectState.NEEDS_PERMISSIONS ->
                    Button(
                        onClick = onRequestPermissions,
                        shape = RoundedCornerShape(14.dp),
                    ) { Text("Grant") }
                HealthConnectState.UNAVAILABLE -> { /* no action */ }
                HealthConnectState.READY ->
                    Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = Color(0xFF30A46C),
                        modifier = Modifier.size(28.dp),
                    )
            }
        }
    }
}

@Composable
private fun HealthConnectStatusLine(state: HealthConnectState) {
    val (color, label) = when (state) {
        HealthConnectState.READY -> Color(0xFF30A46C) to "Connected"
        HealthConnectState.NEEDS_PERMISSIONS -> Color(0xFFF5A524) to "Permission required"
        HealthConnectState.UNAVAILABLE -> Color(0xFFE5484D) to "Not installed"
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).background(color, CircleShape))
        Spacer(Modifier.width(6.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = color,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun BluetoothOffCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
        ),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Default.BluetoothDisabled,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onErrorContainer,
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    "Bluetooth is off",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
                Text(
                    "Turn it on so your meter can sync.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.85f),
                )
            }
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
        shape = RoundedCornerShape(24.dp),
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
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                "No meter paired",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "Put your Contour Next One into pairing mode, then tap Pair meter.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = {
                    scope.launch {
                        val sender = onPairMeter()
                        launcher.launch(IntentSenderRequest.Builder(sender).build())
                    }
                },
                shape = RoundedCornerShape(14.dp),
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text("Pair meter")
            }
        }
    }
}

@Composable
private fun PairAnotherButton(onPairMeter: suspend () -> IntentSender) {
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
        shape = RoundedCornerShape(14.dp),
    ) {
        Icon(Icons.Default.Add, contentDescription = null)
        Spacer(Modifier.width(6.dp))
        Text("Pair another meter")
    }
}
