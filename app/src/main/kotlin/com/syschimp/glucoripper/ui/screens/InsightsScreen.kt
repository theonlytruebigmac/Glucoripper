package com.syschimp.glucoripper.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.syschimp.glucoripper.data.GlucoseUnit
import com.syschimp.glucoripper.data.stats.Aggregate
import com.syschimp.glucoripper.data.stats.ContextAggregate
import com.syschimp.glucoripper.data.stats.GlucoseStats
import com.syschimp.glucoripper.data.stats.PairedMealDelta
import com.syschimp.glucoripper.data.stats.StatsWindow
import com.syschimp.glucoripper.data.stats.computeStats
import com.syschimp.glucoripper.ui.UiState
import com.syschimp.glucoripper.ui.components.RdHairline
import com.syschimp.glucoripper.ui.components.RdOverlineText
import com.syschimp.glucoripper.ui.components.RdSectionHeader
import com.syschimp.glucoripper.ui.components.RdStatCard
import com.syschimp.glucoripper.ui.components.RdTIRBar
import com.syschimp.glucoripper.ui.components.rdSubtle
import com.syschimp.glucoripper.ui.format.formatGlucose
import com.syschimp.glucoripper.ui.format.unitLabel
import com.syschimp.glucoripper.ui.theme.GlucoseElevated
import com.syschimp.glucoripper.ui.theme.GlucoseInRange
import com.syschimp.glucoripper.ui.theme.GlucoseLow
import com.syschimp.glucoripper.ui.theme.RdMono

