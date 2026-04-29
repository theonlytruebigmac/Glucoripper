package com.syschimp.glucoripper.ui.screens

import android.content.IntentSender
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.BluetoothDisabled
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.HealthAndSafety
import androidx.compose.material.icons.outlined.WaterDrop
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import com.syschimp.glucoripper.ui.components.RdBanner
import com.syschimp.glucoripper.ui.components.RdBannerTone
import com.syschimp.glucoripper.ui.components.rdSubtle
import com.syschimp.glucoripper.ui.components.relativeTime
import com.syschimp.glucoripper.ui.theme.GlucoseElevated
import com.syschimp.glucoripper.ui.theme.GlucoseInRange
import com.syschimp.glucoripper.ui.theme.GlucoseLow
import com.syschimp.glucoripper.ui.theme.RdMono
import kotlinx.coroutines.launch
import java.time.Instant

/**
 * Renders paired meters, pairing affordances, and the Health Connect hub as a
 * stacked column — designed to be embedded inside another scrolling container.
 */
@Composable
fun DevicesSection(
    state: UiState,
    onPairMeter: suspend () -> IntentSender,
    onPairFinished: () -> Unit,
    onSync: (PairedMeter) -> Unit,
    onForceResync: (PairedMeter) -> Unit,
    onUnpair: (PairedMeter) -> Unit,
    onRequestHealthPermissions: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (!state.bluetoothEnabled) {
            RdBanner(
                tone = RdBannerTone.Error,
                icon = Icons.Default.BluetoothDisabled,
                title = "Bluetooth is off",
                body = "Turn it on so your meter can sync.",
            )
        }
        if (state.meters.isEmpty()) {
            EmptyMetersCard(onPairMeter, onPairFinished)
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
            PairAnotherButton(onPairMeter, onPairFinished)
        }
        HealthConnectRow(
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
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                MaterialTheme.colorScheme.surfaceContainerLow,
                RoundedCornerShape(14.dp),
            )
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(
                        MaterialTheme.colorScheme.surfaceContainerHigh,
                        RoundedCornerShape(10.dp),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Outlined.WaterDrop,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(18.dp),
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    meter.displayName ?: "Contour Meter",
                    style = RdMono.RowSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                )
                Spacer(Modifier.height(3.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(6.dp)
                            .background(GlucoseInRange, CircleShape),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        "Connected",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Medium,
                        ),
                        color = GlucoseInRange,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "·",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        meter.lastSyncMillis?.let {
                            relativeTime(Instant.ofEpochMilli(it))
                        } ?: "Never synced",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            BatteryChip(lowBattery = lowBattery)
        }

        Spacer(Modifier.height(14.dp))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            DeviceActionButton(
                primary = true,
                onClick = { onSync(meter) },
                enabled = !syncing,
                modifier = Modifier.weight(1f),
            ) {
                if (syncing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(14.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                    Spacer(Modifier.width(6.dp))
                } else {
                    Icon(
                        Icons.Default.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onPrimary,
                    )
                    Spacer(Modifier.width(6.dp))
                }
                Text(
                    "Sync now",
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontWeight = FontWeight.SemiBold,
                    ),
                    color = MaterialTheme.colorScheme.onPrimary,
                )
            }
            Box(modifier = Modifier.weight(1f)) {
                DeviceActionButton(
                    primary = false,
                    onClick = { menuOpen = true },
                    enabled = true,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        "Unpair",
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontWeight = FontWeight.SemiBold,
                        ),
                        color = MaterialTheme.colorScheme.onSurface,
                    )
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

@Composable
private fun DeviceActionButton(
    primary: Boolean,
    onClick: () -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val bg = if (primary) MaterialTheme.colorScheme.primary else androidx.compose.ui.graphics.Color.Transparent
    val border = if (primary) androidx.compose.ui.graphics.Color.Transparent else MaterialTheme.colorScheme.outline
    Row(
        modifier = modifier
            .height(40.dp)
            .border(if (primary) 0.dp else 1.dp, border, CircleShape)
            .background(bg, CircleShape)
            .clickable(enabled = enabled) { onClick() }
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        content()
    }
}

@Composable
private fun BatteryChip(lowBattery: Boolean) {
    val (bg, fg) = if (lowBattery) {
        MaterialTheme.colorScheme.errorContainer to GlucoseLow
    } else {
        MaterialTheme.colorScheme.surfaceContainerHigh to GlucoseInRange
    }
    val label = if (lowBattery) "Battery low" else "Battery OK"
    Box(
        modifier = Modifier
            .background(bg, CircleShape)
            .padding(horizontal = 8.dp, vertical = 3.dp),
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.SemiBold,
                fontSize = androidx.compose.ui.unit.TextUnit(9f, androidx.compose.ui.unit.TextUnitType.Sp),
            ),
            color = fg,
        )
    }
}

