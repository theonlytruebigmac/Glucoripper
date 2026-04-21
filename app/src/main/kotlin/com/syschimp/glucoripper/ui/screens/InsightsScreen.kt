package com.syschimp.glucoripper.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.syschimp.glucoripper.data.GlucoseUnit
import com.syschimp.glucoripper.data.stats.Aggregate
import com.syschimp.glucoripper.data.stats.ContextAggregate
import com.syschimp.glucoripper.data.stats.GlucoseStats
import com.syschimp.glucoripper.data.stats.PeriodDelta
import com.syschimp.glucoripper.data.stats.StatsWindow
import com.syschimp.glucoripper.data.stats.computeStats
import com.syschimp.glucoripper.ui.UiState
import com.syschimp.glucoripper.ui.format.formatGlucose
import com.syschimp.glucoripper.ui.format.unitLabel
import com.syschimp.glucoripper.ui.theme.GlucoseHigh
import com.syschimp.glucoripper.ui.theme.GlucoseInRange
import com.syschimp.glucoripper.ui.theme.GlucoseLow
import kotlin.math.abs

@Composable
fun InsightsScreen(state: UiState) {
    var window by remember { mutableStateOf(StatsWindow.W14) }
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
                start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                Box(Modifier.fillMaxWidth()) {
                    Text(
                        "Insights",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.align(Alignment.Center),
                    )
                }
            }

            item {
                WindowChips(selected = window, onSelect = { window = it })
            }

            if (stats.overall.count == 0) {
                item { EmptyInsightsCard(window) }
                return@LazyColumn
            }

            item {
                OverviewCard(
                    stats = stats,
                    unit = state.prefs.unit,
                )
            }

            item {
                PeriodDeltaCard(
                    delta = stats.periodDelta,
                    window = stats.window,
                    unit = state.prefs.unit,
                )
            }

            item {
                SectionHeader("By meal context")
            }
            stats.byContext.forEach { ctx ->
                item(key = "ctx-${ctx.relationCode}") {
                    ContextCard(
                        ctx = ctx,
                        unit = state.prefs.unit,
                    )
                }
            }

            item {
                PairedMealCard(
                    delta = stats.pairedMealDelta,
                    unit = state.prefs.unit,
                )
            }

            item {
                TimeOfDayCard(
                    stats = stats,
                    unit = state.prefs.unit,
                )
            }

            item {
                ContextMixCard(stats = stats)
            }

            item {
                EventsCard(aggregate = stats.overall, unit = state.prefs.unit)
            }
        }
    }
}

@Composable
private fun WindowChips(
    selected: StatsWindow,
    onSelect: (StatsWindow) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        StatsWindow.entries.forEach { w ->
            FilterChip(
                selected = selected == w,
                onClick = { onSelect(w) },
                label = { Text(w.label) },
            )
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(start = 4.dp, top = 4.dp),
    )
}

@Composable
private fun EmptyInsightsCard(window: StatsWindow) {
    InsightsCard {
        Text(
            "Not enough data",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            "No readings in the last ${window.label}. Sync your meter to start building insights.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun OverviewCard(stats: GlucoseStats, unit: GlucoseUnit) {
    InsightsCard {
        Text(
            "Overview · last ${stats.window.label}",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                stats.overall.avgMgDl?.let { formatGlucose(it, unit) } ?: "—",
                style = MaterialTheme.typography.displayMedium,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.width(6.dp))
            Text(
                unitLabel(unit),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp),
            )
            Spacer(Modifier.width(12.dp))
            Text(
                "avg",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 12.dp),
            )
        }
        HorizontalDivider()
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            MetricBlock(
                label = "GMI",
                value = stats.overall.gmiPercent?.let { "%.1f%%".format(it) } ?: "—",
                sub = "est. A1C",
            )
            MetricBlock(
                label = "Time in range",
                value = stats.overall.inRangePct?.let { "$it%" } ?: "—",
                sub = "${stats.overall.count} reading${if (stats.overall.count == 1) "" else "s"}",
                valueColor = GlucoseInRange,
            )
            MetricBlock(
                label = "Variability",
                value = stats.overall.cvPercent?.let { "%.0f%%".format(it) } ?: "—",
                sub = cvQualityLabel(stats.overall.cvPercent),
                valueColor = cvQualityColor(stats.overall.cvPercent),
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            MetricBlock(
                label = "Median",
                value = stats.overall.medianMgDl?.let { formatGlucose(it, unit) } ?: "—",
                sub = unitLabel(unit),
            )
            MetricBlock(
                label = "Range",
                value = if (stats.overall.minMgDl != null && stats.overall.maxMgDl != null) {
                    "${formatGlucose(stats.overall.minMgDl, unit)}–${formatGlucose(stats.overall.maxMgDl, unit)}"
                } else "—",
                sub = unitLabel(unit),
            )
            MetricBlock(
                label = "Per day",
                value = stats.readingsPerDay?.let { "%.1f".format(it) } ?: "—",
                sub = "readings",
            )
        }
    }
}