@Composable
fun InsightsScreen(state: UiState) {
    var window by remember { mutableStateOf(StatsWindow.W7) }
    val stats = remember(state.recentReadings, state.annotations, state.prefs, window) {
        computeStats(
            readings = state.recentReadings,
            annotations = state.annotations,
            prefs = state.prefs,
            window = window,
        )
    }

    Scaffold(
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0),
        containerColor = MaterialTheme.colorScheme.surface,
    ) { inner ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(inner),
            contentPadding = PaddingValues(
                start = 16.dp, end = 16.dp, top = 14.dp, bottom = 24.dp,
            ),
        ) {
            item { PeriodSelector(window, onSelect = { window = it }) }
            item { Spacer(Modifier.height(18.dp)) }

            if (stats.overall.count == 0) {
                item {
                    Text(
                        "No readings in the last ${window.label}.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 40.dp),
                    )
                }
                return@LazyColumn
            }

            item { HeroAvgAndGmi(stats, state.prefs.unit) }
            item { Spacer(Modifier.height(18.dp)) }
            item {
                RdOverlineText("Time in range")
                Spacer(Modifier.height(8.dp))
                InsightsTIR(stats)
                Spacer(Modifier.height(20.dp))
                RdHairline()
            }
            item { MetricStrip(stats, state.prefs.unit) }
            item { RdHairline() }

            item {
                RdSectionHeader(overline = "vs prior ${window.label}")
                if (stats.periodDelta.priorAvgMgDl == null) {
                    Text(
                        "No prior-period data yet for comparison.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                } else {
                    PeriodDeltaRow(stats, state.prefs.unit)
                }
            }

            item {
                RdSectionHeader(overline = "By meal context")
            }
            stats.byContext.forEach { ctx ->
                item(key = "ctx-${ctx.relationCode}") {
                    ContextRangeCard(ctx = ctx, unit = state.prefs.unit)
                }
            }

            item {
                RdSectionHeader(
                    overline = "Post-meal rise",
                    trailing = {
                        Text(
                            "n=${stats.pairedMealDelta.pairCount}",
                            style = RdMono.Caption,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                )
                PostMealBars(stats.pairedMealDelta, unit = state.prefs.unit)
            }

            item {
                RdSectionHeader(overline = "By time of day")
                Spacer(Modifier.height(8.dp))
                TimeOfDayRow(stats, state.prefs.unit)
            }

            item {
                RdSectionHeader(overline = "Tag mix")
                TagMixRow(stats)
            }

            item {
                RdSectionHeader(overline = "Events")
                Spacer(Modifier.height(8.dp))
                EventsRow(stats.overall, state.prefs.unit)
            }
        }
    }
}

// ─────────── Period selector (full-width 4-button row) ───────────

@Composable
private fun PeriodSelector(
    selected: StatsWindow,
    onSelect: (StatsWindow) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        StatsWindow.entries.forEach { w ->
            val isSel = selected == w
            Box(
                Modifier
                    .weight(1f)
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
                    .background(
                        if (isSel) MaterialTheme.colorScheme.onSurface else Color.Transparent,
                        RoundedCornerShape(8.dp),
                    )
                    .clickable { onSelect(w) }
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    w.label,
                    style = RdMono.Label.copy(fontWeight = FontWeight.SemiBold),
                    color = if (isSel) MaterialTheme.colorScheme.surface
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

// ─────────── Hero: huge mono avg + GMI ───────────

@Composable
private fun HeroAvgAndGmi(stats: GlucoseStats, unit: GlucoseUnit) {
    Row(
        Modifier.fillMaxWidth().padding(top = 8.dp),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Column(Modifier.weight(1f)) {
            RdOverlineText("Average · ${unitLabel(unit)}")
            Spacer(Modifier.height(4.dp))
            Text(
                stats.overall.avgMgDl?.let { formatGlucose(it, unit) } ?: "—",
                style = RdMono.DisplayLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        Column(modifier = Modifier.padding(bottom = 8.dp)) {
            RdOverlineText("GMI")
            Spacer(Modifier.height(4.dp))
            Text(
                stats.overall.gmiPercent?.let { "%.1f%%".format(it) } ?: "—",
                style = RdMono.LargeReadout,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

// ─────────── TIR helpers ───────────

@Composable
private fun InsightsTIR(stats: GlucoseStats) {
    val a = stats.overall
    val total = a.count.coerceAtLeast(1)
    val low = a.lowPct?.let { it * total / 100 } ?: 0
    val high = a.highPct?.let { it * total / 100 } ?: 0
    val ok = (a.inRangePct ?: 0) * total / 100
    val amber = (total - low - ok - high).coerceAtLeast(0)
    RdTIRBar(low = low, ok = ok, amber = amber, high = high)
}

// ─────────── 4-column metric strip ───────────

@Composable
private fun MetricStrip(stats: GlucoseStats, unit: GlucoseUnit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 20.dp),
    ) {
        Metric(
            label = "Variability",
            value = stats.overall.cvPercent?.let { "%.0f%%".format(it) } ?: "—",
            valueColor = cvColor(stats.overall.cvPercent),
            withDivider = false,
            modifier = Modifier.weight(1f),
        )
        Metric(
            label = "Median",
            value = stats.overall.medianMgDl?.let { formatGlucose(it, unit) } ?: "—",
            withDivider = true,
            modifier = Modifier.weight(1f),
        )
        Metric(
            label = "Range",
            value = if (stats.overall.minMgDl != null && stats.overall.maxMgDl != null)
                "${formatGlucose(stats.overall.minMgDl, unit)}–${formatGlucose(stats.overall.maxMgDl, unit)}"
            else "—",
            withDivider = true,
            small = true,
            modifier = Modifier.weight(1f),
        )
        Metric(
            label = "Per day",
            value = stats.readingsPerDay?.let { "%.1f".format(it) } ?: "—",
            withDivider = true,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun Metric(
    label: String,
    value: String,
    withDivider: Boolean,
    small: Boolean = false,
    valueColor: Color = MaterialTheme.colorScheme.onSurface,
    modifier: Modifier = Modifier,
) {
    Row(modifier) {
        if (withDivider) {
            Box(
                Modifier
                    .width(1.dp)
                    .fillMaxHeight()
                    .background(MaterialTheme.colorScheme.outlineVariant),
            )
        }
        Column(Modifier.padding(horizontal = 10.dp)) {
            Text(
                label.uppercase(),
                style = RdMono.Tiny.copy(letterSpacing = 1.2f.sp(), fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                value,
                style = if (small) RdMono.RowSmall else RdMono.LargeReadout,
                color = valueColor,
            )
        }
    }
}

// Local helper to produce a sp TextUnit from Int — matches `0.5f.sp` ergonomics
private fun Float.sp(): androidx.compose.ui.unit.TextUnit =
    androidx.compose.ui.unit.TextUnit(this, androidx.compose.ui.unit.TextUnitType.Sp)

// ─────────── Period delta ───────────

@Composable
private fun PeriodDeltaRow(stats: GlucoseStats, unit: GlucoseUnit) {
    val d = stats.periodDelta
    Row(
        Modifier.fillMaxWidth().padding(top = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        DeltaBlock(
            label = "Avg glucose",
            now = d.currentAvgMgDl?.let { formatGlucose(it, unit) + " " + unitLabel(unit) } ?: "—",
            delta = d.avgDeltaMgDl?.let { "%+.0f %s".format(it, unitLabel(unit)) },
            isGood = d.avgDeltaMgDl?.let { it < 0 },
        )
        DeltaBlock(
            label = "In-range",
            now = d.currentTirPct?.let { "$it%" } ?: "—",
            delta = d.tirDeltaPct?.let { "%+d pts".format(it) },
            isGood = d.tirDeltaPct?.let { it > 0 },
        )
    }
}

@Composable
private fun DeltaBlock(label: String, now: String, delta: String?, isGood: Boolean?) {
    Column {
        RdOverlineText(label)
        Spacer(Modifier.height(4.dp))
        Text(
            now,
            style = RdMono.LargeReadout,
            color = MaterialTheme.colorScheme.onSurface,
        )
        if (delta != null) {
            Spacer(Modifier.height(4.dp))
            val color = when (isGood) {
                true -> GlucoseInRange
                false -> GlucoseElevated
                null -> MaterialTheme.colorScheme.onSurfaceVariant
            }
            Text(
                delta,
                style = RdMono.Caption.copy(fontWeight = FontWeight.SemiBold),
                color = color,
            )
        }
    }
}

// ─────────── Context range card (horizontal range bar) ───────────

@Composable
private fun ContextRangeCard(ctx: ContextAggregate, unit: GlucoseUnit) {
    val ag = ctx.aggregate
    val pct = ag.inRangePct ?: 0
    val ok = pct >= 70
    val pctColor = if (ok) GlucoseInRange else GlucoseElevated

    val min = 50.0
    val max = 200.0
    val lo = ctx.targetLowMgDl
    val hi = ctx.targetHighMgDl
    val avg = ag.avgMgDl ?: ((lo + hi) / 2.0)
    val loF = ((lo - min) / (max - min)).coerceIn(0.0, 1.0).toFloat()
    val hiF = ((hi - min) / (max - min)).coerceIn(0.0, 1.0).toFloat()
    val avgF = ((avg - min) / (max - min)).coerceIn(0.0, 1.0).toFloat()

    Column(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 14.dp),
    ) {
        Row(
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                ctx.label,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.SemiBold,
                ),
                modifier = Modifier.weight(1f),
            )
            Text(
                "tgt ${formatGlucose(lo, unit)}–${formatGlucose(hi, unit)}",
                style = RdMono.Caption,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                "$pct%",
                style = RdMono.Label.copy(fontWeight = FontWeight.SemiBold),
                color = pctColor,
            )
        }
        if (ag.count == 0) {
            Spacer(Modifier.height(6.dp))
            Text(
                "No ${ctx.label.lowercase()} readings yet.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@Column
        }
        Spacer(Modifier.height(10.dp))
        BoxWithConstraints(Modifier.fillMaxWidth().height(20.dp)) {
            val w = maxWidth
            // base track
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 9.dp)
                    .height(2.dp)
                    .background(MaterialTheme.colorScheme.outlineVariant),
            )
            // active segment
            Box(
                Modifier
                    .padding(start = w * loF, top = 9.dp)
                    .height(2.dp)
                    .width(w * (hiF - loF))
                    .background(MaterialTheme.colorScheme.onSurface),
            )
            // avg dot
            Box(
                Modifier
                    .padding(start = w * avgF - 6.dp, top = 4.dp)
                    .size(12.dp)
                    .background(
                        MaterialTheme.colorScheme.surface,
                        CircleShape,
                    )
                    .border(
                        2.dp,
                        MaterialTheme.colorScheme.onSurface,
                        CircleShape,
                    ),
            )
        }
        Spacer(Modifier.height(6.dp))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                "${lo.toInt()}",
                style = RdMono.Tiny,
                color = rdSubtle(),
            )
            Text(
                "avg ${avg.toInt()}",
                style = RdMono.Tiny,
                color = rdSubtle(),
            )
            Text(
                "${hi.toInt()}",
                style = RdMono.Tiny,
                color = rdSubtle(),
            )
        }
        Spacer(Modifier.height(6.dp))
        RdHairline()
    }
}

// ─────────── Post-meal rise (dual horizontal bars) ───────────

@Composable
private fun PostMealBars(d: PairedMealDelta, unit: GlucoseUnit) {
    if (d.pairCount == 0) {
        Text(
            "No matched pre/post-meal pairs yet. Tag readings as Before meal and After meal to enable this metric.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp),
        )
        return
    }
    val pre = d.avgPreMgDl ?: 0.0
    val post = d.avgPostMgDl ?: 0.0
    val delta = d.avgDeltaMgDl ?: 0.0
    val maxV = maxOf(pre, post, 200.0)
    val preF = (pre / maxV).toFloat()
    val postF = (post / maxV).toFloat()

    Column(Modifier.padding(top = 8.dp)) {
        DualBar(label = "Before", value = pre.toInt(), fraction = preF, unit = unitLabel(unit), color = MaterialTheme.colorScheme.onSurfaceVariant)
        DualBar(label = "After", value = post.toInt(), fraction = postF, unit = unitLabel(unit), color = GlucoseElevated)
        Row(
            Modifier.padding(start = 66.dp, top = 8.dp),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                "+${delta.toInt()}",
                style = RdMono.Label.copy(fontWeight = FontWeight.SemiBold),
                color = GlucoseElevated,
            )
            Text(
                "average climb after eating",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun DualBar(label: String, value: Int, fraction: Float, unit: String, color: Color) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(56.dp),
        )
        BoxWithConstraints(
            Modifier
                .weight(1f)
                .height(14.dp)
                .background(MaterialTheme.colorScheme.surfaceContainerLow, RoundedCornerShape(4.dp)),
        ) {
            Box(
                Modifier
                    .fillMaxHeight()
                    .width(maxWidth * fraction.coerceIn(0f, 1f))
                    .background(color, RoundedCornerShape(4.dp)),
            )
        }
        Row(
            modifier = Modifier.width(60.dp),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.Bottom,
        ) {
            Text(
                value.toString(),
                style = RdMono.RowSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                unit,
                style = RdMono.Tiny,
                color = rdSubtle(),
                modifier = Modifier.padding(start = 2.dp, bottom = 2.dp),
            )
        }
    }
}

// ─────────── By time of day (4-column mini bar chart) ───────────

@Composable
private fun TimeOfDayRow(stats: GlucoseStats, unit: GlucoseUnit) {
    val maxAvg = stats.timeOfDay.mapNotNull { it.avgMgDl }.maxOrNull() ?: 1.0
    val barMax = maxOf(maxAvg, 200.0)
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        stats.timeOfDay.forEach { bucket ->
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                val f = ((bucket.avgMgDl ?: 0.0) / barMax).coerceIn(0.0, 1.0).toFloat()
                val barColor = when {
                    bucket.avgMgDl == null -> MaterialTheme.colorScheme.outlineVariant
                    bucket.avgMgDl > 130.0 -> GlucoseElevated
                    else -> GlucoseInRange
                }
                Box(
                    Modifier
                        .width(24.dp)
                        .height(90.dp)
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(3.dp)),
                    contentAlignment = Alignment.BottomCenter,
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height((90 * f).dp)
                            .background(barColor, RoundedCornerShape(3.dp)),
                    )
                }
                Text(
                    bucket.avgMgDl?.let { it.toInt().toString() } ?: "—",
                    style = RdMono.Label,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    bucket.label,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 9f.sp()),
                    color = rdSubtle(),
                )
            }
        }
    }
}

// ─────────── Tag mix ───────────

@Composable
private fun TagMixRow(stats: GlucoseStats) {
    val palette = listOf(
        GlucoseInRange,
        MaterialTheme.colorScheme.onSurface,
        GlucoseElevated,
        MaterialTheme.colorScheme.onSurfaceVariant,
        rdSubtle(),
    )
    val total = stats.contextMix.sumOf { it.count }.coerceAtLeast(1)
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
            .height(6.dp),
    ) {
        stats.contextMix.forEachIndexed { i, slice ->
            Box(
                Modifier
                    .weight(slice.count.toFloat().coerceAtLeast(0.001f))
                    .fillMaxHeight()
                    .background(palette[i % palette.size]),
            )
        }
    }
    Spacer(Modifier.height(14.dp))
    Column {
        stats.contextMix.forEachIndexed { i, slice ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Box(
                    Modifier
                        .size(8.dp)
                        .background(palette[i % palette.size], RoundedCornerShape(2.dp)),
                )
                Text(
                    slice.label,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    "${slice.percent}% · ${slice.count}",
                    style = RdMono.Caption,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

// ─────────── Events ───────────

@Composable
private fun EventsRow(a: Aggregate, unit: GlucoseUnit) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        RdStatCard(
            label = "Hypo",
            value = a.hypoEvents.toString(),
            modifier = Modifier.weight(1f),
        )
        RdStatCard(
            label = "Hyper",
            value = a.hyperEvents.toString(),
            modifier = Modifier.weight(1f),
        )
        RdStatCard(
            label = "Low %",
            value = a.lowPct?.let { "$it%" } ?: "—",
            modifier = Modifier.weight(1f),
        )
        RdStatCard(
            label = "High %",
            value = a.highPct?.let { "$it%" } ?: "—",
            modifier = Modifier.weight(1f),
        )
    }
}

private fun cvColor(cv: Double?): Color = when {
    cv == null -> Color.Unspecified
    cv < 36 -> GlucoseInRange
    cv < 45 -> GlucoseElevated
    else -> GlucoseLow
}
