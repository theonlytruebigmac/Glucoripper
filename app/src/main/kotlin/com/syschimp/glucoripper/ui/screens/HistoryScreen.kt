package com.syschimp.glucoripper.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.health.connect.client.records.BloodGlucoseRecord
import com.syschimp.glucoripper.data.Feeling
import com.syschimp.glucoripper.data.GlucoseUnit
import com.syschimp.glucoripper.ui.ReadingDetailSheet
import com.syschimp.glucoripper.ui.UiState
import com.syschimp.glucoripper.ui.components.ReadingRow
import com.syschimp.glucoripper.ui.format.formatGlucose
import com.syschimp.glucoripper.ui.format.unitLabel
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

private val dayHeaderFormatter = DateTimeFormatter.ofPattern("EEEE, MMMM d")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    state: UiState,
    onSetMealRelation: (BloodGlucoseRecord, Int) -> Unit,
    onSetFeeling: (String?, Feeling?) -> Unit,
    onSetNote: (String?, String?) -> Unit,
) {
    var selectedReading by remember { mutableStateOf<BloodGlucoseRecord?>(null) }
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()

    val grouped = remember(state.recentReadings) {
        val zone = ZoneId.systemDefault()
        state.recentReadings
            .groupBy { it.time.atZone(zone).toLocalDate() }
            .toSortedMap(compareByDescending { it })
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("History", fontWeight = FontWeight.SemiBold) },
                scrollBehavior = scrollBehavior,
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
            )
        },
        containerColor = MaterialTheme.colorScheme.surface,
    ) { inner ->
        if (state.recentReadings.isEmpty()) {
            EmptyState(
                title = "No readings yet",
                subtitle = "Once you sync your meter, your readings will appear here grouped by day.",
                modifier = Modifier.fillMaxSize().padding(inner).padding(24.dp),
            )
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(inner),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            grouped.forEach { (date, readings) ->
                item(key = date.toString()) {
                    DayHeader(
                        date = date,
                        readings = readings,
                        unit = state.prefs.unit,
                        lowMgDl = state.prefs.targetLowMgDl,
                        highMgDl = state.prefs.targetHighMgDl,
                    )
                }
                item(key = "card-${date}") {
                    Card(
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                        ),
                    ) {
                        Column(Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
                            readings.forEachIndexed { index, r ->
                                val ann = r.metadata.clientRecordId
                                    ?.let(state.annotations::get)
                                ReadingRow(
                                    reading = r,
                                    annotation = ann,
                                    unit = state.prefs.unit,
                                    lowMgDl = state.prefs.targetLowMgDl,
                                    highMgDl = state.prefs.targetHighMgDl,
                                    showDate = false,
                                    onClick = { selectedReading = r },
                                )
                                if (index < readings.size - 1) {
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

    Column(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp)) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
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
private fun EmptyState(title: String, subtitle: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(
            title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
