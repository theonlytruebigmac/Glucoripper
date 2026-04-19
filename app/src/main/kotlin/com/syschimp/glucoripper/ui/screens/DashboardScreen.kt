package com.syschimp.glucoripper.ui.screens

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material.icons.filled.BluetoothDisabled
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.HealthAndSafety
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.health.connect.client.records.BloodGlucoseRecord
import com.syschimp.glucoripper.data.Feeling
import com.syschimp.glucoripper.data.GlucoseUnit
import com.syschimp.glucoripper.data.HealthEvent
import com.syschimp.glucoripper.data.StagedReading
import com.syschimp.glucoripper.data.targetRangeFor
import com.syschimp.glucoripper.ui.HealthConnectState
import com.syschimp.glucoripper.ui.LogEventSheet
import com.syschimp.glucoripper.ui.PairedMeter
import com.syschimp.glucoripper.ui.ReadingDetailSheet
import com.syschimp.glucoripper.ui.StagedReadingDetailSheet
import com.syschimp.glucoripper.ui.TimeInRangeCard
import com.syschimp.glucoripper.ui.UiState
import com.syschimp.glucoripper.ui.components.Dot
import com.syschimp.glucoripper.ui.components.RingGauge
import com.syschimp.glucoripper.ui.components.TimelineCard
import com.syschimp.glucoripper.ui.components.SectionHeader
import com.syschimp.glucoripper.ui.components.TodayChart
import com.syschimp.glucoripper.ui.components.bandColor
import com.syschimp.glucoripper.ui.components.formatTimeOnly
import com.syschimp.glucoripper.ui.components.relationShort
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
    onLogEvent: (HealthEvent) -> Unit,
    onRemoveEvent: (String) -> Unit,
    onNavigateToHistory: () -> Unit,
    onNavigateToDevices: () -> Unit,
) {
    var selectedReading by remember { mutableStateOf<BloodGlucoseRecord?>(null) }
    var selectedStagedId by remember { mutableStateOf<String?>(null) }
    var showLogEvent by remember { mutableStateOf(false) }

    val pullState = rememberPullToRefreshState()
    val latest = state.recentReadings.firstOrNull()
    val previous = state.recentReadings.getOrNull(1)

    Scaffold(
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0),
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showLogEvent = true },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 6.dp),
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("Log Event") },
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
                    contentPadding = PaddingValues(
                        start = 16.dp,
                        end = 16.dp,
                        top = 4.dp,
                        bottom = 88.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    item {
                        Box(Modifier.fillMaxWidth()) {
                            Text(
                                "Dashboard",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.align(Alignment.Center),
                            )
                            if (state.meters.isNotEmpty()) {
                                Box(Modifier.align(Alignment.CenterEnd)) {
                                    if (state.syncing) {
                                        CircularProgressIndicator(
                                            Modifier.size(20.dp),
                                            strokeWidth = 2.dp,
                                        )
                                    } else {
                                        Icon(
                                            Icons.Default.Refresh,
                                            contentDescription = "Sync now",
                                            modifier = Modifier
                                                .size(22.dp)
                                                .clickable { state.meters.firstOrNull()?.let(onSyncNow) },
                                        )
                                    }
                                }
                            }
                        }
                    }

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
                        HubCard {
                            Column(
                                Modifier.fillMaxWidth().padding(top = 20.dp, bottom = 10.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(0.dp),
                            ) {
                                // Gauge + chip reflect the latest reading's
                                // context-appropriate target range (fasting /
                                // pre-meal / post-meal / general).
                                val latestRelation = latest?.let { rec ->
                                    val ann = rec.metadata.clientRecordId
                                        ?.let(state.annotations::get)
                                    ann?.mealOverride ?: rec.relationToMeal
                                } ?: -1
                                val latestRange = state.prefs.targetRangeFor(latestRelation)
                                val latestLow = latestRange.first
                                val latestHigh = latestRange.second
                                Box(contentAlignment = Alignment.Center) {
                                    RingGauge(
                                        latest = latest,
                                        previous = previous,
                                        unit = state.prefs.unit,
                                        lowMgDl = latestLow,
                                        highMgDl = latestHigh,
                                        warningBuffer = state.prefs.warningBufferMgDl,
                                        onClick = {
                                            latest?.let { selectedReading = it }
                                        },
                                    )
                                }
                                TargetZoneChip(
                                    lowMgDl = latestLow,
                                    highMgDl = latestHigh,
                                    unit = state.prefs.unit,
                                    current = latest?.level?.inMilligramsPerDeciliter,
                                    contextLabel = contextLabelFor(latestRelation),
                                )
                                TodayChart(
                                    readings = state.recentReadings,
                                    events = state.events,
                                    unit = state.prefs.unit,
                                    lowMgDl = state.prefs.targetLowMgDl,
                                    highMgDl = state.prefs.targetHighMgDl,
                                    hasMeter = state.meters.isNotEmpty(),
                                    onEventClick = { /* future: edit */ },
                                    onPairClick = onNavigateToDevices,
                                    modifier = Modifier.padding(horizontal = 8.dp),
                                    yMinMgDl = state.prefs.chartMinMgDl,
                                    yMaxMgDl = state.prefs.chartMaxMgDl,
                                    warningBuffer = state.prefs.warningBufferMgDl,
                                    colorForReading = { r ->
                                        val ann = r.metadata.clientRecordId
                                            ?.let(state.annotations::get)
                                        val meal = ann?.mealOverride ?: r.relationToMeal
                                        val range = state.prefs.targetRangeFor(meal)
                                        bandColor(
                                            r.level.inMilligramsPerDeciliter,
                                            range.first,
                                            range.second,
                                            state.prefs.warningBufferMgDl,
                                        )
                                    },
                                )
                            }
                        }
                    }

                    item {
                        Text(
                            "Recent",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(start = 4.dp, top = 8.dp, bottom = 2.dp),
                        )
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

                    // "Recent" on the dashboard = today's readings only. Full
                    // history lives on the History tab.
                    val zone = java.time.ZoneId.systemDefault()
                    val today = java.time.LocalDate.now(zone)
                    val todayReadings = state.recentReadings.filter {
                        it.time.atZone(zone).toLocalDate() == today
                    }
                    if (todayReadings.isNotEmpty()) {
                        items(todayReadings, key = { it.metadata.id }) { r ->
                            val ann = r.metadata.clientRecordId
                                ?.let(state.annotations::get)
                            TimelineCard(
                                reading = r,
                                annotation = ann,
                                unit = state.prefs.unit,
                                prefs = state.prefs,
                                onClick = { selectedReading = r },
                            )
                        }
                    } else {
                        item {
                            Card(
                                shape = RoundedCornerShape(18.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                                ),
                            ) {
                                Text(
                                    "No readings today yet.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(16.dp),
                                )
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
            prefs = state.prefs,
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

    if (showLogEvent) {
        LogEventSheet(
            onDismiss = { showLogEvent = false },
            onSave = onLogEvent,
        )
    }
}

// ─────────── Hub card wrapper ───────────

@Composable
private fun HubCard(content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        content()
    }
}

// ─────────── Target zone chip ───────────

@Composable
private fun TargetZoneChip(
    lowMgDl: Double,
    highMgDl: Double,
    unit: GlucoseUnit,
    current: Double?,
    contextLabel: String,
) {
    val color = current?.let { bandColor(it, lowMgDl, highMgDl) }
        ?: MaterialTheme.colorScheme.outline
    Surface(
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Dot(color = color, size = 8.dp)
            Spacer(Modifier.width(8.dp))
            Text(
                "$contextLabel (${formatGlucose(lowMgDl, unit)}–${formatGlucose(highMgDl, unit)} ${unitLabel(unit)})",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

private fun contextLabelFor(mealRelation: Int): String = when (mealRelation) {
    androidx.health.connect.client.records.BloodGlucoseRecord.RELATION_TO_MEAL_FASTING -> "Fasting Target"
    androidx.health.connect.client.records.BloodGlucoseRecord.RELATION_TO_MEAL_BEFORE_MEAL -> "Pre-meal Target"
    androidx.health.connect.client.records.BloodGlucoseRecord.RELATION_TO_MEAL_AFTER_MEAL -> "Post-meal Target"
    else -> "Target Zone"
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
