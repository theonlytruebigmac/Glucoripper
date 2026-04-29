package com.syschimp.glucoripper.ui.screens

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.WaterDrop
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.health.connect.client.records.BloodGlucoseRecord
import com.syschimp.glucoripper.data.Feeling
import com.syschimp.glucoripper.data.targetRangeFor
import com.syschimp.glucoripper.ui.ReadingDetailSheet
import com.syschimp.glucoripper.ui.UiState
import com.syschimp.glucoripper.ui.components.RdEmptyState
import com.syschimp.glucoripper.ui.components.RdOverlineText
import com.syschimp.glucoripper.ui.components.RdReadingRow
import com.syschimp.glucoripper.ui.components.RdTIRBar
import com.syschimp.glucoripper.ui.components.bandColor
import com.syschimp.glucoripper.ui.components.formatTimeOnly
import com.syschimp.glucoripper.ui.components.relationShort
import com.syschimp.glucoripper.ui.format.formatGlucose
import com.syschimp.glucoripper.ui.format.unitLabel
import com.syschimp.glucoripper.ui.theme.GlucoseInRange
import com.syschimp.glucoripper.ui.theme.RdMono
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import kotlin.math.roundToInt

private val dayLabelFormatter = DateTimeFormatter.ofPattern("EEEE, MMM d")

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

    val weekAgo = Instant.now().minus(Duration.ofDays(7))
    val lastWeek = state.recentReadings.filter { it.time.isAfter(weekAgo) }
    val lowTarget = state.prefs.targetLowMgDl
    val highTarget = state.prefs.targetHighMgDl
    val warningBuffer = state.prefs.warningBufferMgDl
    val inRangeCount = lastWeek.count {
        it.level.inMilligramsPerDeciliter in lowTarget..highTarget
    }
    val inRangePct = if (lastWeek.isEmpty()) 0
    else (inRangeCount * 100.0 / lastWeek.size).roundToInt().coerceIn(0, 100)

    // TIR bar buckets across the 7-day window
    val mgsAll = lastWeek.map { it.level.inMilligramsPerDeciliter }
    val low = mgsAll.count { it < lowTarget - warningBuffer }
    val high = mgsAll.count { it > highTarget + warningBuffer }
    val amber = mgsAll.count {
        (it >= lowTarget - warningBuffer && it < lowTarget) ||
            (it > highTarget && it <= highTarget + warningBuffer)
    }
    val ok = mgsAll.size - low - high - amber

    Scaffold(
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0),
        containerColor = MaterialTheme.colorScheme.surface,
    ) { inner ->
        Column(Modifier.fillMaxSize().padding(inner)) {
            if (state.recentReadings.isEmpty()) {
                Box(Modifier.fillMaxSize().padding(16.dp)) {
                    RdEmptyState(
                        icon = Icons.Outlined.WaterDrop,
                        title = "No readings yet",
                        body = "Once you sync your meter, your readings will appear here grouped by day.",
                    )
                }
                return@Column
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = 14.dp,
                    bottom = 24.dp,
                ),
            ) {
                item {
                    Column(Modifier.padding(vertical = 14.dp)) {
                        RdOverlineText("7-day in target")
                        Spacer(Modifier.height(4.dp))
                        Row(
                            verticalAlignment = Alignment.Bottom,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Text(
                                "$inRangePct%",
                                style = RdMono.DisplayLarge.copy(
                                    fontSize = androidx.compose.ui.unit.TextUnit(56f, androidx.compose.ui.unit.TextUnitType.Sp),
                                    lineHeight = androidx.compose.ui.unit.TextUnit(56f, androidx.compose.ui.unit.TextUnitType.Sp),
                                ),
                                color = GlucoseInRange,
                            )
                            Text(
                                "of ${lastWeek.size} reading${if (lastWeek.size == 1) "" else "s"}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(bottom = 8.dp),
                            )
                        }
                        Spacer(Modifier.height(16.dp))
                        RdTIRBar(low = low, ok = ok, amber = amber, high = high)
                    }
                }

                grouped.forEach { (date, readings) ->
                    stickyHeaderItem(date, readings)
                    items(readings, key = { it.metadata.id }) { r ->
                        val ann = r.metadata.clientRecordId?.let(state.annotations::get)
                        val effectiveMeal = ann?.mealOverride ?: r.relationToMeal
                        val tgt = state.prefs.targetRangeFor(effectiveMeal)
                        val color = bandColor(
                            r.level.inMilligramsPerDeciliter,
                            tgt.first,
                            tgt.second,
                            warningBuffer,
                        )
                        RdReadingRow(
                            bandColor = color,
                            timeLabel = formatTimeOnly(r.time),
                            mealLabel = relationShort(effectiveMeal) ?: "General",
                            mgDlText = formatGlucose(r.level.inMilligramsPerDeciliter, state.prefs.unit),
                            unitText = unitLabel(state.prefs.unit),
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

// Sticky day header — name + readings count + avg
private fun androidx.compose.foundation.lazy.LazyListScope.stickyHeaderItem(
    date: LocalDate,
    readings: List<BloodGlucoseRecord>,
) {
    item(key = "hdr-$date") {
        DayHeader(date = date, readings = readings)
    }
}

@Composable
private fun DayHeader(date: LocalDate, readings: List<BloodGlucoseRecord>) {
    val today = LocalDate.now()
    val label = when {
        date == today -> "Today"
        date == today.minusDays(1) -> "Yesterday"
        ChronoUnit.DAYS.between(date, today) < 7 -> dayLabelFormatter.format(date)
        else -> dayLabelFormatter.format(date)
    }
    val avg = if (readings.isEmpty()) "—"
    else readings.map { it.level.inMilligramsPerDeciliter }.average().roundToInt().toString()

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 24.dp, bottom = 10.dp),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                label,
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontWeight = FontWeight.SemiBold,
                ),
            )
            Text(
                "avg $avg",
                style = RdMono.Caption,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Box(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(MaterialTheme.colorScheme.outline)
                .align(Alignment.BottomStart),
        )
    }
}
