package com.syschimp.glucoripper.ui.screens

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material.icons.filled.BluetoothDisabled
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.HealthAndSafety
import androidx.compose.material.icons.outlined.WaterDrop
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.health.connect.client.records.BloodGlucoseRecord
import com.syschimp.glucoripper.data.Feeling
import com.syschimp.glucoripper.data.HealthEvent
import com.syschimp.glucoripper.data.StagedReading
import com.syschimp.glucoripper.data.targetRangeFor
import com.syschimp.glucoripper.ui.HealthConnectState
import com.syschimp.glucoripper.ui.LogEventSheet
import com.syschimp.glucoripper.ui.PairedMeter
import com.syschimp.glucoripper.ui.ReadingDetailSheet
import com.syschimp.glucoripper.ui.StagedReadingDetailSheet
import com.syschimp.glucoripper.ui.UiState
import com.syschimp.glucoripper.ui.components.GlucoseSample
import com.syschimp.glucoripper.ui.components.RdBand
import com.syschimp.glucoripper.ui.components.RdBanner
import com.syschimp.glucoripper.ui.components.RdBannerTone
import com.syschimp.glucoripper.ui.components.RdEmptyState
import com.syschimp.glucoripper.ui.components.RdGlucoseChart
import com.syschimp.glucoripper.ui.components.RdOverlineText
import com.syschimp.glucoripper.ui.components.RdRangeSelector
import com.syschimp.glucoripper.ui.components.RdReadingRow
import com.syschimp.glucoripper.ui.components.RdSectionHeader
import com.syschimp.glucoripper.ui.components.RdStatCard
import com.syschimp.glucoripper.ui.components.RdStatusChip
import com.syschimp.glucoripper.ui.components.RdSyncProgress
import com.syschimp.glucoripper.ui.components.RdTIRBar
import com.syschimp.glucoripper.ui.components.bandColor
import com.syschimp.glucoripper.ui.components.classifyBand
import com.syschimp.glucoripper.ui.components.formatTimeOnly
import com.syschimp.glucoripper.ui.components.rdSubtle
import com.syschimp.glucoripper.ui.components.relationShort
import com.syschimp.glucoripper.ui.components.relativeTime
import com.syschimp.glucoripper.ui.components.trendArrow
import com.syschimp.glucoripper.ui.format.formatGlucose
import com.syschimp.glucoripper.ui.format.unitLabel
import com.syschimp.glucoripper.ui.theme.GlucoseInRange
import com.syschimp.glucoripper.ui.theme.RdMono
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

