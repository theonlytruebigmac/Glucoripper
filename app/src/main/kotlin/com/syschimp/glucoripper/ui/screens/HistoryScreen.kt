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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.health.connect.client.records.BloodGlucoseRecord
import com.syschimp.glucoripper.data.Feeling
import com.syschimp.glucoripper.data.GlucoseUnit
import com.syschimp.glucoripper.ui.ReadingDetailSheet
import com.syschimp.glucoripper.ui.UiState
import com.syschimp.glucoripper.ui.components.TimelineCard
import com.syschimp.glucoripper.ui.format.formatGlucose
import com.syschimp.glucoripper.ui.format.unitLabel
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import kotlin.math.roundToInt

private val dayHeaderFormatter = DateTimeFormatter.ofPattern("EEEE, MMMM d")

@Composable
fun HistoryScreen(
    state: UiState,
    onSetMealRelation: (BloodGlucoseRecord, Int) -> Unit,
    onSetFeeling: (String?, Feeling?) -> Unit,
    onSetNote: (String?, String?) -> Unit,
) {
    var selectedReading by remember { mutableStateOf<BloodGlucoseRecord?>(null) }

    val zone = ZoneId.systemDefault()
    val grouped = remember(state.recentReadings) {
        state.recentReadings
            .groupBy { it.time.atZone(zone).toLocalDate() }
            .toSortedMap(compareByDescending { it })
    }

    // 7-day in-range percentage for summary card
    val weekAgo = Instant.now().minus(Duration.ofDays(7))
    val lastWeek = state.recentReadings.filter { it.time.isAfter(weekAgo) }
    val lowTarget = state.prefs.targetLowMgDl
    val highTarget = state.prefs.targetHighMgDl
    val inRangeCount = lastWeek.count { it.level.inMilligramsPerDeciliter in lowTarget..highTarget }
    val inRangePct = if (lastWeek.isEmpty()) 0
    else (inRangeCount * 100.0 / lastWeek.size).roundToInt().coerceIn(0, 100)

    Scaffold(
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0),
        containerColor = MaterialTheme.colorScheme.surface,
    ) { inner ->
        Column(Modifier.fillMaxSize().padding(inner)) {
            if (state.recentReadings.isEmpty()) {
                EmptyHistoryState(Modifier.fillMaxSize().padding(24.dp))
                return@Column
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = 4.dp,
                    bottom = 24.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item {
                    Box(Modifier.fillMaxWidth()) {
                        Text(
                            "History",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.align(Alignment.Center),
                        )
                    }
                }

                item { WeekSummaryCard(inRangePct = inRangePct) }

                grouped.forEach { (date, readings) ->
                    item(key = "hdr-$date") {
                        DayHeader(
                            date = date,
                            readings = readings,
                            unit = state.prefs.unit,
                            lowMgDl = lowTarget,
                            highMgDl = highTarget,
                        )
                    }
                    items(readings, keyProvider = { it.metadata.id }) { r ->
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
}

// Tiny wrapper so `items(list, keyProvider = ...)` reads naturally; the stdlib
// `items` expects `key: (T) -> Any?`.
private inline fun <T : Any> androidx.compose.foundation.lazy.LazyListScope.items(
    list: List<T>,
    noinline keyProvider: (T) -> Any,
    crossinline content: @Composable (T) -> Unit,
) = items(count = list.size, key = { keyProvider(list[it]) }) { content(list[it]) }

@Composable
private fun WeekSummaryCard(inRangePct: Int) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    "7-Day Average",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    "$inRangePct% In-Target",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            InRangeBadge(pct = inRangePct)
        }
    }
}

@Composable
private fun InRangeBadge(pct: Int) {
    val color = when {
        pct >= 70 -> Color(0xFF30A46C)
        pct >= 50 -> Color(0xFFF5A524)
        else -> Color(0xFFE5484D)
    }
    Box(
        modifier = Modifier
            .size(48.dp)
            .background(color.copy(alpha = 0.15f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            "$pct%",
            style = MaterialTheme.typography.labelLarge,
            color = color,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun DayHeader(
    date: LocalDate,
    readings: List<BloodGlucoseRecord>,
    unit: GlucoseUnit,
    lowMgDl: Double,
    highMgDl: Double,
) {
    val today = LocalDate.now()
    val label = when {
        date == today -> "Today"
        date == today.minusDays(1) -> "Yesterday"
        ChronoUnit.DAYS.between(date, today) < 7 -> dayHeaderFormatter.format(date)
        else -> dayHeaderFormatter.format(date)
    }
    val avgMgDl = readings.map { it.level.inMilligramsPerDeciliter }.average()
    val low = readings.count { it.level.inMilligramsPerDeciliter < lowMgDl }
    val high = readings.count { it.level.inMilligramsPerDeciliter > highMgDl }
    val inRange = readings.size - low - high

    Column(
        Modifier
            .fillMaxWidth()
            .padding(start = 4.dp, end = 4.dp, top = 8.dp, bottom = 2.dp),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                label,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                "${readings.size} · avg ${formatGlucose(avgMgDl, unit)} ${unitLabel(unit)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            "$inRange in range · $low low · $high high",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun EmptyHistoryState(modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(
            "No readings yet",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            "Once you sync your meter, your readings will appear here grouped by day.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
