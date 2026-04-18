package com.syschimp.glucoripper.ui.screens

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material.icons.filled.BluetoothDisabled
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.HealthAndSafety
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.health.connect.client.records.BloodGlucoseRecord
import com.syschimp.glucoripper.data.Feeling
import com.syschimp.glucoripper.data.GlucoseUnit
import com.syschimp.glucoripper.data.StagedReading
import com.syschimp.glucoripper.ui.HealthConnectState
import com.syschimp.glucoripper.ui.PairedMeter
import com.syschimp.glucoripper.ui.ReadingDetailSheet
import com.syschimp.glucoripper.ui.StagedReadingDetailSheet
import com.syschimp.glucoripper.ui.TimeInRangeCard
import com.syschimp.glucoripper.ui.UiState
import com.syschimp.glucoripper.ui.components.Dot
import com.syschimp.glucoripper.ui.components.ReadingRow
import com.syschimp.glucoripper.ui.components.SectionHeader
import com.syschimp.glucoripper.ui.components.TodayChartCard
import com.syschimp.glucoripper.ui.components.bandColor
import com.syschimp.glucoripper.ui.components.formatTimeOnly
import com.syschimp.glucoripper.ui.components.relationShort
import com.syschimp.glucoripper.ui.components.relativeTime
import com.syschimp.glucoripper.ui.computeTimeInRange
import com.syschimp.glucoripper.ui.format.formatGlucose
import com.syschimp.glucoripper.ui.format.unitLabel
import java.time.Duration
import java.time.Instant

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    state: UiState,
    onRefresh: () -> Unit,
    onSyncNow: (PairedMeter) -> Unit,
    onRequestHealthPermissions: () -> Unit,
    onPushStaged: (Collection<String>?) -> Unit,
    onUpdateStaged: (String, (StagedReading) -> StagedReading) -> Unit,
    onDiscardStaged: (String) -> Unit,
    onSetMealRelation: (BloodGlucoseRecord, Int) -> Unit,
    onSetFeeling: (String?, Feeling?) -> Unit,
    onSetNote: (String?, String?) -> Unit,
    onNavigateToHistory: () -> Unit,
    onNavigateToDevices: () -> Unit,
) {
    var selectedReading by remember { mutableStateOf<BloodGlucoseRecord?>(null) }
    var selectedStagedId by remember { mutableStateOf<String?>(null) }

    val pullState = rememberPullToRefreshState()

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        "Dashboard",
                        fontWeight = FontWeight.SemiBold,
                    )
                },
                actions = {
                    if (state.meters.isNotEmpty()) {
                        IconButton(
                            onClick = { state.meters.firstOrNull()?.let(onSyncNow) },
                            enabled = !state.syncing,
                        ) {
                            if (state.syncing) {
                                CircularProgressIndicator(
                                    Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                )
                            } else {
                                Icon(Icons.Default.Refresh, contentDescription = "Sync now")
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
        containerColor = MaterialTheme.colorScheme.surface,
    ) { inner ->
        Column(Modifier.fillMaxSize().padding(inner)) {
            AnimatedVisibility(visible = state.syncing) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
            }
            PullToRefreshBox(
                isRefreshing = state.syncing,
                onRefresh = { state.meters.firstOrNull()?.let(onSyncNow) ?: onRefresh() },
                state = pullState,
            ) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    val problems = collectProblems(state)
                    if (problems.isNotEmpty()) {
                        item {
                            ProblemsBanner(
                                problems = problems,
                                onGrantHealthPermissions = onRequestHealthPermissions,
                            )
                        }
                    }

                    item {
                        TodayChartCard(
                            readings = state.recentReadings,
                            unit = state.prefs.unit,
                            lowMgDl = state.prefs.targetLowMgDl,
                            highMgDl = state.prefs.targetHighMgDl,
                            hasMeter = state.meters.isNotEmpty(),
                            onLatestClick = {
                                state.recentReadings.firstOrNull()?.let { selectedReading = it }
                            },
                            onPairClick = onNavigateToDevices,
                        )
                    }

                    if (state.recentReadings.isNotEmpty()) {
                        item {
                            QuickStats(
                                readings = state.recentReadings,
                                unit = state.prefs.unit,
                                lowMgDl = state.prefs.targetLowMgDl,
                                highMgDl = state.prefs.targetHighMgDl,
                            )
                        }
                    }

                    if (state.staged.isNotEmpty()) {
                        item {
                            PendingCard(
                                staged = state.staged,
                                unit = state.prefs.unit,
                                lowMgDl = state.prefs.targetLowMgDl,
                                highMgDl = state.prefs.targetHighMgDl,
                                pushing = state.pushing,
                                onTap = { selectedStagedId = it.id },
                                onPushAll = { onPushStaged(null) },
                            )
                        }
                    }

                    item {
                        val tir = computeTimeInRange(
                            state.recentReadings,
                            state.prefs.targetLowMgDl,
                            state.prefs.targetHighMgDl,
                        )
                        TimeInRangeCard(
                            tir,
                            state.prefs.unit,
                            state.prefs.targetLowMgDl,
                            state.prefs.targetHighMgDl,
                        )
                    }

                    if (state.recentReadings.size > 1) {
                        item {
                            SectionHeader(
                                text = "Recent",
                                action = {
                                    TextButton(onClick = onNavigateToHistory) {
                                        Text("See all")
                                        Spacer(Modifier.width(2.dp))
                                        Icon(
                                            Icons.AutoMirrored.Filled.ArrowForward,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp),
                                        )
                                    }
                                },
                            )
                        }
                        val recent = state.recentReadings.drop(1).take(5)
                        item {
                            Card(
                                shape = RoundedCornerShape(20.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                                ),
                            ) {
                                Column(Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
                                    recent.forEachIndexed { index, r ->
                                        val ann = r.metadata.clientRecordId
                                            ?.let(state.annotations::get)
                                        ReadingRow(
                                            reading = r,
                                            annotation = ann,
                                            unit = state.prefs.unit,
                                            lowMgDl = state.prefs.targetLowMgDl,
                                            highMgDl = state.prefs.targetHighMgDl,
                                            onClick = { selectedReading = r },
                                        )
                                        if (index < recent.size - 1) {
                                            HorizontalDivider(
                                                color = MaterialTheme.colorScheme.outlineVariant
                                                    .copy(alpha = 0.4f),
                                            )
                                        }
                                    }
                                }
                            }
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
            onSaveMeal = { onSetMealRelation(r, it) },
            onSaveFeeling = { onSetFeeling(r.metadata.clientRecordId, it) },
            onSaveNote = { onSetNote(r.metadata.clientRecordId, it) },
        )
    }

    selectedStagedId?.let { id ->
        state.staged.firstOrNull { it.id == id }?.let { staged ->
            StagedReadingDetailSheet(
                staged = staged,
                unit = state.prefs.unit,
                onDismiss = { selectedStagedId = null },
                onUpdate = { transform -> onUpdateStaged(id, transform) },
                onPush = { onPushStaged(listOf(id)) },
                onDiscard = { onDiscardStaged(id) },
            )
        } ?: run { selectedStagedId = null }
    }
}

// ─────────── Quick stats ───────────

@Composable
private fun QuickStats(
    readings: List<BloodGlucoseRecord>,
    unit: GlucoseUnit,
    lowMgDl: Double,
    highMgDl: Double,
) {
    val now = Instant.now()
    val dayAgo = now.minus(Duration.ofHours(24))
    val weekAgo = now.minus(Duration.ofDays(7))
    val lastDay = readings.filter { it.time.isAfter(dayAgo) }
    val lastWeek = readings.filter { it.time.isAfter(weekAgo) }
    val weekMgDl = lastWeek.map { it.level.inMilligramsPerDeciliter }
    val inRange = weekMgDl.count { it in lowMgDl..highMgDl }
    val inRangePct = if (weekMgDl.isEmpty()) 0 else inRange * 100 / weekMgDl.size
    val avgLabel = weekMgDl.takeIf { it.isNotEmpty() }?.average()
        ?.let { formatGlucose(it, unit) } ?: "—"

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        StatTile(
            modifier = Modifier.weight(1f),
            label = "In range",
            value = "$inRangePct%",
            caption = "7d",
        )
        StatTile(
            modifier = Modifier.weight(1f),
            label = "Average",
            value = avgLabel,
            caption = "7d · ${unitLabel(unit)}",
        )
        StatTile(
            modifier = Modifier.weight(1f),
            label = "Readings",
            value = lastDay.size.toString(),
            caption = "24h",
        )
    }
}

@Composable
private fun StatTile(
    modifier: Modifier = Modifier,
    label: String,
    value: String,
    caption: String,
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Column(
            Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                caption,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// ─────────── Problems banner (shown only when something's wrong) ───────────

private data class Problem(
    val icon: ImageVector,
    val title: String,
    val body: String,
    val actionLabel: String? = null,
    val action: (() -> Unit)? = null,
    val tintContainer: Boolean = false,
)

private fun collectProblems(state: UiState): List<Problem> {
    val list = mutableListOf<Problem>()
    if (!state.bluetoothEnabled) {
        list += Problem(
            icon = Icons.Default.BluetoothDisabled,
            title = "Bluetooth is off",
            body = "Turn it on so Glucoripper can reach your meter.",
            tintContainer = true,
        )
    }
    when (state.healthConnectState) {
        HealthConnectState.UNAVAILABLE -> list += Problem(
            icon = Icons.Outlined.HealthAndSafety,
            title = "Health Connect missing",
            body = "Install Health Connect from Play Store to enable sync.",
            tintContainer = true,
        )
        HealthConnectState.NEEDS_PERMISSIONS -> list += Problem(
            icon = Icons.Outlined.HealthAndSafety,
            title = "Grant Health Connect access",
            body = "Glucoripper needs permission to read and write glucose data.",
            actionLabel = "Grant",
        )
        HealthConnectState.READY -> Unit
    }
    if (state.lowBatteryFlag) {
        list += Problem(
            icon = Icons.Default.BatteryAlert,
            title = "Meter battery low",
            body = "Change the batteries in your meter soon.",
            tintContainer = true,
        )
    }
    return list
}

@Composable
private fun ProblemsBanner(
    problems: List<Problem>,
    onGrantHealthPermissions: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        problems.forEach { p ->
            val container = if (p.tintContainer)
                MaterialTheme.colorScheme.errorContainer
            else MaterialTheme.colorScheme.tertiaryContainer
            val onContainer = if (p.tintContainer)
                MaterialTheme.colorScheme.onErrorContainer
            else MaterialTheme.colorScheme.onTertiaryContainer
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = container),
            ) {
                Row(
                    Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(p.icon, contentDescription = null, tint = onContainer)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            p.title,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = onContainer,
                        )
                        Text(
                            p.body,
                            style = MaterialTheme.typography.bodySmall,
                            color = onContainer.copy(alpha = 0.85f),
                        )
                    }
                    if (p.actionLabel != null) {
                        Spacer(Modifier.width(8.dp))
                        Button(onClick = { onGrantHealthPermissions() }) {
                            Text(p.actionLabel)
                        }
                    }
                }
            }
        }
    }
}