@Composable
private fun HealthConnectRow(
    state: HealthConnectState,
    onRequestPermissions: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(
                MaterialTheme.colorScheme.surfaceContainerLow,
                RoundedCornerShape(14.dp),
            )
            .padding(horizontal = 14.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            Icons.Outlined.HealthAndSafety,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp),
        )
        Column(Modifier.weight(1f)) {
            Text(
                "Health Connect",
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.SemiBold,
                ),
            )
            val (color, label) = when (state) {
                HealthConnectState.READY -> GlucoseInRange to "Connected"
                HealthConnectState.NEEDS_PERMISSIONS -> GlucoseElevated to "Permission required"
                HealthConnectState.UNAVAILABLE -> GlucoseLow to "Not installed"
            }
            Text(
                label,
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                color = color,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        when (state) {
            HealthConnectState.READY -> {
                Icon(
                    Icons.Default.Check,
                    contentDescription = null,
                    tint = GlucoseInRange,
                    modifier = Modifier.size(18.dp),
                )
            }
            HealthConnectState.NEEDS_PERMISSIONS -> {
                Box(
                    Modifier
                        .clickable { onRequestPermissions() }
                        .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                ) {
                    Text(
                        "Grant",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.SemiBold,
                        ),
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
            HealthConnectState.UNAVAILABLE -> Unit
        }
    }
}

@Composable
private fun EmptyMetersCard(
    onPairMeter: suspend () -> IntentSender,
    onPairFinished: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { onPairFinished() }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                1.dp,
                MaterialTheme.colorScheme.outline,
                RoundedCornerShape(14.dp),
            )
            .padding(horizontal = 20.dp, vertical = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(48.dp)
                .background(
                    MaterialTheme.colorScheme.surfaceContainerHigh,
                    CircleShape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Outlined.WaterDrop,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(22.dp),
            )
        }
        Spacer(Modifier.height(14.dp))
        Text(
            "No meter paired",
            style = MaterialTheme.typography.titleMedium.copy(
                fontWeight = FontWeight.SemiBold,
            ),
        )
        Spacer(Modifier.height(4.dp))
        Text(
            "Put your Contour Next One into pairing mode, then tap Pair meter.",
            style = MaterialTheme.typography.bodySmall,
            color = rdSubtle(),
        )
        Spacer(Modifier.height(16.dp))
        Box(
            Modifier
                .clickable {
                    scope.launch {
                        val sender = onPairMeter()
                        launcher.launch(IntentSenderRequest.Builder(sender).build())
                    }
                }
                .background(MaterialTheme.colorScheme.primary, CircleShape)
                .padding(horizontal = 18.dp, vertical = 10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(14.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    "Pair meter",
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontWeight = FontWeight.SemiBold,
                    ),
                    color = MaterialTheme.colorScheme.onPrimary,
                )
            }
        }
    }
}

@Composable
private fun PairAnotherButton(
    onPairMeter: suspend () -> IntentSender,
    onPairFinished: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { onPairFinished() }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                1.dp,
                MaterialTheme.colorScheme.outline,
                RoundedCornerShape(14.dp),
            )
            .clickable {
                scope.launch {
                    val sender = onPairMeter()
                    launcher.launch(IntentSenderRequest.Builder(sender).build())
                }
            }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Default.Add,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(14.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            "Pair another meter",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