private val rangeOptions = listOf("6h", "12h", "24h", "3d")
private fun rangeHours(opt: String): Float = when (opt) {
    "6h" -> 6f; "12h" -> 12f; "24h" -> 24f; "3d" -> 72f
    else -> 24f
}

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
    onNavigateToInsights: () -> Unit,
) {
    var selectedReading by remember { mutableStateOf<BloodGlucoseRecord?>(null) }
    var selectedStagedId by remember { mutableStateOf<String?>(null) }
    var showLogEvent by remember { mutableStateOf(false) }
    var range by remember { mutableStateOf("24h") }

    val pullState = rememberPullToRefreshState()
    val latest = state.recentReadings.firstOrNull()
    val previous = state.recentReadings.getOrNull(1)
    val isEmpty = state.recentReadings.isEmpty()

    Scaffold(
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0),
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showLogEvent = true },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 6.dp),
                icon = { Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp)) },
                text = { Text("Log") },
            )
        },
        containerColor = MaterialTheme.colorScheme.surface,
    ) { inner ->
        PullToRefreshBox(
            isRefreshing = state.syncing,
            onRefresh = { state.meters.firstOrNull()?.let(onSyncNow) ?: onRefresh() },
            state = pullState,
            modifier = Modifier.fillMaxSize().padding(inner),
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = 4.dp,
                    bottom = 96.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(0.dp),
            ) {
                item { GreetingRow(state = state, onSyncNow = onSyncNow) }

                item { ProblemBanners(state, onRequestHealthPermissions) }

                if (state.syncing) {
                    item { RdSyncProgress() }
                }

                if (isEmpty) {
                    item {
                        RdEmptyState(
                            icon = Icons.Outlined.WaterDrop,
                            title = "No readings today yet.",
                            body = "Take a reading on your meter — it'll show up here automatically. Or sync manually from Settings.",
                            actionLabel = "Sync now",
                            onAction = { state.meters.firstOrNull()?.let(onSyncNow) ?: onRefresh() },
                        )
                    }
                } else if (latest != null) {
                    item {
                        DashboardHero(
                            state = state,
                            latest = latest,
                            previous = previous,
                        )
                    }
                    item { Spacer(Modifier.height(4.dp)) }
                    item {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                        ) {
                            RdRangeSelector(
                                options = rangeOptions,
                                selected = range,
                                onSelect = { range = it },
                            )
                        }
                    }
                    item { Spacer(Modifier.height(6.dp)) }
                    item {
                        DashboardChart(
                            state = state,
                            range = range,
                        )
                    }
                    item { Spacer(Modifier.height(24.dp)) }
                    item {
                        RdOverlineText("Last 24h · Time in range")
                        Spacer(Modifier.height(10.dp))
                        DashboardTIR(state)
                    }
                    item { Spacer(Modifier.height(18.dp)) }
                    item { DashboardStatTrio(state) }

                    if (state.staged.isNotEmpty()) {
                        item { Spacer(Modifier.height(22.dp)) }
                        item {
                            RdPendingReview(
                                staged = state.staged,
                                unit = state.prefs.unit,
                                pushing = state.pushing,
                                onTap = { selectedStagedId = it.id },
                                onSendAll = { onPushStaged(null) },
                            )
                        }
                    }

                    item {
                        RdSectionHeader(
                            overline = "Recent",
                            trailing = {
                                Text(
                                    "View history →",
                                    style = RdMono.Caption,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier
                                        .clickable { onNavigateToHistory() }
                                        .padding(6.dp),
                                )
                            },
                        )
                    }

                    val zone = java.time.ZoneId.systemDefault()
                    val today = LocalDate.now(zone)
                    val todayReadings = state.recentReadings.filter {
                        it.time.atZone(zone).toLocalDate() == today
                    }
                    items(todayReadings, key = { it.metadata.id }) { r ->
                        val ann = r.metadata.clientRecordId?.let(state.annotations::get)
                        val effectiveMeal = ann?.mealOverride ?: r.relationToMeal
                        val tgt = state.prefs.targetRangeFor(effectiveMeal)
                        val color = bandColor(
                            r.level.inMilligramsPerDeciliter,
                            tgt.first,
                            tgt.second,
                            state.prefs.warningBufferMgDl,
                        )
                        RdReadingRow(
                            bandColor = color,
                            timeLabel = formatTimeOnly(r.time),
                            mealLabel = relationShort(effectiveMeal) ?: "General",
                            mgDlText = formatGlucose(r.level.inMilligramsPerDeciliter, state.prefs.unit),
                            unitText = unitLabel(state.prefs.unit),
                            onClick = { selectedReading = r },
                            modifier = Modifier.background(
                                MaterialTheme.colorScheme.surface,
                            ),
                        )
                    }
                    if (todayReadings.isEmpty()) {
                        item {
                            Text(
                                "No readings today yet.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(vertical = 14.dp),
                            )
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

// ─────────── Greeting row ───────────

@Composable
private fun GreetingRow(
    state: UiState,
    onSyncNow: (PairedMeter) -> Unit,
) {
    val (greeting, dateLabel) = remember {
        val phrase = when (LocalTime.now().hour) {
            in 0..4 -> "Good night"
            in 5..11 -> "Good morning"
            in 12..17 -> "Good afternoon"
            else -> "Good evening"
        }
        phrase to LocalDate.now().format(DateTimeFormatter.ofPattern("EEE, MMM d"))
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 14.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                greeting,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Text(
                dateLabel,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        if (state.meters.isNotEmpty() && !state.syncing) {
            Box(
                Modifier
                    .size(32.dp)
                    .clickable { state.meters.firstOrNull()?.let(onSyncNow) },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Default.Refresh,
                    contentDescription = "Sync now",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
            }
        } else if (state.syncing) {
            CircularProgressIndicator(
                strokeWidth = 2.dp,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

// ─────────── Problem banners ───────────

@Composable
private fun ProblemBanners(
    state: UiState,
    onRequestHealthPermissions: () -> Unit,
) {
    val items = mutableListOf<@Composable () -> Unit>()
    if (!state.bluetoothEnabled) items += {
        RdBanner(
            tone = RdBannerTone.Error,
            icon = Icons.Default.BluetoothDisabled,
            title = "Bluetooth is off",
            body = "Turn on Bluetooth to sync your meter.",
        )
    }
    when (state.healthConnectState) {
        HealthConnectState.UNAVAILABLE -> items += {
            RdBanner(
                tone = RdBannerTone.Warn,
                icon = Icons.Outlined.HealthAndSafety,
                title = "Health Connect missing",
                body = "Install Health Connect from Play Store to enable sync.",
            )
        }
        HealthConnectState.NEEDS_PERMISSIONS -> items += {
            RdBanner(
                tone = RdBannerTone.Warn,
                icon = Icons.Outlined.HealthAndSafety,
                title = "Grant Health Connect access",
                body = "Glucoripper needs permission to read and write glucose data.",
                actionLabel = "Grant",
                onAction = onRequestHealthPermissions,
            )
        }
        HealthConnectState.READY -> Unit
    }
    if (state.lowBatteryFlag) items += {
        RdBanner(
            tone = RdBannerTone.Warn,
            icon = Icons.Default.BatteryAlert,
            title = "Meter battery low",
            body = "Replace the batteries in your meter soon.",
        )
    }
    if (items.isEmpty()) return
    Column(
        modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items.forEach { it() }
    }
}

// ─────────── Hero numeric ───────────

@Composable
private fun DashboardHero(
    state: UiState,
    latest: BloodGlucoseRecord,
    previous: BloodGlucoseRecord?,
) {
    val latestRelation = latest.metadata.clientRecordId
        ?.let(state.annotations::get)?.mealOverride ?: latest.relationToMeal
    val (lo, hi) = state.prefs.targetRangeFor(latestRelation)
    val mg = latest.level.inMilligramsPerDeciliter
    val band = classifyBand(mg, lo, hi)
    val delta = previous?.let { mg - it.level.inMilligramsPerDeciliter }
    val arrow = trendArrow(delta)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(
            formatGlucose(mg, state.prefs.unit),
            style = RdMono.Hero,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Column(
            modifier = Modifier.padding(bottom = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            RdStatusChip(band = band, arrow = arrow)
            Text(
                "${unitLabel(state.prefs.unit)} · ${relativeTime(latest.time)}",
                style = RdMono.Caption,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// ─────────── Chart wiring ───────────

@Composable
private fun DashboardChart(
    state: UiState,
    range: String,
) {
    val hours = rangeHours(range)
    val now = remember { Instant.now() }
    val windowStart = now.minus(Duration.ofMinutes((hours * 60).toLong()))
    val samples = state.recentReadings
        .asReversed() // oldest first
        .filter { it.time.isAfter(windowStart) }
        .map { r ->
            val secondsFromStart = Duration.between(windowStart, r.time).seconds.toFloat()
            GlucoseSample(
                hour = (secondsFromStart / 3600f).coerceIn(0f, hours),
                mgDl = r.level.inMilligramsPerDeciliter.toFloat(),
            )
        }

    if (samples.size < 2) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "Not enough readings in the selected range.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }

    Column {
        RdGlucoseChart(
            samples = samples,
            lowMgDl = state.prefs.targetLowMgDl.toFloat(),
            highMgDl = state.prefs.targetHighMgDl.toFloat(),
            rangeHours = hours,
            yMin = state.prefs.chartMinMgDl.toFloat(),
            yMax = state.prefs.chartMaxMgDl.toFloat(),
            unitLabel = unitLabel(state.prefs.unit),
        )
        Spacer(Modifier.height(6.dp))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            chartTimeLabels(range).forEach { label ->
                Text(
                    label,
                    style = RdMono.Tiny,
                    color = rdSubtle(),
                )
            }
        }
    }
}

private fun chartTimeLabels(range: String): List<String> = when (range) {
    "6h" -> listOf("−6h", "−4", "−2", "now")
    "12h" -> listOf("−12h", "−9", "−6", "−3", "now")
    "24h" -> listOf("−24h", "−18", "−12", "−6", "now")
    "3d" -> listOf("−3d", "−2d", "−1d", "now")
    else -> emptyList()
}

// ─────────── TIR + stats ───────────

@Composable
private fun DashboardTIR(state: UiState) {
    val cutoff = Instant.now().minus(Duration.ofHours(24))
    val mgs = state.recentReadings
        .filter { it.time.isAfter(cutoff) }
        .map { it.level.inMilligramsPerDeciliter }
    val low = mgs.count { it < state.prefs.targetLowMgDl }
    val high = mgs.count { it > state.prefs.targetHighMgDl + state.prefs.warningBufferMgDl }
    val amber = mgs.count {
        it > state.prefs.targetHighMgDl &&
            it <= state.prefs.targetHighMgDl + state.prefs.warningBufferMgDl
    }
    val ok = mgs.size - low - high - amber
    RdTIRBar(low = low, ok = ok, amber = amber, high = high)
}

@Composable
private fun DashboardStatTrio(state: UiState) {
    val cutoff = Instant.now().minus(Duration.ofHours(24))
    val mgs = state.recentReadings
        .filter { it.time.isAfter(cutoff) }
        .map { it.level.inMilligramsPerDeciliter }
    val avg = if (mgs.isEmpty()) "—" else formatGlucose(mgs.average(), state.prefs.unit)
    val cv = if (mgs.size < 2) "—" else {
        val mean = mgs.average()
        val sd = kotlin.math.sqrt(mgs.map { (it - mean) * (it - mean) }.average())
        "${(sd / mean * 100).toInt()}%"
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        RdStatCard(
            label = "Average",
            value = avg,
            unit = if (mgs.isEmpty()) null else unitLabel(state.prefs.unit),
            modifier = Modifier.weight(1f),
        )
        RdStatCard(
            label = "Readings",
            value = mgs.size.toString(),
            modifier = Modifier.weight(1f),
        )
        RdStatCard(
            label = "Variability",
            value = cv,
            modifier = Modifier.weight(1f),
        )
    }
}

// ─────────── Pending review (redesign style) ───────────

@Composable
private fun RdPendingReview(
    staged: List<StagedReading>,
    unit: com.syschimp.glucoripper.data.GlucoseUnit,
    pushing: Boolean,
    onTap: (StagedReading) -> Unit,
    onSendAll: () -> Unit,
) {
    val visible = staged.take(3)
    val more = staged.size - visible.size
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                MaterialTheme.colorScheme.surfaceContainerLow,
                androidx.compose.foundation.shape.RoundedCornerShape(14.dp),
            )
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    "Pending review",
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontWeight = FontWeight.SemiBold,
                    ),
                )
                Text(
                    "${staged.size} reading${if (staged.size == 1) "" else "s"} awaiting send",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            Box(
                Modifier
                    .clickable(enabled = !pushing) { onSendAll() }
                    .background(
                        MaterialTheme.colorScheme.primary,
                        CircleShape,
                    )
                    .padding(horizontal = 14.dp, vertical = 8.dp),
            ) {
                if (pushing) {
                    CircularProgressIndicator(
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(14.dp),
                    )
                } else {
                    Text(
                        "Send all",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.SemiBold,
                        ),
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        visible.forEachIndexed { i, s ->
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(MaterialTheme.colorScheme.outlineVariant),
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onTap(s) }
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    formatTimeOnly(s.time),
                    style = RdMono.Caption,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.width(56.dp),
                )
                Spacer(Modifier.weight(1f))
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        formatGlucose(s.mgPerDl, unit),
                        style = RdMono.RowSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        unitLabel(unit),
                        style = RdMono.Tiny,
                        color = rdSubtle(),
                        modifier = Modifier.padding(start = 3.dp, bottom = 2.dp),
                    )
                }
            }
            if (i == visible.lastIndex) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(MaterialTheme.colorScheme.outlineVariant),
                )
            }
        }
        if (more > 0) {
            Spacer(Modifier.height(10.dp))
            Text(
                "… and $more more",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp),
            )
        }
    }
}