// ─────────── Pending review ───────────

@Composable
private fun PendingCard(
    staged: List<StagedReading>,
    unit: GlucoseUnit,
    lowMgDl: Double,
    highMgDl: Double,
    pushing: Boolean,
    onTap: (StagedReading) -> Unit,
    onPushAll: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
        ),
    ) {
        Column(
            Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "Pending review",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                    Text(
                        "${staged.size} reading${if (staged.size == 1) "" else "s"} awaiting send",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.75f),
                    )
                }
                Button(
                    onClick = onPushAll,
                    enabled = !pushing && staged.isNotEmpty(),
                ) {
                    if (pushing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                        )
                    } else {
                        Text("Send all")
                    }
                }
            }
            staged.take(5).forEach { s ->
                PendingRow(
                    staged = s,
                    unit = unit,
                    lowMgDl = lowMgDl,
                    highMgDl = highMgDl,
                    onTap = { onTap(s) },
                )
            }
            if (staged.size > 5) {
                Text(
                    "… and ${staged.size - 5} more",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.75f),
                )
            }
        }
    }
}

@Composable
private fun PendingRow(
    staged: StagedReading,
    unit: GlucoseUnit,
    lowMgDl: Double,
    highMgDl: Double,
    onTap: () -> Unit,
) {
    val color = bandColor(staged.mgPerDl, lowMgDl, highMgDl)
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onTap() }
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Dot(color = color, size = 10.dp)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                formatTimeOnly(staged.time),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            val subtitle = buildList {
                relationShort(staged.effectiveMeal)?.let(::add)
                staged.feeling?.let { add("${it.emoji} ${it.label}") }
                staged.note?.takeIf { it.isNotBlank() }?.let {
                    val preview = if (it.length > 24) it.take(24) + "…" else it
                    add("“$preview”")
                }
            }.joinToString(" · ")
            if (subtitle.isNotEmpty()) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.75f),
                )
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                formatGlucose(staged.mgPerDl, unit),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            Text(
                " " + unitLabel(unit),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.75f),
            )
        }
    }
}