@Composable
private fun MetricBlock(
    label: String,
    value: String,
    sub: String? = null,
    valueColor: Color = MaterialTheme.colorScheme.onSurface,
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            value,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            color = valueColor,
        )
        if (sub != null) {
            Text(
                sub,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun PeriodDeltaCard(
    delta: PeriodDelta,
    window: StatsWindow,
    unit: GlucoseUnit,
) {
    InsightsCard {
        Text(
            "Trend vs prior ${window.label}",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            TrendBlock(
                label = "Avg glucose",
                nowValue = delta.currentAvgMgDl?.let { formatGlucose(it, unit) + " " + unitLabel(unit) } ?: "—",
                deltaText = delta.avgDeltaMgDl?.let {
                    "%+.0f %s".format(it, unitLabel(unit))
                },
                deltaIsGood = delta.avgDeltaMgDl?.let { it < 0 },
            )
            TrendBlock(
                label = "In-range",
                nowValue = delta.currentTirPct?.let { "$it%" } ?: "—",
                deltaText = delta.tirDeltaPct?.let {
                    "%+d pts".format(it)
                },
                deltaIsGood = delta.tirDeltaPct?.let { it > 0 },
            )
        }
        if (delta.priorAvgMgDl == null) {
            Text(
                "No prior-period data yet for comparison.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun TrendBlock(
    label: String,
    nowValue: String,
    deltaText: String?,
    deltaIsGood: Boolean?,
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            nowValue,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
        )
        if (deltaText != null) {
            val color = when (deltaIsGood) {
                true -> GlucoseInRange
                false -> GlucoseHigh
                null -> MaterialTheme.colorScheme.onSurfaceVariant
            }
            Text(
                deltaText,
                style = MaterialTheme.typography.labelMedium,
                color = color,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

@Composable
private fun ContextCard(ctx: ContextAggregate, unit: GlucoseUnit) {
    InsightsCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text(
                    ctx.label,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    "target ${formatGlucose(ctx.targetLowMgDl, unit)}–${formatGlucose(ctx.targetHighMgDl, unit)} ${unitLabel(unit)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TirPill(pct = ctx.aggregate.inRangePct)
        }
        if (ctx.aggregate.count == 0) {
            Text(
                "No ${ctx.label.lowercase()} readings in this window.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@InsightsCard
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            MetricBlock(
                label = "Average",
                value = ctx.aggregate.avgMgDl?.let { formatGlucose(it, unit) } ?: "—",
                sub = unitLabel(unit),
            )
            MetricBlock(
                label = "Median",
                value = ctx.aggregate.medianMgDl?.let { formatGlucose(it, unit) } ?: "—",
                sub = unitLabel(unit),
            )
            MetricBlock(
                label = "Range",
                value = if (ctx.aggregate.minMgDl != null && ctx.aggregate.maxMgDl != null) {
                    "${formatGlucose(ctx.aggregate.minMgDl, unit)}–${formatGlucose(ctx.aggregate.maxMgDl, unit)}"
                } else "—",
                sub = "n=${ctx.aggregate.count}",
            )
        }
    }
}

@Composable
private fun TirPill(pct: Int?) {
    val color = when {
        pct == null -> MaterialTheme.colorScheme.outline
        pct >= 70 -> Color(0xFF30A46C)
        pct >= 50 -> Color(0xFFF5A524)
        else -> Color(0xFFE5484D)
    }
    Box(
        modifier = Modifier
            .background(color.copy(alpha = 0.15f), RoundedCornerShape(50))
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Text(
            pct?.let { "$it%  in range" } ?: "— in range",
            style = MaterialTheme.typography.labelMedium,
            color = color,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun PairedMealCard(
    delta: com.syschimp.glucoripper.data.stats.PairedMealDelta,
    unit: GlucoseUnit,
) {
    InsightsCard {
        Text(
            "Post-meal rise",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            "Pre→post-meal pairs within 3 h. Tracks how much glucose climbs after you eat.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (delta.pairCount == 0) {
            Text(
                "No matched pre/post-meal pairs yet. Tag readings as Before meal and After meal to enable this metric.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@InsightsCard
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            MetricBlock(
                label = "Avg pre",
                value = delta.avgPreMgDl?.let { formatGlucose(it, unit) } ?: "—",
                sub = unitLabel(unit),
            )
            MetricBlock(
                label = "Avg post",
                value = delta.avgPostMgDl?.let { formatGlucose(it, unit) } ?: "—",
                sub = unitLabel(unit),
            )
            MetricBlock(
                label = "Delta",
                value = delta.avgDeltaMgDl?.let {
                    "%+.0f".format(it)
                } ?: "—",
                sub = "${unitLabel(unit)} · n=${delta.pairCount}",
                valueColor = when {
                    delta.avgDeltaMgDl == null -> MaterialTheme.colorScheme.onSurface
                    delta.avgDeltaMgDl > 60 -> GlucoseHigh
                    else -> MaterialTheme.colorScheme.onSurface
                },
            )
        }
    }
}

@Composable
private fun TimeOfDayCard(stats: GlucoseStats, unit: GlucoseUnit) {
    InsightsCard {
        Text(
            "By time of day",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        val maxAvg = stats.timeOfDay.mapNotNull { it.avgMgDl }.maxOrNull() ?: 0.0
        stats.timeOfDay.forEach { bucket ->
            Column(
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        "${bucket.label}  ·  ${"%02d".format(bucket.hourStartInclusive)}:00–${"%02d".format(bucket.hourEndInclusive)}:59",
                        style = MaterialTheme.typography.labelMedium,
                    )
                    Text(
                        bucket.avgMgDl?.let {
                            "${formatGlucose(it, unit)} ${unitLabel(unit)} · n=${bucket.count}"
                        } ?: "n=${bucket.count}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TodBar(
                    fraction = if (maxAvg > 0 && bucket.avgMgDl != null)
                        (bucket.avgMgDl / maxAvg).toFloat() else 0f,
                    color = barColor(
                        bucket.avgMgDl,
                        stats.overall.avgMgDl,
                    ),
                )
            }
        }
    }
}

@Composable
private fun TodBar(fraction: Float, color: Color) {
    val track = MaterialTheme.colorScheme.surfaceContainer
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(10.dp)
            .clip(RoundedCornerShape(5.dp)),
    ) {
        drawRoundRect(
            color = track,
            cornerRadius = CornerRadius(size.height / 2f, size.height / 2f),
        )
        if (fraction > 0f) {
            drawRoundRect(
                color = color,
                topLeft = Offset(0f, 0f),
                size = Size(size.width * fraction.coerceIn(0f, 1f), size.height),
                cornerRadius = CornerRadius(size.height / 2f, size.height / 2f),
            )
        }
    }
}

@Composable
private fun ContextMixCard(stats: GlucoseStats) {
    InsightsCard {
        Text(
            "Tag mix",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            "Share of readings in each meal context. Untagged readings only count toward the General target.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        val palette = listOf(
            Color(0xFF30A46C),
            Color(0xFF3B82F6),
            Color(0xFFF59E0B),
            Color(0xFF8B5CF6),
            Color(0xFF9CA3AF),
        )
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(14.dp)
                .clip(RoundedCornerShape(7.dp)),
        ) {
            val total = stats.contextMix.sumOf { it.count }.coerceAtLeast(1).toFloat()
            var x = 0f
            stats.contextMix.forEachIndexed { idx, slice ->
                val w = size.width * (slice.count / total)
                drawRoundRect(
                    color = palette[idx % palette.size],
                    topLeft = Offset(x, 0f),
                    size = Size(w, size.height),
                    cornerRadius = CornerRadius(0f, 0f),
                )
                x += w
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            stats.contextMix.forEachIndexed { idx, slice ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .width(10.dp)
                            .height(10.dp)
                            .background(palette[idx % palette.size], RoundedCornerShape(2.dp)),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        slice.label,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        "${slice.percent}%  (${slice.count})",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun EventsCard(aggregate: Aggregate, unit: GlucoseUnit) {
    InsightsCard {
        Text(
            "Events",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            MetricBlock(
                label = "Hypo",
                value = aggregate.hypoEvents.toString(),
                sub = "< ${formatGlucose(70.0, unit)} ${unitLabel(unit)}",
                valueColor = GlucoseLow,
            )
            MetricBlock(
                label = "Hyper",
                value = aggregate.hyperEvents.toString(),
                sub = "> ${formatGlucose(180.0, unit)} ${unitLabel(unit)}",
                valueColor = GlucoseHigh,
            )
            MetricBlock(
                label = "Low %",
                value = aggregate.lowPct?.let { "$it%" } ?: "—",
                sub = null,
            )
            MetricBlock(
                label = "High %",
                value = aggregate.highPct?.let { "$it%" } ?: "—",
                sub = null,
            )
        }
    }
}

@Composable
private fun InsightsCard(content: @Composable () -> Unit) {
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

private fun cvQualityLabel(cv: Double?): String = when {
    cv == null -> "—"
    cv < 36 -> "stable"
    cv < 45 -> "fair"
    else -> "high"
}

private fun cvQualityColor(cv: Double?): Color = when {
    cv == null -> Color.Unspecified
    cv < 36 -> GlucoseInRange
    cv < 45 -> Color(0xFFF5A524)
    else -> GlucoseHigh
}

private fun barColor(bucketAvg: Double?, overallAvg: Double?): Color {
    if (bucketAvg == null || overallAvg == null) return Color(0xFF9CA3AF)
    val diff = bucketAvg - overallAvg
    return when {
        abs(diff) < 10 -> GlucoseInRange
        diff >= 10 -> GlucoseHigh
        else -> GlucoseLow
    }
}
